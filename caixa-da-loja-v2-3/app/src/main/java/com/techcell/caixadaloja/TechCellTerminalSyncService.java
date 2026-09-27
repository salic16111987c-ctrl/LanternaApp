package com.techcell.caixadaloja;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class TechCellTerminalSyncService extends Service {
    private static final String CHANNEL_ID = "techcell_terminal_sync";
    private static final int NOTIFICATION_ID = 3602;

    private volatile boolean ativo;
    private Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        criarCanal();
        iniciarForeground("Sincronização automática iniciando…");
        iniciarLoop();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ativo) iniciarLoop();
        return START_STICKY;
    }

    private synchronized void iniciarLoop() {
        if (ativo) return;

        GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
        if (!podeSincronizar(ctx)) {
            stopSelf();
            return;
        }

        ativo = true;
        worker = new Thread(this::loop, "TechCell-Terminal-Background-Sync");
        worker.start();
    }

    private boolean podeSincronizar(GestaoDbHelper.SyncContext ctx) {
        return ctx != null &&
                ctx.configurado &&
                !"MASTER".equalsIgnoreCase(ctx.papelDispositivo) &&
                ctx.masterHost != null && !ctx.masterHost.trim().isEmpty() &&
                ctx.masterAuthToken != null && !ctx.masterAuthToken.trim().isEmpty();
    }

    private void loop() {
        while (ativo) {
            long pausa = 8000;
            try {
                GestaoDbHelper db = new GestaoDbHelper(this);
                GestaoDbHelper.SyncContext ctx = db.getSyncContext();
                if (!podeSincronizar(ctx)) {
                    ativo = false;
                    break;
                }

                TechCellSaleSync.Resultado vendas = null;
                if (db.countVendasPendentesMaster() > 0) {
                    vendas = TechCellSaleSync.enviarPendentes(getApplicationContext(), "");
                }

                if (vendas != null && vendas.erro != null && !vendas.erro.trim().isEmpty()) {
                    atualizarNotificacao("Venda pendente • tentando novamente automaticamente");
                    pausa = 20000;
                } else {
                    TechCellProductSync.Resultado produtos =
                            TechCellProductSync.puxarAlteracoes(getApplicationContext());

                    if (produtos.erro != null && !produtos.erro.trim().isEmpty()) {
                        atualizarNotificacao("Master indisponível • nova tentativa automática");
                        pausa = 20000;
                    } else {
                        int vendasEnviadas = vendas == null ? 0 : vendas.enviadas;
                        int alteracoes = produtos.total();
                        if (vendasEnviadas > 0 || alteracoes > 0) {
                            atualizarNotificacao(
                                    "Sincronizado ✓ • vendas " + vendasEnviadas +
                                            " • alterações " + alteracoes);
                        } else {
                            atualizarNotificacao("Sincronizado com o Master ✓");
                        }
                        pausa = 8000;
                    }
                }
            } catch (Throwable e) {
                atualizarNotificacao("Rede local indisponível • tentando novamente");
                pausa = 20000;
            }

            try {
                Thread.sleep(pausa);
            } catch (InterruptedException ignored) {
                // Revalida o estado imediatamente.
            }
        }

        stopSelf();
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel c = new NotificationChannel(
                        CHANNEL_ID,
                        "Sincronização Tech Cell",
                        NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Mantém vendas, produtos, preços e estoque sincronizados pela rede local.");
                nm.createNotificationChannel(c);
            }
        }
    }

    private Notification notificacao(String texto) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Tech Cell • sincronização automática")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();
    }

    private void iniciarForeground(String texto) {
        Notification n = notificacao(texto);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void atualizarNotificacao(String texto) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notificacao(texto));
    }

    @Override public void onDestroy() {
        ativo = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
