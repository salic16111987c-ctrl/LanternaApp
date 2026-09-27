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
    private static final String SNAPSHOT = "TECHCELL_SNAPSHOT_V1";
    private static final String SNAPSHOT_OK = "TECHCELL_SNAPSHOT_OK_V1";
    private static final String SALE = "TECHCELL_SALE_V1";
    private static final String SALE_OK = "TECHCELL_SALE_OK_V1";
    private static final String PRODUCT_DELTA = "TECHCELL_PRODUCT_DELTA_V1";
    private static final String PRODUCT_DELTA_OK = "TECHCELL_PRODUCT_DELTA_OK_V1";
    private static final String ERROR = "TECHCELL_ERROR_V1";

    private volatile boolean ativo;
    private DatagramSocket udp;
    private ServerSocket tcp;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        criarCanal();
        iniciarForeground("Servidor local iniciando…");
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
        manterCpuAtiva();
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
                    new Thread(() -> atender(cliente), "TechCell-Master-Client").start();
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
            cliente.setSoTimeout(30000);
            BufferedReader r = new BufferedReader(new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8));
            String linha = r.readLine();
            if (linha == null) return;

            if (linha.startsWith(PING + "|")) {
                responderPing(w);
                return;
            }

            if (linha.startsWith(SNAPSHOT + "|")) {
                responderSnapshot(linha, w);
                return;
            }

            if (linha.startsWith(SALE + "|")) {
                responderVenda(linha, w);
                return;
            }

            if (linha.startsWith(PRODUCT_DELTA + "|")) {
                responderDeltaProdutos(linha, w);
                return;
            }

            w.write(ERROR + "|COMANDO_INVALIDO");
            w.newLine();
            w.flush();
        } catch (Throwable ignored) {
        } finally {
            try { cliente.close(); } catch (Throwable ignored) {}
        }
    }

    private void responderPing(BufferedWriter w) throws Exception {
        GestaoDbHelper.SyncContext ctx = new GestaoDbHelper(this).getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            w.write(ERROR + "|NAO_MASTER");
            w.newLine();
            w.flush();
            return;
        }

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

    private void responderSnapshot(String linha, BufferedWriter w) throws Exception {
        GestaoDbHelper db = new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            w.write(ERROR + "|NAO_MASTER");
            w.newLine();
            w.flush();
            return;
        }

        String[] p = linha.split("\\|", -1);
        String deviceUuid = p.length >= 2 ? p[1] : "";
        String codigo = p.length >= 3 ? p[2] : "";
        if (!db.validarCodigoPareamento(codigo)) {
            w.write(ERROR + "|CODIGO_INVALIDO");
            w.newLine();
            w.flush();
            return;
        }

        String token = db.autorizarDispositivoLan(deviceUuid);
        String json = db.exportarSnapshotInicial();
        String compactado = TechCellLanClient.compactar(json);
        w.write(SNAPSHOT_OK + "|" + seguro(token) + "|" + compactado);
        w.newLine();
        w.flush();
    }

    private void responderVenda(String linha, BufferedWriter w) throws Exception {
        GestaoDbHelper db = new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            w.write(ERROR + "|NAO_MASTER");
            w.newLine();
            w.flush();
            return;
        }

        String[] p = linha.split("\\|", 4);
        if (p.length < 4) {
            w.write(ERROR + "|PACOTE_INCOMPLETO");
            w.newLine();
            w.flush();
            return;
        }

        String deviceUuid = p[1] == null ? "" : p[1].trim();
        String credencial = p[2] == null ? "" : p[2].trim();
        String token = "";

        if (credencial.startsWith("T:")) {
            token = credencial.substring(2);
            if (!db.validarTokenLan(deviceUuid, token)) {
                w.write(ERROR + "|NAO_AUTORIZADO");
                w.newLine();
                w.flush();
                return;
            }
        } else if (credencial.startsWith("C:")) {
            String codigo = credencial.substring(2);
            if (!db.validarCodigoPareamento(codigo)) {
                w.write(ERROR + "|CODIGO_INVALIDO");
                w.newLine();
                w.flush();
                return;
            }
            token = db.autorizarDispositivoLan(deviceUuid);
        } else {
            w.write(ERROR + "|NAO_AUTORIZADO");
            w.newLine();
            w.flush();
            return;
        }

        String json = TechCellLanClient.descompactar(p[3]);

        GestaoDbHelper.RecebimentoVenda recebida = db.receberVendaDoTerminal(json);
        w.write(SALE_OK + "|" + seguro(recebida.vendaUuid) + "|" + recebida.vendaIdMaster + "|" +
                (recebida.jaExistia ? "EXISTE" : "NOVA") + "|" + seguro(token));
        w.newLine();
        w.flush();
    }

    private void responderDeltaProdutos(String linha, BufferedWriter w) throws Exception {
        GestaoDbHelper db = new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            w.write(ERROR + "|NAO_MASTER");
            w.newLine();
            w.flush();
            return;
        }

        String[] p = linha.split("\\|", -1);
        if (p.length < 4) {
            w.write(ERROR + "|PACOTE_INCOMPLETO");
            w.newLine();
            w.flush();
            return;
        }

        String deviceUuid = p[1] == null ? "" : p[1].trim();
        String token = p[2] == null ? "" : p[2].trim();
        if (!db.validarTokenLan(deviceUuid, token)) {
            w.write(ERROR + "|NAO_AUTORIZADO");
            w.newLine();
            w.flush();
            return;
        }

        long afterSeq = 0;
        try { afterSeq = Math.max(0, Long.parseLong(p[3])); } catch (Throwable ignored) {}

        String json = db.exportarDeltaProdutos(afterSeq, 500);
        w.write(PRODUCT_DELTA_OK + "|" + TechCellLanClient.compactar(json));
        w.newLine();
        w.flush();
    }

    private String seguro(String s) {
        return s == null ? "" : s.replace("|", "").replace("\n", "").replace("\r", "");
    }

    private void manterCpuAtiva() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TechCell:MasterLan");
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
                        CHANNEL_ID, "Servidor local Tech Cell", NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Mantém o Master disponível para os caixas na rede local.");
                nm.createNotificationChannel(c);
            }
        }
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
        liberarCpu();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
