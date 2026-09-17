package com.cilassouza.chegadacasa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.location.Location;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

/** Geofence is the backup detector; both detectors use the SAME arrival state machine. */
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
            // Unknown origin is treated as untrusted for gate safety. Lights still work.
            ArrivalController.handleArrival(context, trigger == null || trigger.isFromMockProvider());
        }
    }

    private void abrirPortaoConfirmado(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        if (!p.getBoolean("ativa", false) || !p.getBoolean("gate_pending", false)
                || p.getBoolean("gate_origin_mock", true)
                || !EwelinkApi.hasSession(context) || !EwelinkApi.hasGate(context)
                || at <= 0L || age < 0L || age > GATE_CONFIRM_WINDOW_MS) {
            p.edit().putBoolean("gate_pending", false).remove("gate_pending_at")
                    .putString("gate_voice_status", "Confirmação rejeitada: expirada, simulada ou inválida")
                    .apply();
            cancelarNotificacaoPortao(context);
            mostrarNotificacao(context, "Portão não acionado",
                    "Confirmação inválida ou expirada. Espere uma nova chegada real.");
            return;
        }
        // User explicitly tapped ABRIR or spoke the exact phrase after opening microphone.
        p.edit().putBoolean("gate_pending", false).remove("gate_pending_at")
                .putString("gate_voice_status", "Autorização explícita recebida; enviando comando")
                .apply();
        cancelarNotificacaoPortao(context);
        final PendingResult pending = goAsync();
        try {
            EwelinkApi.pulseGate(context.getApplicationContext(), new EwelinkApi.TextCallback() {
                @Override public void onSuccess(String message) {
                    try {
                        p.edit().putString("gate_voice_status", "Resposta eWeLink: " + message).apply();
                        SpeechEngine.speak(context, "Comando de abertura do portão enviado.");
                        mostrarNotificacao(context, "Comando do portão enviado", message);
                    } finally { pending.finish(); }
                }
                @Override public void onError(String message) {
                    try {
                        p.edit().putString("gate_voice_status", "Falha no portão: " + message).apply();
                        mostrarNotificacao(context, "Falha ao acionar o portão", message);
                    } finally { pending.finish(); }
                }
            });
        } catch (RuntimeException e) {
            mostrarNotificacao(context, "Falha no comando do portão", e.getClass().getSimpleName());
            p.edit().putString("gate_voice_status", "Erro: " + e.getClass().getSimpleName()).apply();
            pending.finish();
        }
    }

    private void cancelarAberturaPortao(Context context, boolean avisar) {
        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                .putBoolean("gate_pending", false).remove("gate_pending_at")
                .putString("gate_voice_status", "Resposta NÃO; nenhum comando enviado").apply();
        cancelarNotificacaoPortao(context);
        if (avisar) mostrarNotificacao(context, "Portão não aberto", "Nenhum comando foi enviado.");
    }

    public static boolean gateConfirmationIsPending(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        return p.getBoolean("ativa", false) && !p.getBoolean("gate_origin_mock", true)
                && p.getBoolean("gate_pending", false) && at > 0L && age >= 0L
                && age <= GATE_CONFIRM_WINDOW_MS;
    }

    private static void cancelarNotificacaoPortao(Context context) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(NOTIFICATION_GATE);
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
