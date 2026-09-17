package com.cilassouza.chegadacasa;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit, temporary, single-use authorization to test the REAL gate from mock GPS.
 * Mock locations are never allowed to actuate the gate outside this mode.
 */
public final class GateTestMode {
    private static final long WINDOW_MS = 10L * 60L * 1000L;
    private static final long MAX_MOCK_FIX_AGE_MS = 120000L;

    private GateTestMode() { }

    public static boolean isArmed(SharedPreferences p) {
        long remaining = p.getLong("gate_test_until", 0L) - System.currentTimeMillis();
        return p.getBoolean("gate_test_armed", false)
                && remaining > 0L && remaining <= WINDOW_MS;
    }

    public static boolean isAuthorizedPending(SharedPreferences p) {
        long now = System.currentTimeMillis();
        long remaining = p.getLong("gate_test_until", 0L) - now;
        long fixAge = now - p.getLong("monitor_fix_at", 0L);
        int radius = Math.max(50, p.getInt("raio", 100));
        return p.getBoolean("gate_test_pending", false)
                && p.getBoolean("gate_pending", false)
                && p.getBoolean("gate_origin_mock", true)
                && p.getBoolean("monitor_mock", false)
                && remaining > 0L && remaining <= WINDOW_MS
                && fixAge >= 0L && fixAge <= MAX_MOCK_FIX_AGE_MS
                && p.getInt("monitor_accuracy", 9999) <= Math.max(75, Math.round(radius * 0.6f));
    }

    public static void arm(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        p.edit().putBoolean("gate_test_armed", true)
                .putLong("gate_test_until", System.currentTimeMillis() + WINDOW_MS)
                .putBoolean("gate_test_pending", false)
                .putBoolean("gate_pending", false)
                .putBoolean("gate_origin_mock", true)
                .remove("gate_pending_at")
                .putString("gate_voice_status", "TESTE autorizado por 10 min e 1 chegada fictícia; SIM pode abrir o portão REAL")
                .apply();
        GateCarNotification.cancel(context);
    }

    public static void cancel(Context context) {
        SharedPreferences p = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        p.edit().putBoolean("gate_test_armed", false)
                .putBoolean("gate_test_pending", false)
                .putLong("gate_test_until", 0L)
                .putBoolean("gate_pending", false)
                .putBoolean("gate_origin_mock", true)
                .remove("gate_pending_at")
                .putString("gate_voice_status", "Modo de teste do portão cancelado; nenhum comando enviado")
                .apply();
        GateCarNotification.cancel(context);
    }

    /** Must be called before dispatching a gate pulse; deny the pulse if persistence fails. */
    public static boolean consume(SharedPreferences p) {
        if (!isAuthorizedPending(p)) return false;
        return p.edit().putBoolean("gate_test_armed", false)
                .putBoolean("gate_test_pending", false)
                .putLong("gate_test_until", 0L)
                .putBoolean("gate_pending", false)
                .remove("gate_pending_at").commit();
    }
}
