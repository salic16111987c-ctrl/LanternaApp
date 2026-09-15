package com.cilassouza.chegadacasa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.Calendar;
import java.util.Locale;

public class GeofenceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;

        int transition = event.getGeofenceTransition();
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            prefs.edit().putBoolean("dentro", false).apply();
            return;
        }

        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER) return;
        if (prefs.getBoolean("dentro", false)) return;

        long agora = System.currentTimeMillis();
        long ultimo = prefs.getLong("ultimo_alerta", 0);
        if (agora - ultimo < 10 * 60 * 1000L) return;

        if (prefs.getBoolean("so_noite", false)) {
            int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!(hora >= 18 || hora < 6)) return;
        }

        prefs.edit().putBoolean("dentro", true).putLong("ultimo_alerta", agora).apply();

        if (EwelinkApi.hasSession(context) && EwelinkApi.selectedCount(context) > 0) {
            PendingResult pending = goAsync();
            EwelinkApi.turnOnSelected(context.getApplicationContext(), new EwelinkApi.TextCallback() {
                @Override
                public void onSuccess(String message) {
                    falarConfirmacao(context);
                    mostrarNotificacao(context,
                            "Chegada detectada — luzes acionadas",
                            message);
                    pending.finish();
                }

                @Override
                public void onError(String message) {
                    mostrarNotificacao(context,
                            "Chegada detectada — falha no eWeLink",
                            message);
                    pending.finish();
                }
            });
        } else {
            mostrarNotificacao(context,
                    "Você chegou perto de casa",
                    "O celular entrou no raio configurado. A detecção está funcionando; falta concluir a autorização OAuth do eWeLink e escolher as lâmpadas.");
        }
    }

    private static void falarConfirmacao(Context context) {
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
                public void onDone(String utteranceId) {
                    tts.shutdown();
                }

                @Override
                public void onError(String utteranceId) {
                    tts.shutdown();
                }
            });

            int result = tts.speak(
                    "Lâmpadas acesas",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "chegada_lampadas_acesas"
            );
            if (result == TextToSpeech.ERROR) tts.shutdown();
        });
    }

    public static void mostrarNotificacao(Context context, String titulo, String texto) {
        String canal = "chegada";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(canal, "Chegada em casa", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos do acionamento por proximidade");
            nm.createNotificationChannel(ch);
        }

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, canal)
                : new android.app.Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH);

        nm.notify(2001, b.build());
    }
}
