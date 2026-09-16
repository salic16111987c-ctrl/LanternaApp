package com.cilassouza.chegadacasa;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Calendar;
import java.util.Locale;

/**
 * Centraliza a chegada detectada pelo monitor rapido de localizacao.
 * O aviso de voz acontece ANTES da chamada de rede do eWeLink.
 */
public final class ArrivalController {
    private static final int NOTIFICATION_GATE = 2002;
    private static final long MIN_ALERT_INTERVAL_MS = 10 * 60 * 1000L;

    private ArrivalController() { }

    public static void handleArrival(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("config", Context.MODE_PRIVATE);

        if (prefs.getBoolean("dentro", false)) return;

        long agora = System.currentTimeMillis();
        long ultimo = prefs.getLong("ultimo_alerta", 0L);
        if (agora - ultimo < MIN_ALERT_INTERVAL_MS) return;

        if (prefs.getBoolean("so_noite", false)) {
            int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!(hora >= 18 || hora < 6)) return;
        }

        boolean temPortao = EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);
        boolean temLampadas = EwelinkApi.hasSession(app) && EwelinkApi.selectedCount(app) > 0;

        SharedPreferences.Editor chegada = prefs.edit()
                .putBoolean("dentro", true)
                .putLong("ultimo_alerta", agora);
        if (temPortao) {
            chegada.putBoolean("gate_pending", true)
                    .putLong("gate_pending_at", agora);
        }
        chegada.apply();

        if (temPortao) mostrarPerguntaPortao(app);

        // Fala imediatamente; nao espera internet/eWeLink responder.
        if (temLampadas && temPortao) {
            falar(app, "Chegada detectada. Acendendo as lâmpadas. Deseja abrir o portão?");
        } else if (temLampadas) {
            falar(app, "Chegada detectada. Acendendo as lâmpadas.");
        } else if (temPortao) {
            falar(app, "Chegada detectada. Deseja abrir o portão?");
        } else {
            falar(app, "Você chegou perto de casa.");
        }

        if (temLampadas) {
            EwelinkApi.turnOnSelected(app, new EwelinkApi.TextCallback() {
                @Override
                public void onSuccess(String message) {
                    GeofenceReceiver.mostrarNotificacao(app,
                            "Chegada detectada — luzes acionadas",
                            message);
                }

                @Override
                public void onError(String message) {
                    falar(app, "Não consegui acender as lâmpadas.");
                    GeofenceReceiver.mostrarNotificacao(app,
                            "Chegada detectada — falha nas luzes",
                            message);
                }
            });
        } else {
            GeofenceReceiver.mostrarNotificacao(app,
                    "Você chegou perto de casa",
                    "O celular entrou no raio configurado.");
        }
    }

    public static void handleExit(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences("config", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("dentro", false)
                .putBoolean("gate_pending", false)
                .remove("gate_pending_at")
                .apply();

        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_GATE);
    }

    private static void mostrarPerguntaPortao(Context context) {
        Intent abrirIntent = new Intent(context, GeofenceReceiver.class)
                .setAction(GeofenceReceiver.ACTION_OPEN_GATE);
        Intent cancelarIntent = new Intent(context, GeofenceReceiver.class)
                .setAction(GeofenceReceiver.ACTION_CANCEL_GATE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent abrir = PendingIntent.getBroadcast(context, 2201, abrirIntent, flags);
        PendingIntent cancelar = PendingIntent.getBroadcast(context, 2202, cancelarIntent, flags);

        String nome = EwelinkApi.getGateName(context);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, "chegada")
                : new android.app.Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Deseja abrir o portão?")
                .setContentText(nome + " — confirme a abertura")
                .setAutoCancel(false)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .addAction(new android.app.Notification.Action.Builder(
                        android.R.drawable.ic_menu_send, "ABRIR PORTÃO", abrir).build())
                .addAction(new android.app.Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "NÃO ABRIR", cancelar).build());

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_GATE, b.build());
    }

    private static void falar(Context context, String texto) {
        Context app = context.getApplicationContext();
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(app, status -> {
            TextToSpeech tts = holder[0];
            if (tts == null) return;
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown();
                return;
            }

            int lang = tts.setLanguage(new Locale("pt", "BR"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }

            tts.setSpeechRate(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) { }

                @Override
                public void onDone(String utteranceId) { tts.shutdown(); }

                @Override
                public void onError(String utteranceId) { tts.shutdown(); }
            });

            int result = tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null,
                    "chegada_rapida_" + System.currentTimeMillis());
            if (result == TextToSpeech.ERROR) tts.shutdown();
        });
    }
}
