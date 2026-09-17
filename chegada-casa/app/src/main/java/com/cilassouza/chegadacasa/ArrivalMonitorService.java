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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.Calendar;

/** Visible location service; failures and last fix are retained for diagnosis. */
public class ArrivalMonitorService extends Service {
    private static final String CHANNEL_ID = "chegada_monitor";
    private static final int NOTIFICATION_ID = 3101;
    private static final long INTERVAL_MS = 5000L;
    private static final long HOME_INTERVAL_MS = 30000L;
    private static final long FAR_INTERVAL_MS = 60000L;
    private static final long NEAR_INTERVAL_MS = 20000L;
    private static final int MODE_FAR = 0, MODE_HOME = 1, MODE_NEAR = 2, MODE_FAST = 3;
    private static final long WATCHDOG_MS = 30000L;
    private static final long STALE_MS = 90000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private SharedPreferences prefs;
    private long lastFixElapsed;
    private long lastNotification;
    private long serviceStartedElapsed = SystemClock.elapsedRealtime();
    private boolean foreground;
    private boolean firstFix = true;
    private int consecutiveOutside;
    private int profileMode = -1;
    private Location lastMotionFix;
    private float lastMotionDistance = -1f;

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", MODE_PRIVATE);
        if (!p.getBoolean("ativa", false)) {
            p.edit().putString("monitor_state", "Desativado: toque ATIVAR AUTOMAÇÃO").apply();
            return;
        }
        p.edit().putString("monitor_state", "Solicitando início do serviço GPS").apply();
        try {
            Intent intent = new Intent(app, ArrivalMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
            else app.startService(intent);
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
                .putString("monitor_state", "Serviço criado; preparando GPS")
                .putString("monitor_error", "nenhum").apply();
        try {
            createChannels();
            Notification notification = buildNotification("Monitor ativo — aguardando GPS");
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION_ID, notification);
            foreground = true;
        } catch (RuntimeException e) {
            failure("Android recusou o serviço em primeiro plano", e);
            stopSelf();
            return;
        }
        beginUpdates();
        handler.postDelayed(watchdog, WATCHDOG_MS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("ativa", false)) { stopSelf(); return START_NOT_STICKY; }
        if (foreground) ensureProfile();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /** No clock-based throttling: the gate must remain available 24 hours. */
    private int desiredProfile() {
        if (prefs.getBoolean("dentro", false)) return MODE_HOME;
        int distance = prefs.getInt("monitor_distance", -1);
        int accuracy = prefs.getInt("monitor_accuracy", 9999);
        long fixAt = prefs.getLong("monitor_fix_at", 0L);
        // Missing/stale/coarse position: sample economically but more often until known.
        if (distance < 0 || accuracy > 600 || fixAt <= 0L
                || Math.abs(System.currentTimeMillis() - fixAt) > 180000L) return MODE_NEAR;
        if (distance > 2600) return MODE_FAR;
        if (distance <= 250) return MODE_FAST; // Never miss the final approach.
        boolean moving = System.currentTimeMillis() < prefs.getLong("motion_until", 0L);
        boolean approaching = prefs.getBoolean("motion_toward", false);
        if (moving && (distance <= 2000 || (distance <= 2500 && approaching))) return MODE_FAST;
        return MODE_NEAR;
    }

    private long profileInterval(int mode) {
        if (mode == MODE_FAR) return FAR_INTERVAL_MS;
        if (mode == MODE_HOME) return HOME_INTERVAL_MS;
        if (mode == MODE_NEAR) return NEAR_INTERVAL_MS;
        return INTERVAL_MS;
    }

    private String profileName(int mode) {
        if (mode == MODE_FAR) return "ECONÔMICO: ALÉM DE 2 KM";
        if (mode == MODE_HOME) return "ECONÔMICO: EM CASA";
        if (mode == MODE_NEAR) return "OBSERVANDO MOVIMENTO / APROXIMAÇÃO";
        return "GPS PRECISO: APROXIMAÇÃO";
    }

    private void ensureProfile() {
        if (!foreground || !prefs.getBoolean("ativa", false)) return;
        if (callback == null) beginUpdates();
        else if (profileMode != desiredProfile()) restartUpdates();
    }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!prefs.getBoolean("ativa", false)) { stopSelf(); return; }
            if (!foreground) return;
            long elapsed = SystemClock.elapsedRealtime();
            if (callback == null || profileMode != desiredProfile()) {
                // Clock transitions at 06:00/18:00 are checked every 30 seconds.
                restartUpdates();
            } else {
                long staleLimit = Math.max(STALE_MS, profileInterval(profileMode) * 3L);
                if ((lastFixElapsed == 0L && elapsed - serviceStartedElapsed > staleLimit)
                        || (lastFixElapsed > 0L && elapsed - lastFixElapsed > staleLimit)) {
                    prefs.edit().putString("monitor_state", "Localização atrasada; tentando recuperar")
                            .putString("monitor_error", "Sem localização recente").apply();
                    restartUpdates();
                }
            }
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    private void restartUpdates() {
        if (callback != null) { fused.removeLocationUpdates(callback); callback = null; }
        profileMode = -1;
        lastFixElapsed = 0L;
        serviceStartedElapsed = SystemClock.elapsedRealtime();
        beginUpdates();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(watchdog);
        if (fused != null && callback != null) fused.removeLocationUpdates(callback);
        if (prefs != null && prefs.getBoolean("ativa", false)) {
            prefs.edit().putString("monitor_state", "Monitor interrompido; geofence de reserva continua")
                    .apply();
        }
        super.onDestroy();
    }

