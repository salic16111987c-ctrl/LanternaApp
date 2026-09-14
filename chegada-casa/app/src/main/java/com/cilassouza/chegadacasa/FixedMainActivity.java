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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class FixedMainActivity extends MainActivity {
    private static final String BACKEND_BASE = "https://chegada-casa-api.vercel.app";
    private static final String PREF_OAUTH_STATE = "ewelink_oauth_state";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(() -> {
            wireOAuthButton();
            handleOAuthIntent(getIntent());
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthIntent(intent);
    }

    private void wireOAuthButton() {
        Button button = findButton(getWindow().getDecorView(), "CONECTAR AO EWELINK");
        if (button != null) button.setOnClickListener(v -> connectEwelink());
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

        String state = UUID.randomUUID().toString();
        getSharedPreferences("config", MODE_PRIVATE)
                .edit()
                .putString(PREF_OAUTH_STATE, state)
                .apply();

        Uri loginUrl = Uri.parse(BACKEND_BASE + "/api/oauth-start")
                .buildUpon()
                .appendQueryParameter("open", "1")
                .appendQueryParameter("state", state)
                .appendQueryParameter("t", String.valueOf(System.currentTimeMillis()))
                .build();

        Toast.makeText(this,
                "Abrindo o login oficial do eWeLink. Após autorizar, o Chegada Casa abrirá sozinho.",
                Toast.LENGTH_LONG).show();

        startActivity(new Intent(Intent.ACTION_VIEW, loginUrl));
    }

    private void handleOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"chegadacasa".equalsIgnoreCase(data.getScheme()) ||
                !"oauth".equalsIgnoreCase(data.getHost())) return;

        String oauthError = data.getQueryParameter("error");
        if (oauthError != null && !oauthError.isEmpty()) {
            showError("O eWeLink recusou a autorização: " + oauthError);
            return;
        }

        String code = data.getQueryParameter("code");
        String region = data.getQueryParameter("region");
        if (region == null || region.isEmpty()) region = data.getQueryParameter("regin");
        String returnedState = data.getQueryParameter("state");
        String expectedState = getSharedPreferences("config", MODE_PRIVATE)
                .getString(PREF_OAUTH_STATE, "");

        if (returnedState == null || expectedState == null || expectedState.isEmpty() ||
                !expectedState.equals(returnedState)) {
            showError("Falha na validação de segurança do login eWeLink. Toque em CONECTAR AO EWELINK e tente novamente.");
            return;
        }

        if (code == null || code.trim().isEmpty()) {
            showError("O eWeLink não devolveu o código de autorização.");
            return;
        }

        getSharedPreferences("config", MODE_PRIVATE)
                .edit()
                .remove(PREF_OAUTH_STATE)
                .apply();

        exchangeCodeForToken(code.trim(), region == null ? "" : region.trim());
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
}
