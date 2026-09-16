package com.cilassouza.chegadacasa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.location.Location;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import java.util.Locale;

/** Geofence is the backup detector. It MUST use the same state machine as live GPS. */
public class GeofenceReceiver extends BroadcastReceiver {
    public static final String ACTION_OPEN_GATE = "com.cilassouza.chegadacasa.OPEN_GATE";
    public static final String ACTION_CANCEL_GATE = "com.cilassouza.chegadacasa.CANCEL_GATE";
    private static final long GATE_CONFIRM_WINDOW_MS = 5 * 60 * 1000L;
    private static final long MAX_EVENT_AGE_MS = 2 * 60 * 1000L;
    private static final int NOTIFICATION_ARRIVAL = 2001;
    private static final int NOTIFICATION_GATE = 2002;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_OPEN_GATE.equals(action)) { abrirPortaoConfirmado(context); return; }
        if (ACTION_CANCEL_GATE.equals(action)) { cancelarAberturaPortao(context, true); return; }
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!p.getBoolean("ativa", false)) return;
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            p.edit().putString("arrival_last_event", "Geofence sem evento válido").apply();
            return;
        }
        if (event.hasError()) {
            p.edit().putString("arrival_last_event", "Erro geofence: " + event.getErrorCode()).apply();
            return;
        }
        Location trigger = event.getTriggeringLocation();
        if (trigger != null) {
            long age = (SystemClock.elapsedRealtimeNanos() - trigger.getElapsedRealtimeNanos()) / 1000000L;
            if (age < -5000L || age > MAX_EVENT_AGE_MS) {
                p.edit().putString("arrival_last_event", "Geofence atrasado ignorado: " + (age / 1000L) + " s").apply();
                return;
            }
        }
        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            ArrivalController.handleExit(context);
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            ArrivalController.handleArrival(context);
        }
    }

    private void abrirPortaoConfirmado(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        if (!p.getBoolean("ativa", false) || !p.getBoolean("gate_pending", false)
                || at <= 0L || age < 0L || age > GATE_CONFIRM_WINDOW_MS) {
            p.edit().putBoolean("gate_pending", false).remove("gate_pending_at").apply();
            cancelarNotificacaoPortao(context);
            mostrarNotificacao(context, "Confirmação do portão expirada",
                    "Por segurança, aguarde uma nova chegada antes de abrir o portão.");
            return;
        }
        p.edit().putBoolean("gate_pending", false).remove("gate_pending_at").apply();
        cancelarNotificacaoPortao(context);
        final PendingResult pending = goAsync();
        try {
            EwelinkApi.pulseGate(context.getApplicationContext(), new EwelinkApi.TextCallback() {
                @Override public void onSuccess(String message) {
                    try {
                        falar(context, "Portão acionado");
                        mostrarNotificacao(context, "Portão acionado", message);
                    } finally { pending.finish(); }
                }
                @Override public void onError(String message) {
                    try { mostrarNotificacao(context, "Falha ao acionar o portão", message); }
                    finally { pending.finish(); }
                }
            });
        } catch (RuntimeException e) {
            mostrarNotificacao(context, "Falha no comando do portão", e.getClass().getSimpleName());
            pending.finish();
        }
    }

    private void cancelarAberturaPortao(Context context, boolean avisar) {
        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                .putBoolean("gate_pending", false).remove("gate_pending_at").apply();
        cancelarNotificacaoPortao(context);
        if (avisar) mostrarNotificacao(context, "Portão não aberto", "Nenhum comando foi enviado.");
    }

    public static boolean gateConfirmationIsPending(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        return p.getBoolean("gate_pending", false) && at > 0L && age >= 0L
                && age <= GATE_CONFIRM_WINDOW_MS;
    }

    private static void cancelarNotificacaoPortao(Context context) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(NOTIFICATION_GATE);
    }

    private static void falar(Context context, String text) {
        Context app = context.getApplicationContext();
        final TextToSpeech[] ref = new TextToSpeech[1];
        ref[0] = new TextToSpeech(app, status -> {
            TextToSpeech tts = ref[0];
            if (tts == null) return;
            if (status != TextToSpeech.SUCCESS) { tts.shutdown(); return; }
            tts.setLanguage(new Locale("pt", "BR"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { tts.shutdown(); }
                @Override public void onError(String id) { tts.shutdown(); }
            });
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "gate_" + System.currentTimeMillis()) == TextToSpeech.ERROR) tts.shutdown();
        });
    }

    private static void criarCanal(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("chegada", "Chegada em casa",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos de proximidade e confirmação manual do portão");
        nm.createNotificationChannel(channel);
    }

    public static void mostrarNotificacao(Context context, String title, String text) {
        criarCanal(context);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "chegada") : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ARRIVAL, b.build());
    }
}