    private Notification buildNotification(String text) {
        PendingIntent click = PendingIntent.getActivity(this, 3102,
                new Intent(this, ArrivalDiagnosticActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Chegada Casa Rápido — GPS")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(click).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW).build();
    }

    private void notifyStatus(String message) {
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed - lastNotification < 15000L) return;
        lastNotification = elapsed;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void failure(String reason, Exception e) {
        prefs.edit().putString("monitor_state", reason)
                .putString("monitor_error", e.getClass().getSimpleName() + ": " + e.getMessage()).apply();
        if (foreground) notifyStatus("Falha no monitor — toque para verificar");
    }

    private void beginUpdates() {
        if (callback != null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putString("monitor_state", "ERRO: Localização precisa não autorizada")
                    .putString("monitor_error", "Permissão ACCESS_FINE_LOCATION ausente").apply();
            stopSelf();
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || !lm.isLocationEnabled()) {
            prefs.edit().putString("monitor_state", "GPS desligado; aguardando ativação")
                    .putString("monitor_error", "Ative Localização nas configurações do celular").apply();
            notifyStatus("GPS desligado — ative a localização");
            return;
        }
        int mode = desiredProfile();
        long period = profileInterval(mode);
        int priority = mode == MODE_FAST ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        LocationRequest request = new LocationRequest.Builder(priority, period)
                .setMinUpdateIntervalMillis(mode == MODE_FAST ? 3000L : period)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(period).build();
        LocationCallback next = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null) return;
                for (Location loc : result.getLocations()) if (loc != null) process(loc);
            }
            @Override public void onLocationAvailability(LocationAvailability availability) {
                if (availability != null && !availability.isLocationAvailable()) {
                    prefs.edit().putString("monitor_state", "GPS temporariamente indisponível")
                            .putString("monitor_error", "Fused Location relatou indisponibilidade").apply();
                }
            }
        };
        callback = next;
        profileMode = mode;
        prefs.edit().putString("monitor_profile", profileName(mode))
                .putString("monitor_state", "Monitor: " + profileName(mode)).apply();
        notifyStatus(profileName(mode));
        try {
            fused.requestLocationUpdates(request, next, Looper.getMainLooper())
                    .addOnSuccessListener(unused -> {
                        if (callback == next && lastFixElapsed == 0L) {
                            prefs.edit().putString("monitor_state", "GPS registrado; aguardando posição").apply();
                            notifyStatus("GPS registrado — aguardando posição");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (callback == next) callback = null;
                        failure("Falha ao registrar GPS", e);
                    });
        } catch (RuntimeException e) {
            if (callback == next) callback = null;
            failure("Falha ao solicitar GPS", e);
        }
    }

