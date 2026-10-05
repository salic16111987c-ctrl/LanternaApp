package com.cilassouza.chegadacasa;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** Arrival TTS with temporary audio priority; user volume is restored afterwards. */
public final class SpeechEngine {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TARGET_MEDIA_PERCENT = 85;
    private static final long SAFETY_RELEASE_MS = 20000L;

    private static TextToSpeech engine;
    private static boolean ready;
    private static String pending;
    private static Context app;
    private static AudioManager audio;
    private static AudioFocusRequest focusRequest;
    private static boolean focusHeld;
    private static int previousMediaVolume = -1;
    private static int boostedMediaVolume = -1;
    private static String currentUtteranceId;

    private SpeechEngine() { }

    private static final AudioManager.OnAudioFocusChangeListener FOCUS_LISTENER = change ->
            MAIN.post(() -> {
                if ((change == AudioManager.AUDIOFOCUS_LOSS
                        || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                        && currentUtteranceId != null) {
                    String id = currentUtteranceId;
                    record("Prioridade de áudio perdida; aviso interrompido com segurança");
                    if (engine != null) engine.stop();
                    endPriorityAudio(id);
                }
            });

    private static final Runnable SAFETY_RELEASE = () -> {
        if (currentUtteranceId != null) {
            String id = currentUtteranceId;
            record("Proteção de áudio: restaurando volume após tempo limite");
            endPriorityAudio(id);
        }
    };

    public static int mediaVolumePercent(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return -1;
        int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0 : Math.round(100f * manager.getStreamVolume(AudioManager.STREAM_MUSIC) / max);
    }

    public static void speak(Context context, String message) {
        Context application = context.getApplicationContext();
        MAIN.post(() -> {
            app = application;
            pending = message;
            record("Áudio solicitado; aviso terá prioridade temporária e volume reforçado");
            if (ready && engine != null) speakPending();
            else if (engine == null) {
                try {
                    engine = new TextToSpeech(app, status -> MAIN.post(() -> initialize(status)));
                } catch (RuntimeException e) {
                    record("Falha ao criar voz: " + e.getClass().getSimpleName());
                    engine = null;
                }
            }
        });
    }

    private static AudioAttributes speechAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private static void initialize(int status) {
        if (engine == null) { record("Voz indisponível: mecanismo não carregou"); return; }
        if (status != TextToSpeech.SUCCESS) {
            record("Voz não iniciou (código " + status + ")");
            engine.shutdown();
            engine = null;
            ready = false;
            return;
        }
        int language = engine.setLanguage(new Locale("pt", "BR"));
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            language = engine.setLanguage(Locale.getDefault());
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                record("Nenhuma voz instalada para o idioma");
                return;
            }
        }
        engine.setAudioAttributes(speechAttributes());
        engine.setSpeechRate(1f);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                record("Áudio iniciado com prioridade; mídia temporária em " + mediaVolumePercent(app) + "%");
            }
            @Override public void onDone(String id) {
                record("Áudio concluído; restaurando volume anterior");
                MAIN.post(() -> endPriorityAudio(id));
            }
            @Override public void onError(String id) {
                record("ERRO durante reprodução do áudio; restaurando volume");
                MAIN.post(() -> endPriorityAudio(id));
            }
            @Override public void onStop(String id, boolean interrupted) {
                record(interrupted ? "Áudio interrompido; restaurando volume" : "Áudio parado");
                MAIN.post(() -> endPriorityAudio(id));
            }
        });
        ready = true;
        speakPending();
    }

    private static void beginPriorityAudio() {
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            record("AudioManager indisponível; reproduzindo sem prioridade");
            return;
        }
        try {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(speechAttributes())
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(FOCUS_LISTENER, MAIN)
                        .build();
            }
            focusHeld = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (RuntimeException e) {
            focusHeld = false;
            record("Não foi possível obter prioridade de áudio: " + e.getClass().getSimpleName());
        }

        previousMediaVolume = -1;
        boostedMediaVolume = -1;
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(1, Math.round(max * TARGET_MEDIA_PERCENT / 100f));
            if (max > 0 && current < target) {
                previousMediaVolume = current;
                boostedMediaVolume = target;
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            }
            record("Prioridade " + (focusHeld ? "OK" : "não concedida")
                    + "; aviso em até " + TARGET_MEDIA_PERCENT + "% e outros áudios reduzidos temporariamente");
        } catch (RuntimeException e) {
            previousMediaVolume = -1;
            boostedMediaVolume = -1;
            record("Não foi possível reforçar o volume: " + e.getClass().getSimpleName());
        }
    }

    private static void restoreMediaVolume() {
        if (audio == null || previousMediaVolume < 0 || boostedMediaVolume < 0) {
            previousMediaVolume = -1;
            boostedMediaVolume = -1;
            return;
        }
        try {
            // If the user changed volume while the announcement was speaking, respect that choice.
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (current == boostedMediaVolume) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, previousMediaVolume, 0);
            }
        } catch (RuntimeException ignored) {
        }
        previousMediaVolume = -1;
        boostedMediaVolume = -1;
    }

    private static void endPriorityAudio(String id) {
        if (currentUtteranceId == null) return;
        if (id != null && !id.equals(currentUtteranceId)) return;
        MAIN.removeCallbacks(SAFETY_RELEASE);
        restoreMediaVolume();
        if (audio != null && focusHeld && focusRequest != null) {
            try { audio.abandonAudioFocusRequest(focusRequest); }
            catch (RuntimeException ignored) { }
        }
        focusHeld = false;
        currentUtteranceId = null;
    }

    private static void speakPending() {
        if (!ready || engine == null || pending == null) return;
        String message = pending;
        pending = null;

        if (currentUtteranceId != null) endPriorityAudio(currentUtteranceId);
        String id = "chegada_" + System.currentTimeMillis();
        currentUtteranceId = id;
        beginPriorityAudio();
        MAIN.removeCallbacks(SAFETY_RELEASE);
        MAIN.postDelayed(SAFETY_RELEASE, SAFETY_RELEASE_MS);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        int result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, params, id);
        if (result == TextToSpeech.ERROR) {
            record("ERRO: comando de voz recusado");
            endPriorityAudio(id);
        }
    }

    private static void record(String status) {
        if (app != null) {
            SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
            p.edit().putString("tts_state", status)
                    .putLong("tts_at", System.currentTimeMillis()).apply();
        }
    }
}
