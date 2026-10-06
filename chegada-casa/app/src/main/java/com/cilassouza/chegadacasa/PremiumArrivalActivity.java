package com.cilassouza.chegadacasa;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Visual premium aplicado por cima da tela funcional existente.
 * A lógica de GPS, eWeLink, diagnóstico e portão permanece nas classes originais.
 */
public class PremiumArrivalActivity extends ArrivalDiagnosticActivity {
    private static final int NAVY = Color.rgb(12, 32, 59);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int BLUE_DARK = Color.rgb(29, 78, 216);
    private static final int TEXT = Color.rgb(25, 36, 53);
    private static final int MUTED = Color.rgb(103, 116, 137);
    private static final int SURFACE = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(245, 247, 251);
    private static final int BORDER = Color.rgb(229, 233, 240);
    private static final int SOFT_BLUE = Color.rgb(239, 244, 255);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int SOFT_GREEN = Color.rgb(236, 253, 243);
    private static final int AMBER = Color.rgb(180, 105, 0);
    private static final int SOFT_AMBER = Color.rgb(255, 248, 229);
    private static final int RED = Color.rgb(190, 42, 42);
    private static final int SOFT_RED = Color.rgb(255, 241, 241);

    private TextView heroStatus;
    private TextView heroGps;
    private TextView heroEwelink;
    private TextView heroGate;
    private boolean premiumReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.rgb(248, 249, 252));
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        applyPremiumLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (premiumReady) refreshHero();
    }

    private void applyPremiumLayout() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0 || !(content.getChildAt(0) instanceof ScrollView)) return;
        ScrollView scroll = (ScrollView) content.getChildAt(0);
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);

        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(16), dp(12), dp(16), dp(34));
        scroll.setClipToPadding(false);

        styleTechnicalHeader(root);
        styleDiagnosticTools(root);
        hideLegacyHeader(root);

        int residentialIndex = findDirectCardIndex(root, "Residência");
        if (residentialIndex >= 0) {
            root.addView(createHero(), residentialIndex);
        }

        styleConfigurationCards(root);
        styleFooter(root);
        premiumReady = true;
        refreshHero();
    }

    private void styleTechnicalHeader(LinearLayout root) {
        TextView technicalTitle = findText(root, "DIAGNÓSTICO E VOZ");
        if (technicalTitle != null) {
            technicalTitle.setTextSize(12);
            technicalTitle.setAllCaps(true);
            technicalTitle.setLetterSpacing(0.08f);
            technicalTitle.setTextColor(Color.rgb(79, 94, 117));
            technicalTitle.setTypeface(Typeface.DEFAULT_BOLD);
            technicalTitle.setPadding(dp(2), dp(8), 0, dp(8));
        }

        // O painel técnico continua totalmente visível; apenas recebe acabamento visual.
        for (int i = 0; i < Math.min(3, root.getChildCount()); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && child != technicalTitle) {
                TextView diagnostics = (TextView) child;
                diagnostics.setTextColor(Color.rgb(40, 52, 70));
                diagnostics.setTextSize(13);
                diagnostics.setPadding(dp(14), dp(14), dp(14), dp(14));
                diagnostics.setBackground(rounded(SURFACE, BORDER, 16, 1));
                diagnostics.setElevation(dp(1));
            }
        }
    }

    private void styleDiagnosticTools(LinearLayout root) {
        LinearLayout tools = findContainerWithButton(root, "Atualizar diagnóstico");
        if (tools == null) return;
        tools.setPadding(0, dp(14), 0, dp(6));
        for (int i = 0; i < tools.getChildCount(); i++) {
            View child = tools.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout card = (LinearLayout) child;
                styleSurfaceCard(card, 18);
                styleCardTypography(card);
                styleButtonsRecursive(card);
            }
        }
    }

    private void hideLegacyHeader(LinearLayout root) {
        TextView title = findTextExact(root, "Chegada Casa Rápido");
        if (title != null) title.setVisibility(View.GONE);
        TextView subtitle = findText(root, "Automação de chegada");
        if (subtitle != null) subtitle.setVisibility(View.GONE);
    }

    private LinearLayout createHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(19), dp(20), dp(19));
        hero.setBackground(heroGradient());
        hero.setElevation(dp(5));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView eyebrow = text("CHEGADA CASA RÁPIDO", 11, Color.rgb(196, 218, 255), true);
        eyebrow.setLetterSpacing(0.12f);
        brand.addView(eyebrow);
        TextView title = text("Sua casa pronta\nantes de você chegar", 24, Color.WHITE, true);
        title.setLineSpacing(0f, 1.04f);
        title.setPadding(0, dp(4), dp(8), 0);
        brand.addView(title);
        top.addView(brand, brandParams);

        heroStatus = statusPill("VERIFICANDO", false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(dp(8), 0, 0, 0);
        top.addView(heroStatus, statusParams);
        hero.addView(top);

        TextView line = text("GPS inteligente, luzes e portão em uma única automação.", 13,
                Color.rgb(214, 227, 249), false);
        line.setPadding(0, dp(12), 0, dp(14));
        hero.addView(line);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        heroGps = heroChip("GPS", true);
        heroEwelink = heroChip("eWeLink", false);
        heroGate = heroChip("Portão", false);
        addHeroChip(chips, heroGps, true);
        addHeroChip(chips, heroEwelink, true);
        addHeroChip(chips, heroGate, false);
        hero.addView(chips);

        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, dp(4), 0, dp(16));
        hero.setLayoutParams(hp);
        return hero;
    }

    private void refreshHero() {
        if (heroStatus == null) return;
        SharedPreferences p = getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean active = p.getBoolean("ativa", false);
        setStatusPill(heroStatus, active ? "AUTOMAÇÃO ATIVA" : "AUTOMAÇÃO PAUSADA", active);
        setHeroChip(heroGps, "GPS  •  " + (p.getLong("monitor_fix_at", 0L) > 0L ? "OK" : "aguardando"),
                p.getLong("monitor_fix_at", 0L) > 0L);
        setHeroChip(heroEwelink, "eWeLink  •  " + (EwelinkApi.hasSession(this) ? "conectado" : "desconectado"),
                EwelinkApi.hasSession(this));
        setHeroChip(heroGate, "Portão  •  " + (EwelinkApi.hasGate(this) ? "pronto" : "não configurado"),
                EwelinkApi.hasGate(this));
    }

    private void styleConfigurationCards(LinearLayout root) {
        String[] titles = {"Residência", "Raio e horário", "eWeLink", "Automação"};
        for (String title : titles) {
            LinearLayout card = findDirectCard(root, title);
            if (card == null) continue;
            styleSurfaceCard(card, 22);
            styleCardTypography(card);
            styleInputsRecursive(card);
            styleButtonsRecursive(card);
        }
    }

    private void styleSurfaceCard(LinearLayout card, int radius) {
        card.setPadding(dp(18), dp(17), dp(18), dp(18));
        card.setBackground(rounded(SURFACE, BORDER, radius, 1));
        card.setElevation(dp(3));
        ViewGroup.LayoutParams raw = card.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) raw;
            p.setMargins(0, 0, 0, dp(14));
            card.setLayoutParams(p);
        }
    }

    private void styleCardTypography(LinearLayout card) {
        int seenText = 0;
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof TextView && !(child instanceof Button) && !(child instanceof EditText)) {
                TextView t = (TextView) child;
                if (seenText == 0) {
                    t.setTextSize(17);
                    t.setTextColor(TEXT);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                } else if (seenText == 1) {
                    t.setTextSize(12);
                    t.setTextColor(MUTED);
                    t.setLineSpacing(0, 1.08f);
                }
                seenText++;
            }
        }
    }

    private void styleInputsRecursive(View view) {
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            edit.setTextColor(TEXT);
            edit.setHintTextColor(Color.rgb(145, 155, 171));
            edit.setBackground(rounded(Color.rgb(249, 250, 252), Color.rgb(215, 222, 232), 14, 1));
            edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        } else if (view instanceof Spinner) {
            view.setBackground(rounded(Color.rgb(249, 250, 252), Color.rgb(215, 222, 232), 14, 1));
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) styleInputsRecursive(g.getChildAt(i));
        }
    }

    private void styleButtonsRecursive(View view) {
        if (view instanceof Button) {
            styleButton((Button) view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) styleButtonsRecursive(g.getChildAt(i));
        }
    }

    private void styleButton(Button b) {
        String label = String.valueOf(b.getText()).toLowerCase();
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(10), 0, dp(10), 0);

        boolean warning = label.contains("armar teste do portão");
        boolean danger = label.contains("cancelar teste do portão");
        boolean primary = label.contains("ativar automação")
                || label.contains("conectar ao ewelink")
                || label.contains("buscar endereço")
                || label.contains("simular chegada")
                || label.equals("testar áudio");

        if (warning) {
            b.setTextColor(AMBER);
            b.setBackground(ripple(SOFT_AMBER, Color.rgb(243, 213, 142), 13, Color.argb(28, 180, 105, 0)));
            b.setElevation(0);
        } else if (danger) {
            b.setTextColor(RED);
            b.setBackground(ripple(SOFT_RED, Color.rgb(244, 196, 196), 13, Color.argb(26, 190, 42, 42)));
            b.setElevation(0);
        } else if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ripple(BLUE, BLUE, 13, Color.argb(40, 255, 255, 255)));
            b.setElevation(dp(1));
        } else {
            b.setTextColor(Color.rgb(48, 71, 111));
            b.setBackground(ripple(Color.rgb(247, 249, 253), Color.rgb(215, 222, 233), 13,
                    Color.argb(28, 36, 99, 235)));
            b.setElevation(0);
        }
    }

    private void styleFooter(LinearLayout root) {
        TextView footer = findText(root, "Cilas Souza");
        if (footer != null) {
            footer.setText("CHEGADA CASA RÁPIDO  •  v2.9\nDesenvolvido por Cilas Souza");
            footer.setTextSize(10);
            footer.setTextColor(Color.rgb(139, 149, 165));
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.05f);
            footer.setPadding(0, dp(14), 0, dp(5));
        }
    }

    private TextView statusPill(String value, boolean active) {
        TextView t = text(value, 10, active ? Color.WHITE : Color.rgb(218, 226, 239), true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(10), dp(7), dp(10), dp(7));
        t.setBackground(rounded(active ? GREEN : Color.rgb(46, 66, 94),
                active ? GREEN : Color.rgb(69, 90, 120), 18, 1));
        return t;
    }

    private void setStatusPill(TextView t, String value, boolean active) {
        t.setText(value);
        t.setTextColor(active ? Color.WHITE : Color.rgb(218, 226, 239));
        t.setBackground(rounded(active ? GREEN : Color.rgb(46, 66, 94),
                active ? GREEN : Color.rgb(69, 90, 120), 18, 1));
    }

    private TextView heroChip(String value, boolean active) {
        TextView t = text(value, 10, active ? Color.WHITE : Color.rgb(214, 225, 243), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(7), dp(9), dp(7));
        t.setBackground(rounded(active ? Color.argb(65, 255, 255, 255) : Color.argb(28, 255, 255, 255),
                Color.argb(60, 255, 255, 255), 16, 1));
        return t;
    }

    private void setHeroChip(TextView t, String value, boolean active) {
        if (t == null) return;
        t.setText(value);
        t.setTextColor(active ? Color.WHITE : Color.rgb(204, 216, 236));
        t.setBackground(rounded(active ? Color.argb(65, 255, 255, 255) : Color.argb(25, 255, 255, 255),
                active ? Color.argb(80, 255, 255, 255) : Color.argb(45, 255, 255, 255), 16, 1));
    }

    private void addHeroChip(LinearLayout row, TextView chip, boolean spacing) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (spacing) p.setMargins(0, 0, dp(6), 0);
        row.addView(chip, p);
    }

    private LinearLayout findDirectCard(LinearLayout root, String heading) {
        int index = findDirectCardIndex(root, heading);
        return index >= 0 && root.getChildAt(index) instanceof LinearLayout
                ? (LinearLayout) root.getChildAt(index) : null;
    }

    private int findDirectCardIndex(LinearLayout root, String heading) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout && hasDirectText((LinearLayout) child, heading)) return i;
        }
        return -1;
    }

    private boolean hasDirectText(LinearLayout group, String value) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView && value.contentEquals(((TextView) v).getText())) return true;
        }
        return false;
    }

    private LinearLayout findContainerWithButton(View view, String buttonText) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof LinearLayout && containsButton(child, buttonText)) return (LinearLayout) child;
            LinearLayout nested = findContainerWithButton(child, buttonText);
            if (nested != null) return nested;
        }
        return null;
    }

    private boolean containsButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsButton(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private TextView findText(View view, String contains) {
        if (view instanceof TextView && String.valueOf(((TextView) view).getText()).contains(contains)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findText(g.getChildAt(i), contains);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findTextExact(View view, String exact) {
        if (view instanceof TextView && exact.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findTextExact(g.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable heroGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 35, 73), Color.rgb(23, 81, 171)});
        g.setCornerRadius(dp(26));
        return g;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private RippleDrawable ripple(int fill, int stroke, int radiusDp, int rippleColor) {
        GradientDrawable content = rounded(fill, stroke, radiusDp, 1);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