    private void process(Location loc) {
        if (!prefs.getBoolean("ativa", false)) { stopSelf(); return; }
        long age = (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000L;
        if (age < -5000L || age > 60000L) {
            prefs.edit().putString("monitor_state", "Posição GPS antiga ignorada: " + age / 1000L + " s").apply();
            return;
        }
        lastFixElapsed = SystemClock.elapsedRealtime();
        boolean mock = loc.isFromMockProvider();
        // Disable any previously pending gate action immediately when fake GPS starts.
        SharedPreferences.Editor evidence = prefs.edit().putBoolean("monitor_mock", mock);
        if (mock) evidence.putBoolean("gate_pending", false)
                .putBoolean("gate_origin_mock", true).remove("gate_pending_at");
        evidence.apply();
        if (mock) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2002);
        if (!prefs.getBoolean("casa_definida", false)) {
            prefs.edit().putString("monitor_state", "ERRO: residência não configurada").apply();
            return;
        }
        double lat = Double.longBitsToDouble(prefs.getLong("lat", 0L));
        double lon = Double.longBitsToDouble(prefs.getLong("lon", 0L));
        if (lat == 0d && lon == 0d) {
            prefs.edit().putString("monitor_state", "ERRO: coordenadas da casa inválidas").apply();
            return;
        }
        int radius = Math.max(50, prefs.getInt("raio", 100));
        float[] measured = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lat, lon, measured);
        float distance = measured[0];
        float accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 9999f;
        int dist = Math.round(distance);
        int acc = Math.round(accuracy);
        // Motion inferred from speed or successive fresh locations; no permanent sensor polling.
        long nowMotion = System.currentTimeMillis();
        boolean moving = loc.hasSpeed() && accuracy <= 250f && loc.getSpeed() >= 1.3f;
        boolean toward = false;
        if (lastMotionFix != null) {
            long dtMs = (loc.getElapsedRealtimeNanos() - lastMotionFix.getElapsedRealtimeNanos()) / 1000000L;
            float step = loc.distanceTo(lastMotionFix);
            float threshold = Math.max(35f, (accuracy + lastMotionFix.getAccuracy()) * 0.8f);
            if (dtMs > 0L && dtMs <= 300000L && accuracy <= 400f
                    && lastMotionFix.hasAccuracy() && lastMotionFix.getAccuracy() <= 400f
                    && step >= threshold) {
                moving = true;
                toward = lastMotionDistance >= 0f
                        && lastMotionDistance - distance >= Math.max(25f, accuracy * 0.35f);
            }
        }
        lastMotionFix = new Location(loc);
        lastMotionDistance = distance;
        SharedPreferences.Editor motion = prefs.edit();
        if (moving) motion.putLong("motion_until", nowMotion + 120000L)
                .putBoolean("motion_toward", toward);
        else if (nowMotion > prefs.getLong("motion_until", 0L))
            motion.putBoolean("motion_toward", false);
        motion.apply();
        String status = profileName(profileMode) + " — "
                + (mock ? "GPS FICTÍCIO — portão bloqueado — " : "GPS OK — ")
                + dist + " m da casa (±" + acc + " m)";
        prefs.edit().putLong("monitor_fix_at", System.currentTimeMillis())
                .putInt("monitor_distance", dist).putInt("monitor_accuracy", acc)
                .putString("monitor_state", status).putString("monitor_error", "nenhum").apply();
        notifyStatus(status);
        // Switching to high precision must not wait for a fine-accuracy fix.
        handler.post(this::ensureProfile);
        if (accuracy > Math.max(75f, radius * 0.6f)) {
            prefs.edit().putString("monitor_state", "Aguardando GPS mais preciso: ±" + acc + " m").apply();
            return;
        }
        float margin = Math.max(60f, Math.min(120f, radius * 0.3f));
        if (distance > radius + margin) {
            consecutiveOutside++;
            if (consecutiveOutside >= 2) {
                if (prefs.getBoolean("dentro", false)) ArrivalController.handleExit(this);
                else if (!prefs.getBoolean("outside_observed", false)) {
                    prefs.edit().putBoolean("outside_observed", true)
                            .putString("arrival_last_event", "Saída confirmada: " + dist + " m").apply();
                }
            }
            // Home -> outside: switch to fast immediately after two reliable outside fixes.
            handler.post(this::ensureProfile);
            firstFix = false;
            return;
        }
        consecutiveOutside = 0;
        if (distance <= radius) {
            boolean inside = prefs.getBoolean("dentro", false);
            boolean outside = prefs.getBoolean("outside_observed", false);
            if (!inside && outside) ArrivalController.handleArrival(this, mock);
            else if (firstFix && !outside && !inside) {
                prefs.edit().putBoolean("dentro", true)
                        .putString("arrival_last_event", "Iniciou dentro da casa: saia e volte para testar").apply();
            }
            // Arrival -> home: drop the continuous high-accuracy GPS request.
            handler.post(this::ensureProfile);
        }
        firstFix = false;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel gps = new NotificationChannel(CHANNEL_ID, "Monitor GPS de chegada",
                NotificationManager.IMPORTANCE_LOW);
        gps.setDescription("Mostra se o GPS está recebendo posições");
        nm.createNotificationChannel(gps);
        NotificationChannel events = new NotificationChannel("chegada", "Chegada em casa",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(events);
    }
}
