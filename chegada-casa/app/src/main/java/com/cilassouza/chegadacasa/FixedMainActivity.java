package com.cilassouza.chegadacasa;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class FixedMainActivity extends MainActivity {
    private static final String BACKEND_BASE = "https://chegada-casa-api.vercel.app";
    private static final int CALLBACK_PORT = 8787;

    private volatile ServerSocket callbackServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(this::wireOAuthButton);
    }

    private void wireOAuthButton() {
        Button button = findButton(getWindow().getDecorView(), "CONECTAR AO EWELINK");
        if (button != null) {
            button.setOnClickListener(v -> connectEwelink());
        }
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button) {
            Button b = (Button) view;
            if (text.contentEquals(b.getText())) return b;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void connectEwelink() {
        if (EwelinkApi.hasSession(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("eWeLink conectado")
                    .setMessage("A conta eWeLink já está autorizada neste aparelho.")
                    .setPositiveButton("OK", null)
                    .setNegativeButton("DESCONECTAR", (dialog, which) -> {
                        EwelinkApi.clearSession(this);
                        recreate();
                    })
                    .show();
            return;
        }

        final String state = UUID.randomUUID().toString();

        try {
            closeCallbackServer();

            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), CALLBACK_PORT));
            server.setSoTimeout(5 * 60 * 1000);
            callbackServer = server;

            new Thread(() -> waitForOAuthCallback(server, state), "ewelink-oauth-callback").start();

            Uri loginUrl = Uri.parse(BACKEND_BASE + "/api/oauth-start")
                    .buildUpon()
                    .appendQueryParameter("open", "1")
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("t", String.valueOf(System.currentTimeMillis()))
                    .build();

            Toast.makeText(this,
                    "Abrindo o login do eWeLink. Depois de autorizar, volte ao Chegada Casa.",
                    Toast.LENGTH_LONG).show();

            startActivity(new Intent(Intent.ACTION_VIEW, loginUrl));
        } catch (Exception e) {
            closeCallbackServer();
            showError("Não consegui iniciar o retorno OAuth na porta 8787: " + safeMessage(e));
        }
    }

    private void waitForOAuthCallback(ServerSocket server, String expectedState) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(15000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String requestLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consome os headers HTTP do navegador.
            }

            if (requestLine == null) {
                writeBrowserResponse(socket, false, "Retorno OAuth vazio.");
                showError("O eWeLink retornou uma resposta vazia.");
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                writeBrowserResponse(socket, false, "Retorno OAuth inválido.");
                showError("O retorno OAuth veio em um formato inválido.");
                return;
            }

            Uri callback = Uri.parse("http://127.0.0.1" + requestParts[1]);
            String path = callback.getPath();
            String code = callback.getQueryParameter("code");
            String region = callback.getQueryParameter("region");
            String returnedState = callback.getQueryParameter("state");
            String oauthError = callback.getQueryParameter("error");

            if (!"/ewelink/callback".equals(path)) {
                writeBrowserResponse(socket, false, "Caminho de retorno inválido.");
                showError("O navegador retornou para um caminho OAuth inesperado.");
                return;
            }

            if (oauthError != null && !oauthError.isEmpty()) {
                writeBrowserResponse(socket, false, "Autorização recusada pelo eWeLink.");
                showError("O eWeLink recusou a autorização: " + oauthError);
                return;
            }

            if (returnedState == null || !expectedState.equals(returnedState)) {
                writeBrowserResponse(socket, false, "Validação de segurança do OAuth falhou.");
                showError("Falha na validação de segurança (state) do OAuth.");
                return;
            }

            if (code == null || code.trim().isEmpty()) {
                writeBrowserResponse(socket, false, "Código OAuth não recebido.");
                showError("O eWeLink não devolveu o código de autorização.");
                return;
            }

            writeBrowserResponse(socket, true,
                    "Autorização recebida. Volte ao aplicativo Chegada Casa.");

            exchangeCodeForToken(code.trim(), region == null ? "" : region.trim());
        } catch (java.net.SocketTimeoutException e) {
            showError("O login eWeLink expirou. Toque em CONECTAR AO EWELINK e tente novamente.");
        } catch (Exception e) {
            showError("Falha ao receber o retorno do eWeLink: " + safeMessage(e));
        } finally {
            closeCallbackServer();
        }
    }

    private void writeBrowserResponse(Socket socket, boolean ok, String message) {
        try {
            String title = ok ? "Chegada Casa — autorizado" : "Chegada Casa — erro";
            String html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<title>" + title + "</title></head>" +
                    "<body style=\"font-family:sans-serif;padding:28px;line-height:1.5\">" +
                    "<h2>" + title + "</h2><p>" + message + "</p>" +
                    "<p>Você já pode fechar esta página.</p></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private void exchangeCodeForToken(String code, String region) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BACKEND_BASE + "/api/oauth-exchange");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(25000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("code", code);
                body.put("region", region);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                String responseText = readConnection(connection);
                JSONObject response = new JSONObject(responseText);

                if (!response.optBoolean("ok", false)) {
                    String message = response.optString("message", "Falha ao obter token do eWeLink.");
                    showError(message);
                    return;
                }

                String accessToken = response.optString("accessToken", "");
                String refreshToken = response.optString("refreshToken", "");
                String finalRegion = response.optString("region", region);
                long expiresIn = response.optLong("expiresIn", 2592000L);

                if (accessToken.isEmpty()) {
                    showError("O backend não recebeu o Access Token do eWeLink.");
                    return;
                }

                long expiresAt = System.currentTimeMillis() + Math.max(3600L, expiresIn) * 1000L;
                EwelinkApi.saveSession(this, accessToken, finalRegion, expiresAt);

                if (!refreshToken.isEmpty()) {
                    getSharedPreferences("config", MODE_PRIVATE)
                            .edit()
                            .putString("ewelink_refresh_token", refreshToken)
                            .apply();
                }

                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("eWeLink conectado")
                        .setMessage("Autorização concluída. Agora toque em ESCOLHER LÂMPADAS / DISPOSITIVOS.")
                        .setPositiveButton("OK", (dialog, which) -> recreate())
                        .show());
            } catch (Exception e) {
                showError("Não consegui concluir a autorização eWeLink: " + safeMessage(e));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "ewelink-token-exchange").start();
    }

    private String readConnection(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (input == null) return "{\"ok\":false,\"message\":\"HTTP " + status + "\"}";

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) text.append(line);
        reader.close();
        return text.toString();
    }

    private void showError(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Falha na conexão eWeLink")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show());
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private synchronized void closeCallbackServer() {
        ServerSocket server = callbackServer;
        callbackServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        closeCallbackServer();
        super.onDestroy();
    }
}
