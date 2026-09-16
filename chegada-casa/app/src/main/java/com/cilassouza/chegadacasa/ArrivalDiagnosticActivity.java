package com.cilassouza.chegadacasa;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Tela do app rapido: nao oculta falhas de GPS, servico ou configuracao eWeLink. */
public class ArrivalDiagnosticActivity extends FixedMainActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView diagnostics;
    private boolean visible;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateDiagnostics();
            handler.postDelayed(this, 10000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0 ||
                !(content.getChildAt(0) instanceof ScrollView)) return;
        ScrollView scroll = (ScrollView) content.getChildAt(0);
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("DIAGNÓSTICO DA CHEGADA — v1.7");
        title.setTextSize(19);
        title.setTextColor(Color.rgb(25, 60, 110));
        title.setPadding(0, 10, 0, 8);
        root.addView(title, 0);

        diagnostics = new TextView(this);
        diagnostics.setTextColor(Color.BLACK);
        diagnostics.setTextSize(14);
        diagnostics.setPadding(12, 12, 12, 12);
        diagnostics.setBackgroundColor(Color.rgb(225, 239, 250));
        root.addView(diagnostics, 1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button refreshButton = new Button(this);
        refreshButton.setText("ATUALIZAR DIAGNÓSTICO");
        refreshButton.setOnClickListener(v -> updateDiagnostics());
        root.addView(refreshButton, 2);

        Button gpsButton = new Button(this);
        gpsButton.setText("TESTAR GPS AGORA (SEM ACENDER LUZES)");
        gpsButton.setOnClickListener(v -> testGps());
        root.addView(gpsButton, 3);

        Button startButton = new Button(this);
        startButton.setText("REINICIAR MONITOR DE CHEGADA");
        startButton.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
            if (!p.getBoolean("ativa", false)) {
                Toast.makeText(this, "Primeiro configure a casa e toque ATIVAR AUTOMAÇÃO.", Toast.LENGTH_LONG).show();
                return;
            }
            ArrivalMonitorService.start(this);
            Toast.makeText(this, "Solicitei o monitor. Confira o estado abaixo.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this::updateDiagnostics, 1600L);
        });
        root.addView(startButton, 4);
        updateDiagnostics();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private String when(long millis) {
        if (millis <= 0L) return "nenhum registro";
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM,
                new Locale("pt", "BR")).format(new Date(millis));
    }

    private void updateDiagnostics() {
        if (diagnostics == null) return;
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        long fix = p.getLong("monitor_fix_at", 0L);
        long age = fix > 0 ? Math.max(0L, (System.currentTimeMillis() - fix) / 1000L) : -1L;
        int selected = EwelinkApi.selectedCount(this);
        String locationPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ? "precisa" : "NÃO CONCEDIDA";
        String bg = android.os.Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? "liberada" : "NÃO CONCEDIDA";
        String gps = fix == 0 ? "nenhuma leitura" :
                (age > 40 ? "GPS SEM ATUALIZAR há " + age + " s" : "GPS recente: " + age + " s atrás");
        String text = "Automação: " + (p.getBoolean("ativa", false) ? "ATIVA" : "DESATIVADA") +
                " | raio: " + p.getInt("raio", 100) + " m" +
                "\nPermissão: " + locationPermission + " | segundo plano: " + bg +
                "\nMonitor: " + p.getString("monitor_state", "ainda não iniciou") +
                "\nÚltimo GPS: " + gps +
                "\nDistância: " + p.getInt("monitor_distance", -1) + " m" +
                " | precisão: " + p.getInt("monitor_accuracy", -1) + " m" +
                "\nEstado: " + (p.getBoolean("dentro", false) ? "dentro" : "fora") +
                " | saída confirmada: " + (p.getBoolean("outside_observed", false) ? "sim" : "não") +
                "\nSomente à noite: " + (p.getBoolean("so_noite", false) ? "SIM (18h–6h)" : "não") +
                "\neWeLink: " + (EwelinkApi.hasSession(this) ? "conectado" : "NÃO CONECTADO") +
                " | lâmpadas selecionadas: " + selected +
                "\nÚltimo evento: " + p.getString("arrival_last_event", "nenhum") +
                "\nÚltimo comando: " + p.getString("arrival_last_command", "nenhum") +
                "\nErro: " + p.getString("monitor_error", "nenhum") +
                "\nTeste GPS: " + p.getString("probe_gps", "ainda não feito") +
                "\nHorário última posição: " + when(fix);
        diagnostics.setText(text);
    }

    private void testGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Libere Localização precisa para este app.", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        prefs.edit().putString("probe_gps", "Consultando GPS...").apply();
        updateDiagnostics();
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> {
                        String message;
                        if (loc == null) {
                            message = "Falhou: nenhuma posição retornada. Confira localização do celular.";
                        } else {
                            int dist = -1;
                            if (prefs.getBoolean("casa_definida", false)) {
                                float[] out = new float[1];
                                Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                                        Double.longBitsToDouble(prefs.getLong("lat", 0L)),
                                        Double.longBitsToDouble(prefs.getLong("lon", 0L)), out);
                                dist = Math.round(out[0]);
                            }
                            message = "GPS OK — " + dist + " m da casa; precisão " +
                                    Math.round(loc.getAccuracy()) + " m. Sem acionar lâmpadas.";
                        }
                        prefs.edit().putString("probe_gps", message).apply();
                        updateDiagnostics();
                    })
                    .addOnFailureListener(e -> {
                        prefs.edit().putString("probe_gps", "Falha GPS: " + e.getClass().getSimpleName()).apply();
                        updateDiagnostics();
                    });
        } catch (RuntimeException e) {
            prefs.edit().putString("probe_gps", "Falha ao iniciar GPS: " + e.getClass().getSimpleName()).apply();
            updateDiagnostics();
        }
    }
}
