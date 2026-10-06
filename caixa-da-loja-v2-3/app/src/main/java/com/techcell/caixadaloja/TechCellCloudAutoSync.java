package com.techcell.caixadaloja;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Laço leve de sincronização cloud do Master.
 * O serviço LAN continua sendo a base local; a nuvem é complementar.
 */
public final class TechCellCloudAutoSync {
    private static final AtomicBoolean RODANDO = new AtomicBoolean(false);
    private static volatile boolean parar;

    private TechCellCloudAutoSync() {}

    public static void garantir(Context context) {
        final Context app = context.getApplicationContext();
        parar = false;
        if (!RODANDO.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                while (!parar) {
                    GestaoDbHelper db = new GestaoDbHelper(app);
                    GestaoDbHelper.SyncContext ctx;
                    try { ctx = db.getSyncContext(); }
                    finally { db.close(); }

                    if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) break;

                    if (ctx.cloudAtiva && TechCellCloudSync.estaAutenticado(app)) {
                        try { TechCellCloudSync.sincronizarCadastrosPendentes(app); }
                        catch (Throwable ignored) {}
                        try { TechCellCloudSales.sincronizar(app, 250); }
                        catch (Throwable ignored) {}
                    }

                    try { Thread.sleep(60000L); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } finally {
                RODANDO.set(false);
            }
        }, "TechCell-Cloud-Auto").start();
    }

    public static void parar() {
        parar = true;
    }
}
