package com.techcell.caixadaloja;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Laço leve de sincronização cloud.
 *
 * Durante a fase de testes multi-local, a nuvem trabalha nos aparelhos vinculados
 * mesmo quando eles não estão na mesma rede. O SQLite continua como cache local.
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

                    if (!ctx.configurado) break;

                    if (ctx.cloudAtiva && TechCellCloudSync.estaAutenticado(app)) {
                        try { TechCellCloudRealtimeSync.sincronizar(app); }
                        catch (Throwable ignored) {}

                        // O Master físico consolida as vendas recebidas pela LAN e as
                        // publica na nuvem. Os terminais remotos apenas recebem esse histórico.
                        if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                            try { TechCellCloudSales.sincronizar(app, 250); }
                            catch (Throwable ignored) {}
                        }
                    }

                    // Intervalo curto proposital nesta fase para permitir testes em
                    // cidades diferentes com atualização visual quase imediata.
                    try { Thread.sleep(3000L); }
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
