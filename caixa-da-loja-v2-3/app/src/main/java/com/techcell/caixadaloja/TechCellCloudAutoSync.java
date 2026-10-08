package com.techcell.caixadaloja;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/** Laço leve de sincronização híbrida: LAN continua ativa e a nuvem cobre o remoto. */
public final class TechCellCloudAutoSync {
    private static final AtomicBoolean RODANDO = new AtomicBoolean(false);
    private static volatile boolean parar;
    private static volatile long ultimoRefreshAcesso;

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
                    if (!ctx.configurado) break;

                    if (ctx.cloudAtiva && TechCellCloudSync.estaAutenticado(app)) {
                        // Permissões alteradas pelo Master passam a valer sem exigir novo login.
                        long agora = System.currentTimeMillis();
                        if (agora - ultimoRefreshAcesso >= 15000L) {
                            try {
                                TechCellAccess.atualizarDaNuvem(app);
                                ultimoRefreshAcesso = agora;
                            } catch (Throwable ignored) {}
                        }

                        // Caixa/Gerente com permissão podem cadastrar/alterar produtos remotamente.
                        try { TechCellCloudProductPush.sincronizar(app); }
                        catch (Throwable ignored) {}

                        // Vendas: LAN e nuvem trabalham em paralelo.
                        try {
                            TechCellCloudSaleQueue.Resultado fila = TechCellCloudSaleQueue.sincronizar(app);
                            if (!fila.ok && !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                                GestaoDbHelper statusDb = new GestaoDbHelper(app);
                                try { statusDb.registrarResultadoEnvioVendas(0, fila.erro); }
                                finally { statusDb.close(); }
                            }
                        } catch (Throwable e) {
                            if (!"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                                GestaoDbHelper statusDb = new GestaoDbHelper(app);
                                try {
                                    String m = e.getMessage();
                                    statusDb.registrarResultadoEnvioVendas(0,
                                            m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m.trim());
                                } finally { statusDb.close(); }
                            }
                        }

                        try { TechCellCloudRealtimeSync.sincronizar(app); }
                        catch (Throwable ignored) {}

                        if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                            try { TechCellCloudSales.sincronizar(app, 250); }
                            catch (Throwable ignored) {}
                        }
                    }

                    try { Thread.sleep(3000L); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } finally { RODANDO.set(false); }
        }, "TechCell-Cloud-Auto").start();
    }

    public static void parar() { parar = true; }
}
