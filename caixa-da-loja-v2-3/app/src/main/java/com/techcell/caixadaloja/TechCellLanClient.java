package com.techcell.caixadaloja;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

public final class TechCellLanClient {
    public static final int DISCOVERY_PORT = 8766;
    private static final String DISCOVER = "TECHCELL_DISCOVER_V1";
    private static final String MASTER = "TECHCELL_MASTER_V1";
    private static final String PING = "TECHCELL_PING_V1";
    private static final String PONG = "TECHCELL_PONG_V1";

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
            socket.connect(new java.net.InetSocketAddress(host, port), 1800);
            socket.setSoTimeout(2200);

            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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
