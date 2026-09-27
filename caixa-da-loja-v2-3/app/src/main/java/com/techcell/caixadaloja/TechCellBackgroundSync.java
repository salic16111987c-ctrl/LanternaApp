package com.techcell.caixadaloja;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class TechCellBackgroundSync {
    private TechCellBackgroundSync(){}

    public static void garantir(Context context) {
        Context app = context.getApplicationContext();
        GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(app).getSyncContext();

        if (!ctx.configurado) {
            parar(app);
            return;
        }

        if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            try { app.stopService(new Intent(app, TechCellTerminalSyncService.class)); } catch (Throwable ignored) {}
            iniciar(app, new Intent(app, TechCellMasterService.class));
            return;
        }

        try { app.stopService(new Intent(app, TechCellMasterService.class)); } catch (Throwable ignored) {}

        boolean pronto = ctx.masterHost != null && !ctx.masterHost.trim().isEmpty() &&
                ctx.masterAuthToken != null && !ctx.masterAuthToken.trim().isEmpty();
        if (pronto) {
            iniciar(app, new Intent(app, TechCellTerminalSyncService.class));
        } else {
            try { app.stopService(new Intent(app, TechCellTerminalSyncService.class)); } catch (Throwable ignored) {}
        }
    }

    public static void parar(Context context) {
        Context app = context.getApplicationContext();
        try { app.stopService(new Intent(app, TechCellMasterService.class)); } catch (Throwable ignored) {}
        try { app.stopService(new Intent(app, TechCellTerminalSyncService.class)); } catch (Throwable ignored) {}
    }

    private static void iniciar(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable ignored) {
            // Se o Android bloquear uma partida em segundo plano, o próximo acesso ao app tenta novamente.
        }
    }
}
