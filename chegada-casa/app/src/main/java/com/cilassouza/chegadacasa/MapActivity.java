package com.cilassouza.chegadacasa;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

public class MapActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("config", MODE_PRIVATE);

        boolean definida = prefs.getBoolean("casa_definida", false);
        double lat = definida ? Double.longBitsToDouble(prefs.getLong("lat", 0)) : -14.2350;
        double lon = definida ? Double.longBitsToDouble(prefs.getLong("lon", 0)) : -51.9253;
        int zoom = definida ? 18 : 4;

        WebView web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body{height:100%;margin:0;font-family:sans-serif}#map{height:78%}.box{height:22%;box-sizing:border-box;padding:10px;background:white}.msg{font-size:16px;margin-bottom:8px}.btn{width:100%;height:54px;font-size:17px;font-weight:bold}</style></head><body>" +
                "<div id='map'></div><div class='box'><div class='msg'>Toque exatamente sobre sua residência e depois salve.</div><button class='btn' onclick='savePoint()'>SALVAR ESTE PONTO</button></div>" +
                "<script>var lat=" + String.format(Locale.US, "%.8f", lat) + ",lon=" + String.format(Locale.US, "%.8f", lon) + ";" +
                "var map=L.map('map').setView([lat,lon]," + zoom + ");" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);" +
                "var m=L.marker([lat,lon]).addTo(map);" +
                "map.on('click',function(e){lat=e.latlng.lat;lon=e.latlng.lng;m.setLatLng(e.latlng);});" +
                "function savePoint(){Android.saveLocation(lat,lon);}</script></body></html>";

        web.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null);
        setContentView(web);
    }

    private class Bridge {
        @JavascriptInterface
        public void saveLocation(double lat, double lon) {
            prefs.edit()
                    .putLong("lat", Double.doubleToRawLongBits(lat))
                    .putLong("lon", Double.doubleToRawLongBits(lon))
                    .putString("endereco", "Ponto marcado no mapa")
                    .putBoolean("casa_definida", true)
                    .putBoolean("ativa", false)
                    .putBoolean("dentro", false)
                    .apply();
            runOnUiThread(() -> {
                Toast.makeText(MapActivity.this, "Residência salva pelo mapa.", Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }
}
