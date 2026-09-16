package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** Location monitor with persistent, user-visible diagnostic status. */
public class ArrivalMonitorService extends Service {
    private static final String CHANNEL_ID = "chegada_monitor";
    private static final int NOTIFICATION_ID = 3101;
    private static final long INTERVAL_MS = 5000L;
    private static final long FASTEST_MS = 3000L;
    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private SharedPreferences prefs;
    private boolean firstFix = true;
    private long lastNotificationAt;

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", MODE_PRIVATE);
        if (!p.getBoolean("ativa", false)) {
            p.edit().putString("monitor_state", "Desativado: toque ATIVAR AUTOMAÇÃO").apply();
            return;
        }
        try {
            Intent i = new Intent(app, ArrivalMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
            p.edit().putString("monitor_state", "Inicialização solicitada; aguardando serviço/GPS")
                    .putString("monitor_error", "nenhum").apply();
        } catch (RuntimeException e) {
            p.edit().putString("monitor_state", "FALHA AO INICIAR SERVIÇO")
                    .putString("monitor_error", e.getClass().getSimpleName() + ": " + e.getMessage()).apply();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        fused = LocationServices.getFusedLocationProviderClient(this);
        prefs.edit().putLong("monitor_started_at", System.currentTimeMillis())
                .putString("monitor_state", "Serviço criado; iniciando GPS")
                .putString("monitor_error", "nenhum").apply();
        try {
            createChannels();
            startForeground(NOTIFICATION_ID, buildNotification("Monitor iniciado — aguardando GPS"),
                    Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
            beginUpdates();
        } catch (RuntimeException e) {
            fail("Não foi possível manter o monitor ativo", e);
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (prefs == null) prefs = getSharedPreferences("config", MODE_PRIVATE);
        if (!prefs.getBoolean("ativa", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (callback == null) beginUpdates();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (fused != null && callback != null) fused.removeLocationUpdates(callback);
        if (prefs != null) prefs.edit().putString("monitor_state", "Serviço encerrado; geofence reserva permanece")
                .apply();
        super.onDestroy();
    }

    private Notification buildNotification(String message) {
        Intent open = new Intent(this, ArrivalDiagnosticActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 3102, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Chegada Casa Rápido — GPS")
                .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pi).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW).build();
    }

    private void notifyStatus(String message) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotificationAt < 15000L) return;
        lastNotificationAt = now;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void fail(String reason, Exception e) {
        String detail = reason + ": " + e.getClass().getSimpleName();
        prefs.edit().putString("monitor_state", reason)
                .putString("monitor_error", detail + " — " + String.valueOf(e.getMessage())).apply();
    }

    private void beginUpdates() {
        if (callback != null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putString("monitor_state", "ERRO: permitir Localização precisa")
                    .putString("monitor_error", "ACCESS_FINE_LOCATION não concedida").apply();
            stopSelf();
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && !lm.isLocationEnabled()) {
            prefs.edit().putString("monitor_state", "ERRO: localização do celular desligada")
                    .putString("monitor_error", "Ative localização/GPS do Android").apply();
            notifyStatus("Localização desligada — ative o GPS");
            return;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_MS)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(INTERVAL_MS)
                .build();
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                for (Location loc : result.getLocations()) {
                    if (loc != null) process(loc);
                }
            }
        };
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnSuccessListener(unused -> {
                        prefs.edit().putString("monitor_state", "GPS solicitado; aguardando primeira posição")
                                .putString("monitor_error", "nenhum").apply();
                        notifyStatus("GPS solicitado — aguardando primeira posição");
                    })
                    .addOnFailureListener(e -> {
                        fail("Falha ao solicitar GPS", e);
                        notifyStatus("GPS falhou — toque para ver diagnóstico");
                        callback = null;
                    });
        } catch (RuntimeException e) {
            callback = null;
            fail("Falha ao solicitar GPS", e);
            notifyStatus("GPS falhou — toque para ver diagnóstico");
        }
    }

    private void process(Location loc) {
        if (!prefs.getBoolean("ativa", false)) { stopSelf(); return; }
        if (!prefs.getBoolean("casa_definida", false)) {
            prefs.edit().putString("monitor_state", "ERRO: residência não configurada").apply();
            return;
        }
        long ageMs = (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000L;
        if (ageMs > 60000L || ageMs < -5000L) {
            prefs.edit().putString("monitor_state", "Posição antiga ignorada (" + ageMs / 1000 + " s)")
                    .apply();
            return;
        }
        double homeLat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
        double homeLon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
        int radius = Math.max(50, prefs.getInt("raio", 100));
        float[] out = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), homeLat, homeLon, out);
        float distance = out[0];
        float accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 9999f;
        int distRounded = Math.round(distance);
        int accuracyRounded = Math.round(accuracy);
        long now = System.currentTimeMillis();
        float maxAccuracy = Math.max(65f, radius * 0.5f);
        String state = "GPS OK — " + distRounded + " m da casa (±" + accuracyRounded + " m)";
        prefs.edit().putLong("monitor_fix_at", now)
                .putInt("monitor_distance", distRounded)
                .putInt("monitor_accuracy", accuracyRounded)
                .putString("monitor_state", state).putString("monitor_error", "nenhum").apply();
        notifyStatus(state);

        if (accuracy > maxAccuracy) {
            prefs.edit().putString("monitor_state", "Precisão GPS insuficiente: ±" + accuracyRounded +
                    " m; aguardando melhor posição").apply();
            return;
        }

        float margin = Math.max(50f, Math.min(100f, radius * 0.25f));
        boolean inside = prefs.getBoolean("dentro", false);
        boolean outsideObserved = prefs.getBoolean("outside_observed", false);
        if (distance > radius + margin) {
            if (inside) ArrivalController.handleExit(this);
            else if (!outsideObserved) {
                prefs.edit().putBoolean("outside_observed", true)
                        .putString("arrival_last_event", "Saída confirmada por GPS às " + now +
                                " (" + distRounded + " m)").apply();
            }
            firstFix = false;
            return;
        }
        if (distance <= radius) {
            if (!inside && outsideObserved) {
                // Inclusive after a service restart when the first available fix is already inside.
                ArrivalController.handleArrival(this);
            } else if (firstFix && !outsideObserved) {
                // Starting while already at home is not an arrival; avoid unwanted commands.
                prefs.edit().putBoolean("dentro", true)
                        .putString("arrival_last_event", "Monitor iniciado dentro do raio: saia e volte para testar")
                        .apply();
            }
            firstFix = false;
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Monitor GPS de chegada", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mostra se a localização está chegando ao aplicativo");
        nm.createNotificationChannel(channel);
        NotificationChannel events = new NotificationChannel("chegada", "Chegada em casa",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(events);
    }
}
