package com.cilassouza.chegadacasa;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Configuração inicial comercial em etapas curtas. */
public class CommercialSetupActivity extends Activity {
    private static final int NAVY = Color.rgb(10, 29, 55);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int AMBER = Color.rgb(180, 105, 0);
    private static final int TEXT = Color.rgb(24, 35, 52);
    private static final int MUTED = Color.rgb(104, 116, 135);
    private static final int BACKGROUND = Color.rgb(246, 248, 252);
    private static final int BORDER = Color.rgb(229, 233, 240);

    private LinearLayout steps;
    private TextView progress;
    private Button finish;

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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView eyebrow = text("CONFIGURAÇÃO INICIAL", 10, BLUE, true);
        eyebrow.setLetterSpacing(0.1f);
        content.addView(eyebrow);

        TextView title = text("Prepare sua chegada", 26, TEXT, true);
        title.setPadding(0, dp(4), 0, dp(3));
        content.addView(title);

        TextView subtitle = text("Configure somente o essencial. O portão é opcional e pode ser adicionado depois.",
                13, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(14));
        content.addView(subtitle);

        progress = text("Verificando configuração...", 12, MUTED, true);
        progress.setPadding(dp(13), dp(10), dp(13), dp(10));
        progress.setBackground(rounded(Color.WHITE, BORDER, 14, 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMargins(0, 0, 0, dp(12));
        content.addView(progress, pp);

        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        content.addView(steps);

        finish = primaryButton("Concluir configuração");
        finish.setOnClickListener(v -> finishSetup());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        fp.setMargins(0, dp(4), 0, dp(8));
        content.addView(finish, fp);

        Button preview = secondaryButton("Ver painel agora");
        preview.setOnClickListener(v -> {
            Intent i = new Intent(this, CommercialHomeActivity.class);
            i.putExtra("skip_setup_once", true);
            startActivity(i);
            finish();
        });
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        setContentView(page);
        refreshSteps();
    }

    private void refreshSteps() {
        if (steps == null) return;
        steps.removeAllViews();

        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean home = p.getBoolean("casa_definida", false);
        boolean ew = EwelinkApi.hasSession(this);
        boolean lights = EwelinkApi.selectedCount(this) > 0;
        boolean automation = p.getBoolean("ativa", false);
        boolean gate = EwelinkApi.hasGate(this);

        int done = (home ? 1 : 0) + (ew && lights ? 1 : 0) + (automation ? 1 : 0);
        progress.setText(done + " de 3 etapas essenciais concluídas");
        progress.setTextColor(done == 3 ? GREEN : MUTED);

        addStep("1", "Residência", home ? "Configurada" : "Defina o endereço ou ponto no mapa",
                home, "Configurar residência");
        addStep("2", "eWeLink", ew && lights
                        ? EwelinkApi.selectedCount(this) + " dispositivo(s) selecionado(s)"
                        : ew ? "Conta conectada • escolha as luzes" : "Conecte a conta e escolha as luzes",
                ew && lights, "Configurar eWeLink");
        addStep("3", "Automação", automation ? "Ativa" : "Ative o monitoramento de chegada",
                automation, "Configurar automação");
        addStep("+", "Portão", gate ? "Configurado com confirmação" : "Opcional • configure somente se desejar",
                gate, gate ? "Revisar portão" : "Adicionar portão");

        finish.setEnabled(done == 3);
        finish.setAlpha(done == 3 ? 1f : 0.45f);
    }

    private void addStep(String number, String title, String status, boolean ok, String buttonLabel) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(Color.WHITE, BORDER, 18, 1));
        card.setElevation(dp(1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(number, 12, ok ? Color.WHITE : BLUE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(ok ? GREEN : Color.rgb(238, 243, 255),
                ok ? GREEN : Color.rgb(218, 228, 252), 18, 1));
        head.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 16, TEXT, true);
        TextView s = text(status, 11, ok ? GREEN : MUTED, false);
        copy.addView(t);
        copy.addView(s);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(11), 0, 0, 0);
        head.addView(copy, cp);
        card.addView(head);

        Button button = secondaryButton(buttonLabel);
        button.setOnClickListener(v -> startActivity(new Intent(this, FixedMainActivity.class)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bp.setMargins(0, dp(11), 0, 0);
        card.addView(button, bp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(10));
        steps.addView(card, p);
    }

    private void finishSetup() {
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean ready = p.getBoolean("casa_definida", false)
                && EwelinkApi.hasSession(this)
                && EwelinkApi.selectedCount(this) > 0
                && p.getBoolean("ativa", false);
        if (!ready) {
            Toast.makeText(this, "Conclua as três etapas essenciais antes de finalizar.",
                    Toast.LENGTH_LONG).show();
            refreshSteps();
            return;
        }
        p.edit().putBoolean("commercial_onboarding_done", true).apply();
        startActivity(new Intent(this, CommercialHomeActivity.class));
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        if (steps != null) refreshSteps();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = baseButton(label);
        b.setTextColor(Color.WHITE);
        b.setBackground(ripple(BLUE, BLUE, 14, Color.argb(35, 255, 255, 255)));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = baseButton(label);
        b.setTextColor(Color.rgb(48, 71, 111));
        b.setBackground(ripple(Color.rgb(248, 250, 253), Color.rgb(215, 222, 233),
                14, Color.argb(28, 37, 99, 235)));
        return b;
    }

    private Button baseButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
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
