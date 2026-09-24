package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TechCellHomeActivity extends Activity {
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#101828"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button moduleButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setMinHeight(dp(64));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(34));
        scroll.addView(root);

        TextView brand = text("TECH CELL ACS", 30, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView sub = text("Escolha o sistema que deseja usar", 15, false);
        sub.setTextColor(Color.parseColor("#667085"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub);

        Button caixa = moduleButton("💵  Caixa da Loja");
        caixa.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(caixa);

        TextView caixaInfo = text(
                "Mantém o caixa atual: lançamentos diários, resumo, fechamento e planilha. " +
                "Este módulo continua separado da nova gestão.", 13, false);
        caixaInfo.setTextColor(Color.parseColor("#667085"));
        caixaInfo.setPadding(dp(8), 0, dp(8), dp(18));
        root.addView(caixaInfo);

        Button gestao = moduleButton("🏪  Gestão Tech Cell");
        gestao.setOnClickListener(v -> startActivity(new Intent(this, GestaoActivity.class)));
        root.addView(gestao);

        TextView gestaoInfo = text(
                "Novo módulo: PDV, produtos, estoque, clientes, fornecedores, financeiro, " +
                "custos e lucros. Os dados do SMB serão importados somente depois da conferência.", 13, false);
        gestaoInfo.setTextColor(Color.parseColor("#667085"));
        gestaoInfo.setPadding(dp(8), 0, dp(8), dp(18));
        root.addView(gestaoInfo);

        TextView safe = text("Versão inicial de teste — não altera os dados do Caixa da Loja.", 12, true);
        safe.setTextColor(Color.parseColor("#176240"));
        safe.setGravity(Gravity.CENTER);
        safe.setPadding(dp(8), dp(18), dp(8), 0);
        root.addView(safe);

        setContentView(scroll);
    }
}
