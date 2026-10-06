package com.cilassouza.chegadacasa;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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

/** Compact daily-use dashboard. Detailed setup and diagnostics live on separate screens. */
public class CompactHomeActivity extends Activity {
    private static final int NAVY = Color.rgb(12, 32, 59);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int TEXT = Color.rgb(24, 35, 52);
    private static final int MUTED = Color.rgb(104, 116, 135);
    private static final int BACKGROUND = Color.rgb(245, 247, 251);
    private static final int BORDER = Color.rgb(229, 233, 240);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int RED = Color.rgb(190, 42, 42);
    private static final int AMBER = Color.rgb(180, 105, 0);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView distanceValue;
    private TextView distanceCaption;
    private TextView automationValue;
    private TextView gpsValue;
    private TextView ewelinkValue;
    private TextView gateValue;
    private TextView lastEvent;
    private boolean visible;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            refreshDashboard();
            handler.postDelayed(this, 3000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        buildUi();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Chegada Casa", 22, TEXT, true);
        TextView version = pill("v2.10", Color.rgb(236, 242, 255), BLUE);
        brand.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        brand.addView(version);
        content.addView(brand);

        TextView subtitle = text("Painel de chegada", 12, MUTED, false);
        subtitle.setPadding(0, dp(1), 0, dp(14));
        content.addView(subtitle);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(12, 32, 59), Color.rgb(27, 72, 135)});
        gradient.setCornerRadius(dp(24));
        hero.setBackground(gradient);
        hero.setElevation(dp(4));

        TextView heroLabel = text("DISTÂNCIA DA RESIDÊNCIA", 10,
                Color.rgb(188, 210, 242), true);
        heroLabel.setLetterSpacing(0.09f);
        hero.addView(heroLabel);
        distanceValue = text("—", 40, Color.WHITE, true);
        distanceValue.setPadding(0, dp(2), 0, 0);
        hero.addView(distanceValue);
        distanceCaption = text("Aguardando leitura do GPS", 12,
                Color.rgb(215, 228, 248), false);
        hero.addView(distanceCaption);

        LinearLayout heroActions = new LinearLayout(this);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setPadding(0, dp(14), 0, 0);
        Button updateGps = compactButton("Atualizar GPS", true);
        updateGps.setOnClickListener(v -> probeGps());
        Button audio = compactButton("Testar áudio", false);
        audio.setOnClickListener(v -> SpeechEngine.speak(this,
                "Teste de áudio do Chegada Casa. O aviso de chegada está funcionando."));
        addHalf(heroActions, updateGps, true);
        addHalf(heroActions, audio, false);
        hero.addView(heroActions);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, 0, 0, dp(14));
        content.addView(hero, hp);

        TextView statusTitle = text("Status agora", 15, TEXT, true);
        statusTitle.setPadding(dp(2), 0, 0, dp(8));
        content.addView(statusTitle);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        automationValue = statusCard(row1, "AUTOMAÇÃO", "Verificando", true);
        gpsValue = statusCard(row1, "GPS", "Verificando", false);
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        ewelinkValue = statusCard(row2, "EWELINK", "Verificando", true);
        gateValue = statusCard(row2, "PORTÃO", "Verificando", false);
        LinearLayout.LayoutParams row2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2p.setMargins(0, dp(8), 0, 0);
        content.addView(row2, row2p);

        LinearLayout eventCard = new LinearLayout(this);
        eventCard.setOrientation(LinearLayout.VERTICAL);
        eventCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        eventCard.setBackground(rounded(Color.WHITE, BORDER, 16, 1));
        TextView eventLabel = text("ÚLTIMO EVENTO", 9, MUTED, true);
        eventLabel.setLetterSpacing(0.08f);
        eventCard.addView(eventLabel);
        lastEvent = text("Nenhum evento registrado", 12, TEXT, false);
        lastEvent.setMaxLines(2);
        lastEvent.setPadding(0, dp(3), 0, 0);
        eventCard.addView(lastEvent);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, dp(12), 0, 0);
        content.addView(eventCard, ep);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(9));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(10));

        Button home = navButton("Início", true);
        Button setup = navButton("Configurar", false);
        Button diagnostic = navButton("Diagnóstico", false);
        setup.setOnClickListener(v -> startActivity(new Intent(this, FixedMainActivity.class)));
        diagnostic.setOnClickListener(v -> startActivity(new Intent(this, ArrivalDiagnosticActivity.class)));
        nav.addView(home, navParams());
        nav.addView(setup, navParams());
        nav.addView(diagnostic, navParams());
        page.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        setContentView(page);
    }

    private TextView statusCard(LinearLayout row, String label, String initial, boolean left) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(rounded(Color.WHITE, BORDER, 16, 1));
        TextView l = text(label, 9, MUTED, true);
        l.setLetterSpacing(0.08f);
        card.addView(l);
        TextView value = text(initial, 14, TEXT, true);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(value);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                dp(76), 1f);
        if (left) p.setMargins(0, 0, dp(4), 0);
        else p.setMargins(dp(4), 0, 0, 0);
        row.addView(card, p);
        return value;
    }

    private void refreshDashboard() {
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        int distance = p.getInt("monitor_distance", -1);
        int accuracy = p.getInt("monitor_accuracy", -1);
        long fix = p.getLong("monitor_fix_at", 0L);
        long age = fix > 0L ? Math.max(0L, (System.currentTimeMillis() - fix) / 1000L) : -1L;

        if (distance >= 0) distanceValue.setText(distance < 1000 ? distance + " m" :
                String.format(java.util.Locale.US, "%.1f km", distance / 1000f));
        else distanceValue.setText("—");

        if (fix <= 0L) distanceCaption.setText("Aguardando primeira leitura do GPS");
        else distanceCaption.setText("Precisão ±" + accuracy + " m  •  atualizado há " + age + " s");

        boolean active = p.getBoolean("ativa", false);
        setValue(automationValue, active ? "Ativa" : "Pausada", active ? GREEN : AMBER);

        boolean gpsFresh = fix > 0L && age <= 45L;
        setValue(gpsValue, gpsFresh ? "Online" : (fix > 0L ? "Sem leitura" : "Aguardando"),
                gpsFresh ? GREEN : AMBER);

        boolean ew = EwelinkApi.hasSession(this);
        setValue(ewelinkValue, ew ? "Conectado" : "Desconectado", ew ? GREEN : RED);

        boolean gate = EwelinkApi.hasGate(this);
        setValue(gateValue, gate ? "Configurado" : "Não configurado", gate ? GREEN : AMBER);

        String event = p.getString("arrival_last_event", "nenhum");
        if (event == null || event.trim().isEmpty() || "nenhum".equalsIgnoreCase(event.trim())) {
            event = p.getString("monitor_state", "Nenhum evento registrado");
        }
        lastEvent.setText(event == null ? "Nenhum evento registrado" : event);
    }

    private void probeGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Libere a localização precisa nas configurações do aplicativo.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        distanceCaption.setText("Atualizando GPS...");
        SharedPreferences p = getSharedPreferences("config", MODE_PRIVATE);
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc == null) {
                            distanceCaption.setText("GPS não devolveu posição");
                            return;
                        }
                        int distance = -1;
                        if (p.getBoolean("casa_definida", false)) {
                            float[] out = new float[1];
                            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                                    Double.longBitsToDouble(p.getLong("lat", 0L)),
                                    Double.longBitsToDouble(p.getLong("lon", 0L)), out);
                            distance = Math.round(out[0]);
                        }
                        p.edit()
                                .putInt("monitor_distance", distance)
                                .putInt("monitor_accuracy", Math.round(loc.getAccuracy()))
                                .putLong("monitor_fix_at", System.currentTimeMillis())
                                .putBoolean("monitor_mock", loc.isFromMockProvider())
                                .putString("probe_gps", "GPS " + (loc.isFromMockProvider() ? "FICTÍCIO" : "OK")
                                        + " — " + distance + " m da casa; precisão ±"
                                        + Math.round(loc.getAccuracy()) + " m. Lâmpadas não acionadas.")
                                .apply();
                        refreshDashboard();
                    })
                    .addOnFailureListener(e -> distanceCaption.setText("Falha ao atualizar o GPS"));
        } catch (RuntimeException e) {
            distanceCaption.setText("Falha ao atualizar o GPS");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(refresher);
        refresher.run();
        if (getSharedPreferences("config", MODE_PRIVATE).getBoolean("ativa", false)) {
            ArrivalMonitorService.start(this);
        }
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String value, int background, int foreground) {
        TextView t = text(value, 10, foreground, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(rounded(background, background, 16, 0));
        return t;
    }

    private Button compactButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setGravity(Gravity.CENTER);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ripple(Color.argb(42, 255, 255, 255),
                    Color.argb(80, 255, 255, 255), 13, Color.argb(45, 255, 255, 255)));
        } else {
            b.setTextColor(Color.rgb(221, 231, 248));
            b.setBackground(ripple(Color.argb(22, 255, 255, 255),
                    Color.argb(55, 255, 255, 255), 13, Color.argb(35, 255, 255, 255)));
        }
        return b;
    }

    private void addHalf(LinearLayout parent, Button b, boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (left) p.setMargins(0, 0, dp(4), 0);
        else p.setMargins(dp(4), 0, 0, 0);
        parent.addView(b, p);
    }

    private Button navButton(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setTextColor(selected ? BLUE : MUTED);
        b.setBackground(rounded(selected ? Color.rgb(238, 243, 255) : Color.TRANSPARENT,
                selected ? Color.rgb(218, 228, 252) : Color.TRANSPARENT, 14, selected ? 1 : 0));
        return b;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private void setValue(TextView view, String value, int color) {
        view.setText(value);
        view.setTextColor(color);
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private RippleDrawable ripple(int fill, int stroke, int radius, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(fill, stroke, radius, 1), null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
