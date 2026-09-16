package com.cilassouza.chegadacasa;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** One arrival path for accurate GPS updates. Gate still requires manual confirmation. */
public final class ArrivalController {
    private static final int NOTIFICATION_GATE = 2002;
    private static final long MIN_ALERT_INTERVAL_MS = 10 * 60 * 1000L;
    private ArrivalController() { }

    private static String time() {
        return DateFormat.getTimeInstance(DateFormat.MEDIUM, new Locale("pt", "BR"))
                .format(new Date());
    }

    public static synchronized void handleArrival(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("ativa", false)) {
            prefs.edit().putString("arrival_last_event", time() + " — chegada ignorada: automação desativada").apply();
            return;
        }
        if (prefs.getBoolean("dentro", false)) return;
        long now = System.currentTimeMillis();
        long last = prefs.getLong("ultimo_alerta", 0L);
        if (now >= last && now - last < MIN_ALERT_INTERVAL_MS) {
            // Mark inside so we do not trigger late after the 10-minute cooldown expires.
            prefs.edit().putBoolean("dentro", true).putBoolean("outside_observed", false)
                    .putString("arrival_last_event", time() + " — chegada bloqueada: intervalo mínimo de 10 min")
                    .apply();
            return;
        }
        if (prefs.getBoolean("so_noite", false)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!(hour >= 18 || hour < 6)) {
                prefs.edit().putBoolean("dentro", true).putBoolean("outside_observed", false)
                        .putString("arrival_last_event", time() + " — chegada detectada, mas opção SOMENTE À NOITE ativa")
                        .apply();
                return;
            }
        }
        boolean session = EwelinkApi.hasSession(app);
        boolean lights = session && EwelinkApi.selectedCount(app) > 0;
        boolean gate = session && EwelinkApi.hasGate(app);
        SharedPreferences.Editor edit = prefs.edit().putBoolean("dentro", true)
                .putBoolean("outside_observed", false)
                .putLong("ultimo_alerta", now)
                .putString("arrival_last_event", time() + " — chegada confirmada por GPS");
        if (gate) edit.putBoolean("gate_pending", true).putLong("gate_pending_at", now);
        edit.apply();

        if (gate) showGateConfirmation(app);
        if (!lights) {
            String reason = !session ? "Conta eWeLink NÃO conectada neste app" :
                    "Nenhuma lâmpada selecionada neste app";
            prefs.edit().putString("arrival_last_command", time() + " — NÃO ENVIADO: " + reason).apply();
            GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — sem lâmpadas", reason);
            speak(app, gate ? "Chegada detectada. Deseja abrir o portão?" :
                    "Chegada detectada. Verifique a configuração das lâmpadas.");
            return;
        }
        // Immediate local notification and speech, independent of remote API response.
        prefs.edit().putString("arrival_last_command", time() + " — enviando comando eWeLink").apply();
        GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — enviando comando",
                "Comando enviado à eWeLink; aguardando resposta.");
        speak(app, gate ? "Chegada detectada. Acendendo as lâmpadas. Deseja abrir o portão?"
                : "Chegada detectada. Acendendo as lâmpadas.");
        try {
            EwelinkApi.turnOnSelected(app, new EwelinkApi.TextCallback() {
                @Override public void onSuccess(String message) {
                    prefs.edit().putString("arrival_last_command", time() + " — resposta eWeLink: " + message).apply();
                    GeofenceReceiver.mostrarNotificacao(app, "eWeLink respondeu ao comando", message);
                }
                @Override public void onError(String message) {
                    prefs.edit().putString("arrival_last_command", time() + " — falha eWeLink: " + message).apply();
                    GeofenceReceiver.mostrarNotificacao(app, "Chegada detectada — falha nas luzes", message);
                    speak(app, "Não consegui acender as lâmpadas.");
                }
            });
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName();
            prefs.edit().putString("arrival_last_command", time() + " — erro ao enviar: " + error).apply();
            GeofenceReceiver.mostrarNotificacao(app, "Falha ao enviar comando", error);
        }
    }

    public static synchronized void handleExit(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                .putBoolean("dentro", false).putBoolean("outside_observed", true)
                .putBoolean("gate_pending", false).remove("gate_pending_at")
                .putString("arrival_last_event", time() + " — saída confirmada; chegada rearmada")
                .apply();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_GATE);
    }

    private static void showGateConfirmation(Context context) {
        Intent open = new Intent(context, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_OPEN_GATE);
        Intent cancel = new Intent(context, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_CANCEL_GATE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getBroadcast(context, 2201, open, flags);
        PendingIntent cancelPi = PendingIntent.getBroadcast(context, 2202, cancel, flags);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, "chegada")
                : new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Deseja abrir o portão?")
                .setContentText(EwelinkApi.getGateName(context) + " — confirme a abertura")
                .setAutoCancel(false).setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .addAction(new android.app.Notification.Action.Builder(
                        android.R.drawable.ic_menu_send, "ABRIR PORTÃO", openPi).build())
                .addAction(new android.app.Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "NÃO ABRIR", cancelPi).build());
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_GATE, b.build());
    }

    private static void speak(Context context, String text) {
        Context app = context.getApplicationContext();
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(app, status -> {
            TextToSpeech tts = holder[0];
            if (tts == null) return;
            if (status != TextToSpeech.SUCCESS) { tts.shutdown(); return; }
            int lang = tts.setLanguage(new Locale("pt", "BR"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { tts.shutdown(); }
                @Override public void onError(String id) { tts.shutdown(); }
            });
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "chegada_" + System.currentTimeMillis()) == TextToSpeech.ERROR) tts.shutdown();
        });
    }
}
