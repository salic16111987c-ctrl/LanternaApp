package com.cilassouza.chegadacasa;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
    public static final String ACTION_OPEN_GATE = "com.cilassouza.chegadacasa.OPEN_GATE";
    public static final String ACTION_CANCEL_GATE = "com.cilassouza.chegadacasa.CANCEL_GATE";
    private static final long GATE_CONFIRM_WINDOW_MS = 5 * 60 * 1000L;
    private static final int NOTIFICATION_ARRIVAL = 2001;
    private static final int NOTIFICATION_GATE = 2002;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (ACTION_OPEN_GATE.equals(action)) {
            abrirPortaoConfirmado(context);
            return;
        }
        if (ACTION_CANCEL_GATE.equals(action)) {
            cancelarAberturaPortao(context, true);
            return;
        }

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) return;

        int transition = event.getGeofenceTransition();
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            prefs.edit()
                    .putBoolean("dentro", false)
                    .putBoolean("gate_pending", false)
                    .remove("gate_pending_at")
                    .apply();
            cancelarNotificacaoPortao(context);
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

        boolean temPortao = EwelinkApi.hasSession(context) && EwelinkApi.hasGate(context);
        SharedPreferences.Editor chegada = prefs.edit()
                .putBoolean("dentro", true)
                .putLong("ultimo_alerta", agora);
        if (temPortao) {
            chegada.putBoolean("gate_pending", true)
                    .putLong("gate_pending_at", agora);
        }
        chegada.apply();

        if (temPortao) mostrarPerguntaPortao(context);

        if (EwelinkApi.hasSession(context) && EwelinkApi.selectedCount(context) > 0) {
            PendingResult pending = goAsync();
            EwelinkApi.turnOnSelected(context.getApplicationContext(), new EwelinkApi.TextCallback() {
                @Override
                public void onSuccess(String message) {
                    falar(context, temPortao
                            ? "Lâmpadas acesas. Deseja abrir o portão?"
                            : "Lâmpadas acesas");
                    mostrarNotificacao(context,
                            "Chegada detectada — luzes acionadas",
                            message);
                    pending.finish();
                }

                @Override
                public void onError(String message) {
                    if (temPortao) falar(context, "Deseja abrir o portão?");
                    mostrarNotificacao(context,
                            "Chegada detectada — falha nas luzes",
                            message);
                    pending.finish();
                }
            });
        } else if (temPortao) {
            falar(context, "Deseja abrir o portão?");
        } else {
            mostrarNotificacao(context,
                    "Você chegou perto de casa",
                    "O celular entrou no raio configurado.");
        }
    }

    private void abrirPortaoConfirmado(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean pendingFlag = prefs.getBoolean("gate_pending", false);
        long pendingAt = prefs.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - pendingAt;

        if (!pendingFlag || pendingAt <= 0 || age < 0 || age > GATE_CONFIRM_WINDOW_MS) {
            prefs.edit().putBoolean("gate_pending", false).remove("gate_pending_at").apply();
            cancelarNotificacaoPortao(context);
            mostrarNotificacao(context,
                    "Confirmação do portão expirada",
                    "Por segurança, a chegada precisa ser detectada novamente antes de abrir o portão.");
            return;
        }

        prefs.edit().putBoolean("gate_pending", false).remove("gate_pending_at").apply();
        cancelarNotificacaoPortao(context);

        PendingResult pending = goAsync();
        EwelinkApi.pulseGate(context.getApplicationContext(), new EwelinkApi.TextCallback() {
            @Override
            public void onSuccess(String message) {
                falar(context, "Portão acionado");
                mostrarNotificacao(context, "Portão acionado", message);
                pending.finish();
            }

            @Override
            public void onError(String message) {
                mostrarNotificacao(context, "Falha ao acionar o portão", message);
                pending.finish();
            }
        });
    }

    private void cancelarAberturaPortao(Context context, boolean avisar) {
        context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .edit().putBoolean("gate_pending", false).remove("gate_pending_at").apply();
        cancelarNotificacaoPortao(context);
        if (avisar) {
            mostrarNotificacao(context,
                    "Portão não aberto",
                    "A abertura foi cancelada. Nenhum comando foi enviado ao eWeLink.");
        }
    }

    public static boolean gateConfirmationIsPending(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("gate_pending", false)) return false;
        long at = prefs.getLong("gate_pending_at", 0L);
        long age = System.currentTimeMillis() - at;
        return at > 0 && age >= 0 && age <= GATE_CONFIRM_WINDOW_MS;
    }

    private static void mostrarPerguntaPortao(Context context) {
        criarCanal(context);

        Intent abrirIntent = new Intent(context, GeofenceReceiver.class).setAction(ACTION_OPEN_GATE);
        Intent cancelarIntent = new Intent(context, GeofenceReceiver.class).setAction(ACTION_CANCEL_GATE);
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

    private static void cancelarNotificacaoPortao(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_GATE);
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
                    "chegada_" + System.currentTimeMillis());
            if (result == TextToSpeech.ERROR) tts.shutdown();
        });
    }

    private static void criarCanal(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    "chegada", "Chegada em casa", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos do acionamento por proximidade e confirmação do portão");
            nm.createNotificationChannel(ch);
        }
    }

    public static void mostrarNotificacao(Context context, String titulo, String texto) {
        criarCanal(context);
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, "chegada")
                : new android.app.Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ARRIVAL, b.build());
    }
}
