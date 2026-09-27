package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

public final class TechCellBatteryGuard {
    private TechCellBatteryGuard(){}

    public static boolean liberado(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable e) {
            return false;
        }
    }

    public static String status(Context context) {
        return liberado(context)
                ? "Proteção para tela bloqueada: LIBERADA ✓"
                : "Proteção para tela bloqueada: REVISAR BATERIA";
    }

    public static void abrirConfiguracao(Activity activity) {
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= 23 && !liberado(activity)) {
            try {
                Intent direto = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                direto.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(direto);
                return;
            } catch (Throwable ignored) {}
        }

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent lista = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                activity.startActivity(lista);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Intent detalhes = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            detalhes.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(detalhes);
        } catch (Throwable ignored) {}
    }
}
