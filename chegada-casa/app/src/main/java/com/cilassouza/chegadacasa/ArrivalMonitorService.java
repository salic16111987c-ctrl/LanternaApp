package com.cilassouza.chegadacasa;

import android.Manifest;
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
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Monitor de chegada em primeiro plano. Mantem atualizacoes de localizacao
 * suficientemente frequentes para evitar o atraso comum do geofence em segundo plano.
 */
public class ArrivalMonitorService extends Service {
    private static final String CHANNEL_ID = "chegada_monitor";
    private static final int NOTIFICATION_ID = 3101;
    private static final long UPDATE_INTERVAL_MS = 5000L;
    private static final long MIN_UPDATE_INTERVAL_MS = 3000L;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private SharedPreferences prefs;
    private boolean primeiraLeitura = true;

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        if (!app.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getBoolean("ativa", false)) return;

        try {
            Intent intent = new Intent(app, ArrivalMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Exception ignored) {
            // O geofence continua funcionando como reserva se o Android bloquear o servico.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        fused = LocationServices.getFusedLocationProviderClient(this);
        criarCanal();
        iniciarForeground();
        iniciarLocalizacao();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("ativa", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (fused != null && callback != null) {
            fused.removeLocationUpdates(callback);
        }
        super.onDestroy();
    }

    private void iniciarForeground() {
        Intent open = new Intent(this, FixedMainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 3102, open, flags);

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, CHANNEL_ID)
                : new android.app.Notification.Builder(this);

        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Chegada Casa ativo")
                .setContentText("Monitorando sua aproximação para acionar as luzes sem atraso")
                .setOngoing(true)
                .setContentIntent(pi)
                .setCategory(android.app.Notification.CATEGORY_SERVICE)
                .setPriority(android.app.Notification.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, b.build());
        }
    }

    private void iniciarLocalizacao() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(5f)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) processarLocalizacao(loc);
            }
        };

        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSelf();
        }
    }

    private void processarLocalizacao(Location atual) {
        if (!prefs.getBoolean("ativa", false)) {
            stopSelf();
            return;
        }
        if (!prefs.getBoolean("casa_definida", false)) return;

        double lat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
        double lon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
        int raio = Math.max(50, prefs.getInt("raio", 100));

        float[] distance = new float[1];
        Location.distanceBetween(atual.getLatitude(), atual.getLongitude(), lat, lon, distance);
        float metros = distance[0];
        boolean dentro = prefs.getBoolean("dentro", false);
        float histerese = Math.max(50f, Math.min(100f, raio * 0.25f));

        // Na primeira leitura nao dispara se o usuario ja abriu o app estando em casa.
        if (primeiraLeitura) {
            primeiraLeitura = false;
            if (metros <= raio) {
                prefs.edit().putBoolean("dentro", true).apply();
            } else if (metros > raio + histerese) {
                prefs.edit().putBoolean("dentro", false).apply();
            }
            return;
        }

        if (dentro) {
            if (metros > raio + histerese) {
                ArrivalController.handleExit(this);
            }
            return;
        }

        if (metros <= raio) {
            ArrivalController.handleArrival(this);
        }
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Monitor de chegada", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Mantem a deteccao de chegada ativa em segundo plano");
            nm.createNotificationChannel(ch);

            NotificationChannel chegada = new NotificationChannel(
                    "chegada", "Chegada em casa", NotificationManager.IMPORTANCE_HIGH);
            chegada.setDescription("Avisos do acionamento por proximidade e confirmacao do portao");
            nm.createNotificationChannel(chegada);
        }
    }
}
