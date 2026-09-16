package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

/** Restore the passive backup geofence after BOOT_COMPLETED or APK replacement.
 * Do not start a location foreground service from background: Android may prohibit it.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("config", Context.MODE_PRIVATE);
        if (!p.getBoolean("ativa", false) || !p.getBoolean("casa_definida", false)) return;
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= 29 && app.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)) {
            p.edit().putString("monitor_state", "Após reinício: permissão de localização ausente").apply();
            return;
        }
        double lat = Double.longBitsToDouble(p.getLong("lat", 0L));
        double lon = Double.longBitsToDouble(p.getLong("lon", 0L));
        int radius = Math.max(50, p.getInt("raio", 100));
        if (lat == 0d && lon == 0d) return;
        Geofence geofence = new Geofence.Builder().setRequestId("casa")
                .setCircularRegion(lat, lon, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(0).addGeofence(geofence).build();
        Intent receiver = new Intent(app, GeofenceReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(app, 77, receiver, flags);
        final PendingResult pending = goAsync();
        try {
            LocationServices.getGeofencingClient(app).removeGeofences(pi).addOnCompleteListener(removed -> {
                try {
                    LocationServices.getGeofencingClient(app).addGeofences(request, pi)
                            .addOnCompleteListener(added -> {
                                p.edit().putString("monitor_state", added.isSuccessful()
                                        ? "Geofence restaurado após reinício; abra o app para GPS contínuo"
                                        : "Falha ao restaurar geofence: " + String.valueOf(added.getException()))
                                        .apply();
                                pending.finish();
                            });
                } catch (RuntimeException e) {
                    p.edit().putString("monitor_error", "Reinício: " + e.getMessage()).apply();
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            p.edit().putString("monitor_error", "Reinício: " + e.getMessage()).apply();
            pending.finish();
        }
    }
}
