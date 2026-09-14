package com.cilassouza.chegadacasa;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EwelinkApi {
    public static final String APP_ID = "d4m62Z1qSNOle69A1rJqhUd7al3eA7qg";
    public static final String REDIRECT_URL = "http://127.0.0.1:8787/ewelink/callback";
    private static final String PREFS = "config";
    private static final SecureRandom RANDOM = new SecureRandom();

    public interface TextCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    public interface DevicesCallback {
        void onSuccess(List<Device> devices);
        void onError(String message);
    }

    public static class Device {
        public final String id;
        public final String name;
        public final boolean online;
        public final int channels;

        public Device(String id, String name, boolean online, int channels) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.channels = channels;
        }
    }

    public static boolean hasSession(Context context) {
        String token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("ewelink_access_token", "");
        return token != null && !token.isEmpty();
    }

    public static void saveSession(Context context, String accessToken, String region, long expiresAt) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("ewelink_access_token", accessToken)
                .putString("ewelink_region", normalizeRegion(region))
                .putLong("ewelink_access_exp", expiresAt)
                .apply();
    }

    public static void clearSession(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("ewelink_access_token")
                .remove("ewelink_region")
                .remove("ewelink_access_exp")
                .remove("ewelink_selected")
                .apply();
    }

    public static int selectedCount(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("ewelink_selected", "[]")).length();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void saveSelected(Context context, List<Device> devices, boolean[] checked) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < devices.size(); i++) {
            if (!checked[i]) continue;
            Device d = devices.get(i);
            try {
                JSONObject o = new JSONObject();
                o.put("id", d.id);
                o.put("name", d.name);
                o.put("channels", d.channels);
                out.put(o);
            } catch (Exception ignored) {}
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("ewelink_selected", out.toString()).apply();
    }

    public static void fetchDevices(Context context, DevicesCallback callback) {
        new Thread(() -> {
            try {
                SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String token = p.getString("ewelink_access_token", "");
                String region = p.getString("ewelink_region", "as");
                if (token == null || token.isEmpty()) {
                    callback.onError("A conta eWeLink ainda não foi autorizada.");
                    return;
                }

                HttpURLConnection c = open(baseUrl(region) + "/v2/device/thing?num=0&lang=en", "GET", token);
                JSONObject root = new JSONObject(read(c));
                if (root.optInt("error", -1) != 0) {
                    callback.onError("Falha ao obter dispositivos: " + root.optString("msg", "erro " + root.optInt("error")));
                    return;
                }

                JSONObject data = root.optJSONObject("data");
                JSONArray list = data == null ? null : data.optJSONArray("thingList");
                if (list == null) {
                    callback.onError("O eWeLink não retornou a lista de dispositivos.");
                    return;
                }

                List<Device> result = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    int itemType = item.optInt("itemType", 0);
                    if (itemType != 1 && itemType != 2) continue;
                    JSONObject d = item.optJSONObject("itemData");
                    if (d == null) continue;
                    String id = d.optString("deviceid", "");
                    if (id.isEmpty()) continue;
                    JSONObject params = d.optJSONObject("params");
                    int channels = 0;
                    if (params != null) {
                        JSONArray switches = params.optJSONArray("switches");
                        if (switches != null && switches.length() > 0) channels = switches.length();
                        else if (params.has("switch")) channels = 1;
                    }
                    if (channels > 0) {
                        result.add(new Device(id, d.optString("name", id), d.optBoolean("online", false), channels));
                    }
                }
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onError("Erro ao consultar dispositivos: " + message(e));
            }
        }).start();
    }

    public static void turnOnSelected(Context context, TextCallback callback) {
        new Thread(() -> {
            try {
                SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String token = p.getString("ewelink_access_token", "");
                String region = p.getString("ewelink_region", "as");
                if (token == null || token.isEmpty()) {
                    callback.onError("eWeLink ainda não conectado.");
                    return;
                }

                JSONArray selected = new JSONArray(p.getString("ewelink_selected", "[]"));
                if (selected.length() == 0) {
                    callback.onError("Nenhum dispositivo foi selecionado.");
                    return;
                }

                int ok = 0;
                for (int i = 0; i < selected.length(); i++) {
                    JSONObject d = selected.optJSONObject(i);
                    if (d == null) continue;
                    String id = d.optString("id", "");
                    int channels = Math.max(1, d.optInt("channels", 1));

                    JSONObject params = new JSONObject();
                    if (channels == 1) {
                        params.put("switch", "on");
                    } else {
                        JSONArray switches = new JSONArray();
                        for (int ch = 0; ch < channels; ch++) {
                            JSONObject sw = new JSONObject();
                            sw.put("switch", "on");
                            sw.put("outlet", ch);
                            switches.put(sw);
                        }
                        params.put("switches", switches);
                    }

                    JSONObject body = new JSONObject();
                    body.put("type", 1);
                    body.put("id", id);
                    body.put("params", params);

                    HttpURLConnection c = open(baseUrl(region) + "/v2/device/thing/status", "POST", token);
                    c.setDoOutput(true);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    JSONObject response = new JSONObject(read(c));
                    if (response.optInt("error", -1) == 0) ok++;
                    if (i < selected.length() - 1) Thread.sleep(1100L);
                }

                if (ok > 0) callback.onSuccess(ok + " dispositivo(s) acionado(s) pelo eWeLink.");
                else callback.onError("O eWeLink não confirmou o acionamento dos dispositivos.");
            } catch (Exception e) {
                callback.onError("Erro ao acionar eWeLink: " + message(e));
            }
        }).start();
    }

    private static HttpURLConnection open(String url, String method, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("X-CK-Appid", APP_ID);
        c.setRequestProperty("X-CK-Nonce", randomString(8));
        c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream input = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        if (input == null) return "{\"error\":" + code + ",\"msg\":\"HTTP " + code + "\"}";
        BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static String baseUrl(String region) {
        switch (normalizeRegion(region)) {
            case "cn": return "https://cn-apia.coolkit.cn";
            case "us": return "https://us-apia.coolkit.cc";
            case "eu": return "https://eu-apia.coolkit.cc";
            default: return "https://as-apia.coolkit.cc";
        }
    }

    private static String normalizeRegion(String region) {
        if (region == null) return "as";
        String r = region.toLowerCase(Locale.US);
        if (r.startsWith("cn")) return "cn";
        if (r.startsWith("us")) return "us";
        if (r.startsWith("eu")) return "eu";
        return "as";
    }

    private static String randomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < len; i++) b.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return b.toString();
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
