package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/** Live diagnostics, safely isolated manual simulation and user-controlled voice volume. */
public class ArrivalDiagnosticActivity extends FixedMainActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView diagnostics;
    private boolean visible;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateDiagnostics();
            handler.postDelayed(this, 3000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0
                || !(content.getChildAt(0) instanceof ScrollView)) return;
        ScrollView scroll = (ScrollView) content.getChildAt(0);
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("DIAGNÓSTICO E VOZ — v2.6");
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

        Button volumeButton = new Button(this);
        volumeButton.setText("🔊 AUMENTAR VOLUME DO AVISO (MÍDIA)");
        volumeButton.setOnClickListener(v -> {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null) {
                try {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                } catch (SecurityException e) {
                    Toast.makeText(this, "Ajuste o volume de mídia nas configurações do celular.",
                            Toast.LENGTH_LONG).show();
                }
            }
            Toast.makeText(this, "Volume de mídia: " + SpeechEngine.mediaVolumePercent(this)
                    + "%. Toque novamente para aumentar.", Toast.LENGTH_SHORT).show();
            updateDiagnostics();
        });
        root.addView(volumeButton, 4);

        Button speechButton = new Button(this);
        speechButton.setText("TESTAR ÁUDIO AGORA (SEM ACENDER LUZES)");
        speechButton.setOnClickListener(v -> {
            SpeechEngine.speak(this, "Teste de áudio do Chegada Casa. O aviso de chegada está funcionando.");
            handler.postDelayed(this::updateDiagnostics, 1500L);
        });
        root.addView(speechButton, 5);

        Button simulateButton = new Button(this);
        simulateButton.setText("SIMULAR CHEGADA E ACENDER LUZES (TESTE REAL)");
        simulateButton.setOnClickListener(v -> confirmSimulation(simulateButton));
        root.addView(simulateButton, 6);

        Button gateTestButton = new Button(this);
        gateTestButton.setText("🧪 ARMAR TESTE DO PORTÃO COM FAKE GPS (10 MIN / 1 VEZ)");
        gateTestButton.setOnClickListener(v -> armFakeGpsGateTest());
        root.addView(gateTestButton, 7);

        Button cancelGateTestButton = new Button(this);
        cancelGateTestButton.setText("CANCELAR TESTE DO PORTÃO");
        cancelGateTestButton.setOnClickListener(v -> {
            GateTestMode.cancel(this);
            updateDiagnostics();
            Toast.makeText(this, "Teste do portão cancelado.", Toast.LENGTH_LONG).show();
        });
        root.addView(cancelGateTestButton, 8);

        Button startButton = new Button(this);
        startButton.setText("REINICIAR MONITOR DE CHEGADA");
        startButton.setOnClickListener(v -> {
            SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
            if (!p.getBoolean("ativa", false)) {
                Toast.makeText(this, "Configure a casa e toque ATIVAR AUTOMAÇÃO primeiro.", Toast.LENGTH_LONG).show();
                return;
            }
            ArrivalMonitorService.start(this);
            Toast.makeText(this, "Monitor solicitado. Confira o estado do GPS abaixo.", Toast.LENGTH_LONG).show();
            handler.postDelayed(this::updateDiagnostics, 1600L);
        });
        root.addView(startButton, 9);
        updateDiagnostics();
    }

    private void armFakeGpsGateTest() {
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        KeyguardManager lock = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (lock != null && lock.isDeviceLocked()) {
            Toast.makeText(this, "Desbloqueie o celular antes de armar o teste.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!p.getBoolean("ativa", false) || !EwelinkApi.hasSession(this)
                || !EwelinkApi.hasGate(this)) {
            new AlertDialog.Builder(this).setTitle("Teste não disponível")
                    .setMessage("Ative a automação e configure a conta eWeLink e o portão primeiro.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("TESTE REAL DO PORTÃO COM GPS FICTÍCIO")
                .setMessage("ATENÇÃO: ao simular a rota de saída e retorno, o app mostrará a pergunta SIM/NÃO. "
                        + "Se você confirmar SIM, o portão FÍSICO poderá abrir de verdade! "
                        + "Arme apenas estando diante do portão, com a passagem desimpedida, "
                        + "observando o equipamento e sem crianças, pessoas ou veículos na trajetória. "
                        + "Este modo autoriza uma ÚNICA chegada simulada durante 10 minutos. "
                        + "Sem o seu SIM, nenhum comando será enviado. Confirmar que está no local e armar?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ESTOU NO LOCAL — ARMAR TESTE", (dialog, which) -> {
                    GateTestMode.arm(this);
                    updateDiagnostics();
                    Toast.makeText(this, "TESTE ARMADO por 10 minutos e 1 chegada. SIM abre o portão REAL!",
                            Toast.LENGTH_LONG).show();
                }).show();
    }

    /** Does not fake GPS, reset cooldown, change inside/outside or pulse the gate. */
    private void confirmSimulation(Button button) {
        boolean connected = EwelinkApi.hasSession(this);
        int count = EwelinkApi.selectedCount(this);
        if (!connected || count == 0) {
            String reason = !connected ? "A eWeLink ainda não está conectada neste aplicativo."
                    : "Nenhuma lâmpada está selecionada neste aplicativo.";
            new AlertDialog.Builder(this).setTitle("Teste não disponível")
                    .setMessage(reason + " Configure na seção eWeLink e tente novamente.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Simular chegada de verdade?")
                .setMessage("Este teste vai falar o aviso, gerar uma notificação e ENVIAR AGORA o comando real para "
                        + count + " dispositivo(s) selecionado(s). As lâmpadas podem acender. "
                        + "O portão NÃO será acionado. GPS, raio, estado dentro/fora e próxima chegada NÃO serão alterados. "
                        + "A resposta da API não comprova que a luz acendeu fisicamente. Confirmar?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SIMULAR E ACENDER", (dialog, which) -> {
                    button.setEnabled(false);
                    ArrivalController.simulateArrival(this);
                    updateDiagnostics();
                    Toast.makeText(this, "Simulação iniciada. Confira SIMULAÇÃO no diagnóstico e observe as lâmpadas.",
                            Toast.LENGTH_LONG).show();
                    handler.postDelayed(() -> button.setEnabled(true), 15000L);
                }).show();
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
        long age = fix > 0L ? Math.max(0L, (System.currentTimeMillis() - fix) / 1000L) : -1L;
        String precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ? "OK" : "NEGADA";
        String background = Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? "OK" : "NEGADA";
        String notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ? "OK" : "NEGADA — notificações podem não aparecer";
        String gps = fix == 0L ? "nenhuma leitura" :
                (age > 40L ? "PAROU há " + age + " s" : "recente: " + age + " s atrás");
        String info = "Automação: " + (p.getBoolean("ativa", false) ? "ATIVA" : "DESATIVADA") +
                " | raio: " + p.getInt("raio", 100) + " m" +
                "\nLocalização precisa: " + precise + " | em segundo plano: " + background +
                "\nPermissão notificação: " + notifications +
                "\nPerfil GPS: " + p.getString("monitor_profile", "ainda não definido") +
                " | movimento: " + (System.currentTimeMillis() < p.getLong("motion_until", 0L) ? "detectado" : "parado/indefinido") +
                " | aproximando: " + (p.getBoolean("motion_toward", false) ? "sim" : "não/indefinido") +
                "\nCerca 2,5 km: " + p.getString("outer_geofence_status", "sem evento ainda") +
                "\nMonitor: " + p.getString("monitor_state", "ainda não iniciou") +
                "\nÚltimo GPS: " + gps +
                "\nDistância: " + p.getInt("monitor_distance", -1) + " m" +
                " | precisão: ±" + p.getInt("monitor_accuracy", -1) + " m" +
                "\nGPS fictício: " + (p.getBoolean("monitor_mock", false) ? "SIM" : "não") +
                " | teste portão: " + (GateTestMode.isAuthorizedPending(p) ? "PERGUNTA PENDENTE — SIM ABRE REAL"
                        : GateTestMode.isArmed(p) ? "ARMADO 10 MIN / 1 VEZ" : "DESARMADO") +
                "\nEstado: " + (p.getBoolean("dentro", false) ? "DENTRO" : "FORA") +
                " | saída confirmada: " + (p.getBoolean("outside_observed", false) ? "SIM" : "NÃO") +
                "\nSomente LÂMPADAS 18h–6h: " + (p.getBoolean("so_noite", false) ? "SIM" : "NÃO") +
                "\neWeLink: " + (EwelinkApi.hasSession(this) ? "conectada" : "NÃO CONECTADA") +
                " | lâmpadas selecionadas: " + EwelinkApi.selectedCount(this) +
                " | portão: " + (EwelinkApi.hasGate(this) ? "configurado" : "não configurado") +
                "\nVolume do áudio (MÍDIA): " + SpeechEngine.mediaVolumePercent(this) + "%" +
                " — ajuste no botão abaixo ou nas teclas de volume; Bluetooth pode mudar a saída." +
                "\nPortão por voz: chegada REAL ou teste GPS temporariamente armado; toque RESPONDER POR VOZ na notificação, "
                    + "depois no microfone e diga SIM, ABRIR PORTÃO ou NÃO." +
                "\nStatus portão: " + p.getString("gate_voice_status", "sem confirmação por voz") +
                "\nÚltimo evento: " + p.getString("arrival_last_event", "nenhum") +
                "\nÚltimo comando: " + p.getString("arrival_last_command", "nenhum") +
                "\nSIMULAÇÃO: " + p.getString("simulation_last_result", "nenhuma simulação executada") +
                "\nÁudio: " + p.getString("tts_state", "nenhum") +
                " | horário: " + when(p.getLong("tts_at", 0L)) +
                "\nErro GPS: " + p.getString("monitor_error", "nenhum") +
                "\nTeste GPS: " + p.getString("probe_gps", "ainda não feito") +
                "\nHorário última posição: " + when(fix);
        diagnostics.setText(info);
    }

    private void testGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Libere Localização precisa para este aplicativo.", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences p = getSharedPreferences("config", MODE_PRIVATE);
        p.edit().putString("probe_gps", "Consultando GPS...").apply();
        updateDiagnostics();
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> {
                        String message;
                        if (loc == null) message = "Falhou: GPS não devolveu posição";
                        else {
                            int distance = -1;
                            if (p.getBoolean("casa_definida", false)) {
                                float[] out = new float[1];
                                Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                                        Double.longBitsToDouble(p.getLong("lat", 0L)),
                                        Double.longBitsToDouble(p.getLong("lon", 0L)), out);
                                distance = Math.round(out[0]);
                            }
                            message = "GPS " + (loc.isFromMockProvider() ? "FICTÍCIO" : "OK")
                                    + " — " + distance + " m da casa; precisão ±"
                                    + Math.round(loc.getAccuracy()) + " m. Lâmpadas não acionadas.";
                        }
                        p.edit().putString("probe_gps", message).apply();
                        updateDiagnostics();
                    })
                    .addOnFailureListener(e -> {
                        p.edit().putString("probe_gps", "Falha GPS: " + e.getClass().getSimpleName()).apply();
                        updateDiagnostics();
                    });
        } catch (RuntimeException e) {
            p.edit().putString("probe_gps", "Falha GPS: " + e.getClass().getSimpleName()).apply();
            updateDiagnostics();
        }
    }
}
