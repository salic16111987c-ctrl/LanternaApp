package com.cilassouza.chegadacasa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** Reuse the engine instead of creating and immediately destroying it on every arrival. */
public final class SpeechEngine {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static TextToSpeech engine;
    private static boolean ready;
    private static String pending;
    private static Context app;
    private SpeechEngine() { }

    public static void speak(Context context, String message) {
        Context application = context.getApplicationContext();
        MAIN.post(() -> {
            app = application;
            pending = message;
            record("Áudio solicitado; preparando voz");
            if (ready && engine != null) {
                speakPending();
            } else if (engine == null) {
                try {
                    engine = new TextToSpeech(app, status -> MAIN.post(() -> initialize(status)));
                } catch (RuntimeException e) {
                    record("Falha ao criar voz: " + e.getClass().getSimpleName());
                    engine = null;
                }
            }
        });
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
        engine.setSpeechRate(1f);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { record("Áudio iniciado"); }
            @Override public void onDone(String id) { record("Áudio concluído"); }
            @Override public void onError(String id) { record("ERRO durante reprodução do áudio"); }
        });
        ready = true;
        speakPending();
    }

    private static void speakPending() {
        if (!ready || engine == null || pending == null) return;
        String message = pending;
        pending = null;
        int result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null,
                "chegada_" + System.currentTimeMillis());
        if (result == TextToSpeech.ERROR) record("ERRO: comando de voz recusado");
    }

    private static void record(String status) {
        if (app != null) {
            SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
            p.edit().putString("tts_state", status)
                    .putLong("tts_at", System.currentTimeMillis()).apply();
        }
    }
}
