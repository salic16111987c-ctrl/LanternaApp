package com.cilassouza.chegadacasa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.car.app.notification.CarAppExtender;
import androidx.car.app.notification.CarNotificationManager;
import androidx.car.app.notification.CarPendingIntent;
import androidx.core.app.NotificationCompat;

/** A genuine arrival only. Android Auto decides if/when this appears as a heads-up card. */
public final class GateCarNotification {
    public static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL = "chegada_portao_carro_v1";

    private GateCarNotification() { }

    public static void show(Context context) {
        if (!GeofenceReceiver.gateConfirmationIsPending(context)) return;
        Context app = context.getApplicationContext();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Confirmar portão no carro", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Solicitação de confirmação do portão após chegada real");
            nm.createNotificationChannel(channel);
        }

        int normalFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(app, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_OPEN_GATE);
        Intent decline = new Intent(app, GeofenceReceiver.class).setAction(GeofenceReceiver.ACTION_CANCEL_GATE);
        PendingIntent openPi = PendingIntent.getBroadcast(app, 2201, open, normalFlags);
        PendingIntent declinePi = PendingIntent.getBroadcast(app, 2202, decline, normalFlags);
        PendingIntent voicePi = PendingIntent.getActivity(app, 2203,
                new Intent(app, GateVoiceActivity.class), normalFlags);

        String name = EwelinkApi.getGateName(app);
        NotificationCompat.Builder notice = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Deseja abrir o portão?")
                .setContentText(name + " — escolha SIM ou NÃO")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Chegada detectada. Confirme apenas se for seguro abrir " + name + "."))
                .setAutoCancel(false).setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(voicePi)
                .addAction(android.R.drawable.ic_btn_speak_now, "RESPONDER POR VOZ", voicePi)
                .addAction(android.R.drawable.ic_menu_send, "SIM, ABRIR", openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "NÃO", declinePi);
        try {
            Intent carIntent = new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(app, ChegadaCarAppService.class));
            int carFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) carFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent carPi = CarPendingIntent.getCarApp(app, 2210, carIntent, carFlags);
            notice.extend(new CarAppExtender.Builder()
                    .setContentTitle("Chegada em casa — portão")
                    .setContentText("Abrir " + name + "? Confirmação obrigatória.")
                    .setContentIntent(carPi)
                    .setImportance(NotificationManager.IMPORTANCE_HIGH)
                    .addAction(android.R.drawable.ic_menu_send, "SIM, ABRIR", openPi)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "NÃO ABRIR", declinePi)
                    .build());
            CarNotificationManager.from(app).notify(NOTIFICATION_ID, notice);
            record(app, "Confirmação enviada ao Android e Android Auto; exibição na central depende do host");
        } catch (RuntimeException e) {
            // Keep the essential phone confirmation even if a particular car host rejects it.
            nm.notify(NOTIFICATION_ID, notice.build());
            record(app, "Notificação no celular; extensão Android Auto indisponível: "
                    + e.getClass().getSimpleName());
        }
    }

    public static void cancel(Context context) {
        Context app = context.getApplicationContext();
        try { CarNotificationManager.from(app).cancel(NOTIFICATION_ID); }
        catch (RuntimeException e) {
            ((NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE))
                    .cancel(NOTIFICATION_ID);
        }
    }

    private static void record(Context context, String status) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        p.edit().putString("car_notification_status", status).apply();
    }
}
