package com.techcell.caixadaloja;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class TechCellTerminalSyncService extends Service {
    private static final String CHANNEL_ID = "techcell_terminal_sync";
    private static final int NOTIFICATION_ID = 3602;

    private volatile boolean ativo;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

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

        manterCpuAtiva();
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
                GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
                if (!podeSincronizar(ctx)) {
                    ativo = false;
                    break;
                }

                TechCellSyncCoordinator.Resultado r =
                        TechCellSyncCoordinator.sincronizar(getApplicationContext());

                if (r.ocupado) {
                    // Outra tela/ciclo já está fazendo a mesma sincronização.
                    atualizarNotificacao("Sincronização em andamento…");
                    pausa = 4000;
                } else if (r.conflitoEstoque) {
                    atualizarNotificacao("Venda pendente • estoque mudou no Master");
                    pausa = 20000;
                } else if (r.erro != null && !r.erro.trim().isEmpty()) {
                    atualizarNotificacao("Master indisponível • nova tentativa automática");
                    pausa = 20000;
                } else {
                    if (r.vendasEnviadas > 0 || r.vendasRecebidas > 0 || r.produtosAlterados > 0) {
                        atualizarNotificacao(
                                "Sincronizado ✓ • enviadas " + r.vendasEnviadas +
                                        " • recebidas " + r.vendasRecebidas +
                                        " • produtos " + r.produtosAlterados);
                    } else {
                        atualizarNotificacao("Sincronizado com o Master ✓");
                    }
                    pausa = 8000;
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

    private void manterCpuAtiva() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TechCell:TerminalSync");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Throwable ignored) {}
    }

    private void liberarCpu() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {}
        wakeLock = null;
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel c = new NotificationChannel(
                        CHANNEL_ID,
                        "Sincronização Tech Cell",
                        NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Mantém vendas, estornos, produtos, preços e estoque sincronizados pela rede local.");
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
        liberarCpu();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
