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
import java.util.List;
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
        List<Geofence> fences = event.getTriggeringGeofences();
        boolean homeFence = false;
        boolean outerFence = false;
        if (fences != null) for (Geofence fence : fences) {
            if ("casa".equals(fence.getRequestId())) homeFence = true;
            if ("aproximacao_2500m".equals(fence.getRequestId())) outerFence = true;
        }
        if (outerFence) {
            boolean entered = event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER;
            p.edit().putBoolean("outer_inside", entered)
                    .putString("outer_geofence_status", entered
                            ? "Entrou na preparação 2,5 km" : "Saiu da preparação 2,5 km").apply();
        }
        // Outer 2.5-km fence is NEVER a lamp or gate arrival trigger.
        if (!homeFence) return;
        if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // A geofence EXIT alone can be GPS drift while the owner is at home.
            // Only the monitor may rearm a real arrival after sustained outside fixes.
            p.edit().putString("arrival_last_event",
                    "Cerca sinalizou saída; aguardando GPS confirmar afastamento").apply();
        } else if (event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // An early mock geofence event must wait for the GPS monitor to verify it.
            if (GateTestMode.isArmed(p) && (trigger == null || trigger.isFromMockProvider())
                    && !p.getBoolean("monitor_mock", false)) {
                p.edit().putString("arrival_last_event", "Teste fake GPS: aguardando posição do monitor").apply();
                return;
            }
            // ENTER is only a backup after the monitor confirmed a genuine exit.
            // Never turn on lights or prompt a physical gate on an unverified fence bounce.
            if (!p.getBoolean("outside_observed", false) || trigger == null
                    || !trigger.hasAccuracy()
                    || trigger.getAccuracy() > Math.max(60f, p.getInt("raio", 100) * 0.4f)) {
                p.edit().putString("arrival_last_event",
                        "Cerca entrou; chegada sem saída confirmada/posição precisa: ignorada").apply();
                return;
            }
            float[] homeDistance = new float[1];
            Location.distanceBetween(trigger.getLatitude(), trigger.getLongitude(),
                    Double.longBitsToDouble(p.getLong("lat", 0L)),
                    Double.longBitsToDouble(p.getLong("lon", 0L)), homeDistance);
            if (!p.getBoolean("casa_definida", false)
                    || homeDistance[0] > Math.max(50, p.getInt("raio", 100))) return;
            ArrivalController.handleArrival(context, trigger.isFromMockProvider());
        }
    }

    private void abrirPortaoConfirmado(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        boolean mockTest = p.getBoolean("gate_origin_mock", true)
                && GateTestMode.isAuthorizedPending(p);
        if (!p.getBoolean("ativa", false) || !p.getBoolean("gate_pending", false)
                || (p.getBoolean("gate_origin_mock", true) && !mockTest)
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
        // Even in test mode, mock GPS alone never sends a pulse: an explicit SIM
        // is mandatory, and the temporary permit is CONSUMED before any hardware call.
        if (mockTest && !GateTestMode.consume(p)) {
            cancelarNotificacaoPortao(context);
            mostrarNotificacao(context, "Teste cancelado", "Autorização de teste expirada ou não pôde ser consumida.");
            return;
        }
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
                .putBoolean("gate_pending", false).putBoolean("gate_test_pending", false)
                .putBoolean("gate_test_armed", false).putLong("gate_test_until", 0L)
                .remove("gate_pending_at")
                .putString("gate_voice_status", "Resposta NÃO; nenhum comando enviado").apply();
        cancelarNotificacaoPortao(context);
        if (avisar) mostrarNotificacao(context, "Portão não aberto", "Nenhum comando foi enviado.");
    }

    public static boolean gateConfirmationIsPending(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        long at = p.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        return p.getBoolean("ativa", false)
                && (!p.getBoolean("gate_origin_mock", true) || GateTestMode.isAuthorizedPending(p))
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
