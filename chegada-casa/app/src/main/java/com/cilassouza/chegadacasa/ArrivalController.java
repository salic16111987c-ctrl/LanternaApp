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
            p.edit().putBoolean("dentro", true).putBoolean("outside_observed", false)
                    .putString("arrival_last_event", time() + " — chegada bloqueada: intervalo mínimo de 1 min")
                    .apply();
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
        boolean session = EwelinkApi.hasSession(app);
        boolean lights = session && EwelinkApi.selectedCount(app) > 0;
        boolean gate = session && EwelinkApi.hasGate(app);
        SharedPreferences.Editor editor = p.edit().putBoolean("dentro", true)
                .putBoolean("outside_observed", false).putLong("ultimo_alerta", now)
                .putString("arrival_last_event", time() + " — CHEGADA DETECTADA");
        if (gate) editor.putBoolean("gate_pending", true).putLong("gate_pending_at", now);
        editor.apply();
        if (gate) showGateConfirmation(app);

        if (!lights) {
            String reason = !session ? "eWeLink NÃO conectada neste aplicativo" :
                    "Nenhuma lâmpada selecionada neste aplicativo";
            p.edit().putString("arrival_last_command", time() + " — NÃO ENVIADO: " + reason).apply();
            GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — sem comando às luzes", reason);
            SpeechEngine.speak(app, gate ? "Chegada detectada. Deseja abrir o portão?" :
                    "Chegada detectada. Verifique a conexão e as lâmpadas selecionadas.");
            return;
        }

        // Audible, visible indication must NEVER await a cloud request.
        p.edit().putString("arrival_last_command", time() + " — iniciando solicitação eWeLink").apply();
        GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — comando em andamento",
                "Solicitando acionamento das lâmpadas; aguardando resposta eWeLink.");
        SpeechEngine.speak(app, gate ? "Chegada detectada. Acionando as lâmpadas. Deseja abrir o portão?" :
                "Chegada detectada. Acionando as lâmpadas.");
        try {
            EwelinkApi.turnOnSelected(app, new EwelinkApi.TextCallback() {
                @Override public void onSuccess(String message) {
                    p.edit().putString("arrival_last_command", time() + " — eWeLink: " + message).apply();
                    GeofenceReceiver.mostrarNotificacao(app, "Resposta eWeLink recebida", message);
                }
                @Override public void onError(String message) {
                    p.edit().putString("arrival_last_command", time() + " — ERRO eWeLink: " + message).apply();
                    GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — falha nas lâmpadas", message);
                    SpeechEngine.speak(app, "Não consegui enviar o comando para as lâmpadas.");
                }
            });
        } catch (RuntimeException e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            p.edit().putString("arrival_last_command", time() + " — ERRO: " + reason).apply();
            GeofenceReceiver.mostrarNotificacao(app, "Erro ao enviar comando", reason);
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
