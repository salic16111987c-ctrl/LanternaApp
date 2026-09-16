package com.cilassouza.chegadacasa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Single state machine called by the continuous GPS and geofence backup. */
public final class ArrivalController {
    private static final int NOTIFICATION_GATE = 2002;
    private static final long MIN_ALERT_INTERVAL_MS = 60 * 1000L;
    private ArrivalController() { }

    private static String time() {
        return DateFormat.getTimeInstance(DateFormat.MEDIUM, new Locale("pt", "BR"))
                .format(new Date());
    }

    public static synchronized void handleArrival(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!p.getBoolean("ativa", false)) {
            p.edit().putString("arrival_last_event", time() + " — chegada ignorada: automação desligada").apply();
            return;
        }
        if (p.getBoolean("dentro", false)) return;
        long now = System.currentTimeMillis();
        long last = p.getLong("ultimo_alerta", 0L);
        if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {
            // CRITICAL: keep "dentro" false and "outside_observed" true. The GPS
            // callback runs repeatedly while within the radius, and must be free
            // to retry after this interval. Previous versions marked "dentro"
            // true here and permanently swallowed this arrival until another exit.
            long secondsRemaining = (MIN_ALERT_INTERVAL_MS - (now - last) + 999L) / 1000L;
            p.edit().putString("arrival_last_event", time()
                    + " — aguardando " + secondsRemaining
                    + " s para repetir chegada; tentativa continua armada").apply();
            return;
        }
        if (p.getBoolean("so_noite", false)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!(hour >= 18 || hour < 6)) {
                p.edit().putBoolean("dentro", true).putBoolean("outside_observed", false)
                        .putString("arrival_last_event", time() + " — chegada detectada, mas SOMENTE À NOITE ativado")
                        .apply();
                return;
            }
        }
        boolean gate = EwelinkApi.hasSession(app) && EwelinkApi.hasGate(app);
        SharedPreferences.Editor editor = p.edit().putBoolean("dentro", true)
                .putBoolean("outside_observed", false).putLong("ultimo_alerta", now)
                .putString("arrival_last_event", time() + " — CHEGADA DETECTADA");
        if (gate) editor.putBoolean("gate_pending", true).putLong("gate_pending_at", now);
        editor.apply();
        if (gate) showGateConfirmation(app);
        dispatchLights(app, p, gate, false);
    }

    /** Exercises the actual light/audio/notification path without changing geofence state.
     * Never starts a gate action or creates a gate confirmation. Only invoked by a
     * user-confirmed button; it does not pretend that a GPS arrival happened.
     */
    public static synchronized void simulateArrival(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
        p.edit().putString("simulation_last_result", time() + " — SIMULAÇÃO iniciada manualmente")
                .apply();
        dispatchLights(app, p, false, true);
    }

    /** Identical light command pipeline for genuine and simulated arrivals. */
    private static void dispatchLights(Context app, SharedPreferences p, boolean gate,
                                       boolean simulated) {
        String origin = simulated ? "SIMULAÇÃO" : "CHEGADA";
        boolean session = EwelinkApi.hasSession(app);
        boolean lights = session && EwelinkApi.selectedCount(app) > 0;
        if (!lights) {
            String reason = !session ? "eWeLink NÃO conectada neste aplicativo" :
                    "Nenhuma lâmpada selecionada neste aplicativo";
            String result = time() + " — " + origin + " — NÃO ENVIADO: " + reason;
            p.edit().putString("arrival_last_command", result)
                    .putString("simulation_last_result", simulated ? result :
                            p.getString("simulation_last_result", "não realizada")).apply();
            GeofenceReceiver.mostrarNotificacao(app, origin + " — sem comando às luzes", reason);
            SpeechEngine.speak(app, simulated ? "Simulação não enviou o comando. Confira a conexão e as lâmpadas selecionadas." :
                    gate ? "Chegada detectada. Deseja abrir o portão?" :
                            "Chegada detectada. Verifique a conexão e as lâmpadas selecionadas.");
            return;
        }

        String start = time() + " — " + origin + " — solicitação eWeLink iniciada";
        SharedPreferences.Editor startEditor = p.edit().putString("arrival_last_command", start);
        if (simulated) startEditor.putString("simulation_last_result", start);
        startEditor.apply();
        GeofenceReceiver.mostrarNotificacao(app, origin + " — comando em andamento",
                "Solicitando acionamento das lâmpadas; aguardando resposta eWeLink.");
        SpeechEngine.speak(app, simulated ? "Simulação de chegada. Acionando as lâmpadas." :
                gate ? "Chegada detectada. Acionando as lâmpadas. Deseja abrir o portão?" :
                        "Chegada detectada. Acionando as lâmpadas.");
        try {
            EwelinkApi.turnOnSelected(app, new EwelinkApi.TextCallback() {
                @Override public void onSuccess(String message) {
                    String result = time() + " — " + origin + " — resposta eWeLink: " + message;
                    SharedPreferences.Editor done = p.edit().putString("arrival_last_command", result);
                    if (simulated) done.putString("simulation_last_result", result);
                    done.apply();
                    GeofenceReceiver.mostrarNotificacao(app, origin + " — resposta eWeLink", message);
                }
                @Override public void onError(String message) {
                    String result = time() + " — " + origin + " — ERRO eWeLink: " + message;
                    SharedPreferences.Editor done = p.edit().putString("arrival_last_command", result);
                    if (simulated) done.putString("simulation_last_result", result);
                    done.apply();
                    GeofenceReceiver.mostrarNotificacao(app, origin + " — falha nas lâmpadas", message);
                    SpeechEngine.speak(app, "Não consegui enviar o comando para as lâmpadas.");
                }
            });
        } catch (RuntimeException e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            String result = time() + " — " + origin + " — ERRO: " + reason;
            SharedPreferences.Editor failure = p.edit().putString("arrival_last_command", result);
            if (simulated) failure.putString("simulation_last_result", result);
            failure.apply();
            GeofenceReceiver.mostrarNotificacao(app, origin + " — erro ao enviar comando", reason);
        }
    }

    public static synchronized void handleExit(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!p.getBoolean("ativa", false)) return;
        p.edit().putBoolean("dentro", false).putBoolean("outside_observed", true)
                .putBoolean("gate_pending", false).remove("gate_pending_at")
                .putString("arrival_last_event", time() + " — SAÍDA CONFIRMADA; próxima chegada armada")
                .apply();
        ((NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_GATE);
    }

    private static void showGateConfirmation(Context context) {
        Intent open = new Intent(context, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_OPEN_GATE);
        Intent cancel = new Intent(context, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_CANCEL_GATE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getBroadcast(context, 2201, open, flags);
        PendingIntent cancelPi = PendingIntent.getBroadcast(context, 2202, cancel, flags);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("chegada", "Chegada em casa",
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "chegada") : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Deseja abrir o portão?")
                .setContentText(EwelinkApi.getGateName(context) + " — confirme a abertura")
                .setAutoCancel(false).setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_send,
                        "ABRIR PORTÃO", openPi).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "NÃO ABRIR", cancelPi).build());
        nm.notify(NOTIFICATION_GATE, b.build());
    }
}
