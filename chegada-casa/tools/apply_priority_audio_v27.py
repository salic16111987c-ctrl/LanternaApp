#!/usr/bin/env python3
"""v2.7: priority arrival voice that ducks other audio, boosts media temporarily, then restores it."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/cilassouza/chegadacasa'

speech_path = src / 'SpeechEngine.java'
new_speech = r'''package com.cilassouza.chegadacasa;

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
        } catch (RuntimeException | SecurityException e) {
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
        } catch (RuntimeException | SecurityException ignored) {
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
'''
current = speech_path.read_text(encoding='utf-8')
if 'TARGET_MEDIA_PERCENT = 85' not in current:
    if 'public final class SpeechEngine' not in current:
        raise AssertionError('SpeechEngine anchor not found')
    speech_path.write_text(new_speech, encoding='utf-8')
    print('PATCHED SpeechEngine.java')
else:
    print('ALREADY PATCHED SpeechEngine.java')


def replace_once(path, before, after):
    text = path.read_text(encoding='utf-8')
    if after in text:
        print('ALREADY PATCHED', path.name)
        return
    if text.count(before) != 1:
        raise AssertionError(f'{path}: expected one anchor for {before[:80]!r}')
    path.write_text(text.replace(before, after, 1), encoding='utf-8')
    print('PATCHED', path.name)

manifest = root / 'app/src/main/AndroidManifest.xml'
text = manifest.read_text(encoding='utf-8')
permission = '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n'
if 'android.permission.MODIFY_AUDIO_SETTINGS' not in text:
    anchor = '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n'
    if anchor not in text:
        raise AssertionError('Manifest permission anchor not found')
    manifest.write_text(text.replace(anchor, anchor + permission, 1), encoding='utf-8')
    print('PATCHED AndroidManifest.xml')
else:
    print('ALREADY PATCHED AndroidManifest.xml')

gradle_path = root / 'app/build.gradle'
gradle_text = gradle_path.read_text(encoding='utf-8')
if "versionName '2.6-antifalsas-chegadas'" in gradle_text:
    replace_once(gradle_path,
'''        versionCode 11
        versionName '2.6-antifalsas-chegadas'
''',
'''        versionCode 12
        versionName '2.7-audio-prioritario'
''')
elif "versionName '2.7-audio-prioritario'" in gradle_text or "versionName '2.8-interface-profissional'" in gradle_text:
    print('VERSION ALREADY v2.7 OR NEWER')
else:
    raise AssertionError('Unexpected Chegada Casa version while applying priority audio')

diagnostic_path = src / 'ArrivalDiagnosticActivity.java'
diagnostic_text = diagnostic_path.read_text(encoding='utf-8')
if 'title.setText("DIAGNÓSTICO E VOZ — v2.6");' in diagnostic_text:
    replace_once(diagnostic_path,
                 'title.setText("DIAGNÓSTICO E VOZ — v2.6");',
                 'title.setText("DIAGNÓSTICO E VOZ — v2.7");')
elif 'DIAGNÓSTICO E VOZ — v2.7' in diagnostic_text or 'DIAGNÓSTICO E VOZ — v2.8' in diagnostic_text:
    print('DIAGNOSTIC TITLE ALREADY v2.7 OR NEWER')
else:
    raise AssertionError('Unexpected diagnostic version while applying priority audio')

checks = root / 'tools/check_arrival_invariants.py'
replace_once(checks,
'''check('Volume changed only through explicit user action',
      'volumeButton.setOnClickListener' in diagnostic and
      'audio.adjustStreamVolume(AudioManager.STREAM_MUSIC' in diagnostic and
      'SpeechEngine.mediaVolumePercent(this)' in diagnostic and
      'TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f' in speech and
      'AudioAttributes.USAGE_MEDIA' in speech)
''',
'''check('Arrival voice gets transient priority, ducks other audio, boosts then restores volume',
      all(text in speech for text in ['USAGE_ASSISTANCE_NAVIGATION_GUIDANCE',
                                      'AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK',
                                      'TARGET_MEDIA_PERCENT = 85',
                                      'setStreamVolume(AudioManager.STREAM_MUSIC',
                                      'previousMediaVolume', 'restoreMediaVolume()',
                                      'abandonAudioFocusRequest', 'SAFETY_RELEASE_MS'])
      and 'TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f' in speech
      and 'android.permission.MODIFY_AUDIO_SETTINGS' in manifest)
''')
replace_once(checks,
'''      "versionName '2.6-antifalsas-chegadas'" in gradle and 'versionCode 11' in gradle and
''',
'''      "versionName '2.7-audio-prioritario'" in gradle and 'versionCode 12' in gradle and
''')

print('SUCCESS: v2.7 priority arrival audio patch is ready.')
