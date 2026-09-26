package com.techcell.caixadaloja;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class TechCellMasterService extends Service {
    private static final String CHANNEL_ID = "techcell_master_local";
    private static final int NOTIFICATION_ID = 3201;
    private static final String DISCOVER = "TECHCELL_DISCOVER_V1";
    private static final String MASTER = "TECHCELL_MASTER_V1";
    private static final String PING = "TECHCELL_PING_V1";
    private static final String PONG = "TECHCELL_PONG_V1";

    private volatile boolean ativo;
    private DatagramSocket udp;
    private ServerSocket tcp;

    @Override public void onCreate() {
        super.onCreate();
        criarCanal();
        startForeground(NOTIFICATION_ID, notificacao("Servidor local iniciando…"));
        iniciar();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ativo) iniciar();
        return START_STICKY;
    }

    private synchronized void iniciar() {
        if (ativo) return;
        GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            stopSelf();
            return;
        }
        ativo = true;
        int porta = ctx.masterPort > 0 ? ctx.masterPort : 8765;
        atualizarNotificacao("Master ativo na rede local • porta " + porta);

        new Thread(this::loopUdp, "TechCell-Master-UDP").start();
        new Thread(() -> loopTcp(porta), "TechCell-Master-TCP").start();
    }

    private void loopUdp() {
        try {
            udp = new DatagramSocket(null);
            udp.setReuseAddress(true);
            udp.bind(new java.net.InetSocketAddress(TechCellLanClient.DISCOVERY_PORT));
            udp.setSoTimeout(1000);

            byte[] buffer = new byte[2048];
            while (ativo) {
                try {
                    DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                    udp.receive(p);
                    String msg = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                    if (!DISCOVER.equals(msg.trim())) continue;

                    GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
                    if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) continue;

                    String resposta = MASTER + "|" +
                            TechCellLanClient.encodeNome(ctx.nomeDispositivo) + "|" +
                            seguro(ctx.empresaUuid) + "|" +
                            seguro(ctx.filialUuid) + "|" +
                            seguro(ctx.dispositivoUuid) + "|" +
                            (ctx.masterPort > 0 ? ctx.masterPort : 8765);
                    byte[] out = resposta.getBytes(StandardCharsets.UTF_8);
                    udp.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (udp != null) udp.close();
        }
    }

    private void loopTcp(int porta) {
        try {
            tcp = new ServerSocket();
            tcp.setReuseAddress(true);
            tcp.bind(new java.net.InetSocketAddress(porta));
            tcp.setSoTimeout(1000);
            while (ativo) {
                try {
                    Socket cliente = tcp.accept();
                    atender(cliente);
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (tcp != null) tcp.close(); } catch (Throwable ignored) {}
        }
    }

    private void atender(Socket cliente) {
        try {
            cliente.setSoTimeout(2200);
            BufferedReader r = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8));
            String linha = r.readLine();
            if (linha != null && linha.startsWith(PING + "|")) {
                GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
                String resposta = PONG + "|" +
                        TechCellLanClient.encodeNome(ctx.nomeDispositivo) + "|" +
                        seguro(ctx.empresaUuid) + "|" +
                        seguro(ctx.filialUuid) + "|" +
                        seguro(ctx.dispositivoUuid) + "|" +
                        (ctx.masterPort > 0 ? ctx.masterPort : 8765);
                w.write(resposta);
                w.newLine();
                w.flush();
            }
        } catch (Throwable ignored) {
        } finally {
            try { cliente.close(); } catch (Throwable ignored) {}
        }
    }

    private String seguro(String s) {
        return s == null ? "" : s.replace("|", "").replace("\n", "").replace("\r", "");
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel c = new NotificationChannel(
                        CHANNEL_ID, "Servidor local Tech Cell", NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Mantém o Master disponível para os caixas na rede local.");
                nm.createNotificationChannel(c);
            }
        }
    }

    private Notification notificacao(String texto) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Tech Cell Master")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();
    }

    private void atualizarNotificacao(String texto) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notificacao(texto));
    }

    @Override public void onDestroy() {
        ativo = false;
        if (udp != null) udp.close();
        try { if (tcp != null) tcp.close(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
