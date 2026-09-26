package com.techcell.caixadaloja;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TechCellLanClient {
    public static final int DISCOVERY_PORT = 8766;
    private static final String DISCOVER = "TECHCELL_DISCOVER_V1";
    private static final String MASTER = "TECHCELL_MASTER_V1";
    private static final String PING = "TECHCELL_PING_V1";
    private static final String PONG = "TECHCELL_PONG_V1";
    private static final String SNAPSHOT = "TECHCELL_SNAPSHOT_V1";
    private static final String SNAPSHOT_OK = "TECHCELL_SNAPSHOT_OK_V1";
    private static final String ERROR = "TECHCELL_ERROR_V1";

    public static class MasterInfo {
        public String host = "";
        public int port = 8765;
        public String nome = "";
        public String empresaUuid = "";
        public String filialUuid = "";
        public String dispositivoUuid = "";
    }

    private TechCellLanClient(){}

    public static MasterInfo descobrirPrimeiro(int timeoutMs) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setBroadcast(true);
            socket.setSoTimeout(500);
            byte[] dados = DISCOVER.getBytes(StandardCharsets.UTF_8);

            Set<InetAddress> destinos = new LinkedHashSet<>();
            destinos.add(InetAddress.getByName("255.255.255.255"));

            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress b = ia.getBroadcast();
                        if (b != null) destinos.add(b);
                    }
                } catch (Throwable ignored) {}
            }

            for (InetAddress destino : destinos) {
                try {
                    socket.send(new DatagramPacket(dados, dados.length, destino, DISCOVERY_PORT));
                } catch (Throwable ignored) {}
            }

            long fim = System.currentTimeMillis() + Math.max(1200, timeoutMs);
            byte[] buffer = new byte[2048];
            while (System.currentTimeMillis() < fim) {
                try {
                    DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
                    socket.receive(resposta);
                    String linha = new String(resposta.getData(), resposta.getOffset(), resposta.getLength(), StandardCharsets.UTF_8);
                    MasterInfo info = parseMaster(linha);
                    if (info != null) {
                        info.host = resposta.getAddress().getHostAddress();
                        return info;
                    }
                } catch (SocketTimeoutException timeout) {
                    // continua até o prazo total
                }
            }
            return null;
        } finally {
            socket.close();
        }
    }

    public static MasterInfo testarConexao(String host, int port, String dispositivoUuid) throws Exception {
        Socket socket = new Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(3500);

            BufferedWriter w = writer(socket);
            BufferedReader r = reader(socket);
            w.write(PING + "|" + limpar(dispositivoUuid));
            w.newLine();
            w.flush();

            String linha = r.readLine();
            if (linha == null || !linha.startsWith(PONG + "|")) {
                throw new IllegalStateException("Resposta inválida do Master.");
            }

            String[] p = linha.split("\\|", -1);
            if (p.length < 6) throw new IllegalStateException("Resposta incompleta do Master.");
            MasterInfo info = new MasterInfo();
            info.host = host;
            info.port = parsePort(p[5]);
            info.nome = decodeNome(p[1]);
            info.empresaUuid = p[2];
            info.filialUuid = p[3];
            info.dispositivoUuid = p[4];
            return info;
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    public static String baixarSnapshot(String host, int port, String dispositivoUuid, String codigoPareamento) throws Exception {
        Socket socket = new Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), 2500);
            socket.setSoTimeout(30000);

            BufferedWriter w = writer(socket);
            BufferedReader r = reader(socket);
            w.write(SNAPSHOT + "|" + limpar(dispositivoUuid) + "|" + limpar(codigoPareamento));
            w.newLine();
            w.flush();

            String linha = r.readLine();
            if (linha == null) throw new IllegalStateException("O Master encerrou a conexão sem enviar dados.");
            if (linha.startsWith(ERROR + "|")) {
                String erro = linha.substring((ERROR + "|").length());
                if ("CODIGO_INVALIDO".equals(erro)) throw new SecurityException("Código de pareamento incorreto.");
                if ("NAO_MASTER".equals(erro)) throw new IllegalStateException("O aparelho remoto não está configurado como Master.");
                throw new IllegalStateException("Master recusou a sincronização: " + erro);
            }
            if (!linha.startsWith(SNAPSHOT_OK + "|")) {
                throw new IllegalStateException("Resposta de sincronização inválida.");
            }

            String payload = linha.substring((SNAPSHOT_OK + "|").length());
            if (payload.isEmpty()) throw new IllegalStateException("O Master enviou um pacote vazio.");
            return descompactar(payload);
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    static String compactar(String texto) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(raw);
        gz.write((texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
        gz.finish();
        gz.close();
        return Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP);
    }

    private static String descompactar(String base64) throws Exception {
        byte[] compressed = Base64.decode(base64, Base64.NO_WRAP);
        GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = gz.read(buffer)) >= 0) {
            if (n > 0) out.write(buffer, 0, n);
        }
        gz.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static BufferedWriter writer(Socket socket) throws Exception {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static MasterInfo parseMaster(String linha) {
        if (linha == null || !linha.startsWith(MASTER + "|")) return null;
        String[] p = linha.split("\\|", -1);
        if (p.length < 6) return null;
        MasterInfo info = new MasterInfo();
        info.nome = decodeNome(p[1]);
        info.empresaUuid = p[2];
        info.filialUuid = p[3];
        info.dispositivoUuid = p[4];
        info.port = parsePort(p[5]);
        if (info.empresaUuid.isEmpty() || info.filialUuid.isEmpty() || info.dispositivoUuid.isEmpty()) return null;
        return info;
    }

    static String encodeNome(String nome) {
        String x = nome == null ? "" : nome;
        return Base64.encodeToString(x.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    private static String decodeNome(String valor) {
        try {
            return new String(Base64.decode(valor, Base64.NO_WRAP | Base64.URL_SAFE), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }

    private static int parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            return p > 0 && p <= 65535 ? p : 8765;
        } catch (Throwable e) {
            return 8765;
        }
    }

    private static String limpar(String s) {
        return s == null ? "" : s.replace("|", "").replace("\n", "").replace("\r", "");
    }
}
