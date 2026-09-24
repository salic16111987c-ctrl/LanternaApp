package com.techcell.caixadaloja;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class GestaoActivity extends Activity {
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

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(dp(54));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> Toast.makeText(
                this, "Módulo em construção. Nenhum dado foi alterado.", Toast.LENGTH_SHORT).show());
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(root);

        Button back = new Button(this);
        back.setText("←  Voltar");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = text("Gestão Tech Cell", 28, true);
        title.setPadding(0, dp(18), 0, 0);
        root.addView(title);

        TextView sub = text("Sistema completo da loja", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        TextView safe = text(
                "MÓDULO SEPARADO DO CAIXA DA LOJA\n" +
                "O histórico SMB ainda não foi importado. Esta versão é apenas a estrutura inicial.",
                13, true);
        safe.setTextColor(Color.parseColor("#176240"));
        safe.setBackgroundColor(Color.parseColor("#ECFDF3"));
        safe.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams safeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        safeLp.setMargins(0, dp(18), 0, dp(18));
        safe.setLayoutParams(safeLp);
        root.addView(safe);

        TextView fin = text("Financeiro", 20, true);
        root.addView(fin);
        root.addView(text("Vendas: R$ 0,00", 16, true));
        root.addView(text("Custo das mercadorias: R$ 0,00", 16, false));
        root.addView(text("Lucro bruto: R$ 0,00  (venda − custo)", 16, false));
        root.addView(text("Despesas: R$ 0,00", 16, false));
        root.addView(text("Lucro líquido: R$ 0,00  (lucro bruto − despesas)", 16, true));

        TextView menu = text("Módulos", 20, true);
        menu.setPadding(0, dp(22), 0, dp(6));
        root.addView(menu);

        root.addView(action("🛒  PDV / Frente de Caixa"));
        root.addView(action("📦  Produtos"));
        root.addView(action("🧮  Estoque"));
        root.addView(action("💰  Financeiro"));
        root.addView(action("👤  Clientes"));
        root.addView(action("🚚  Fornecedores"));
        root.addView(action("📊  Relatórios"));
        root.addView(action("🗃️  Importação do SMB"));

        TextView next = text(
                "Próxima etapa: estruturar Produtos + Estoque e preparar a importação segura do banco SMB.",
                13, false);
        next.setTextColor(Color.parseColor("#667085"));
        next.setGravity(Gravity.CENTER);
        next.setPadding(dp(8), dp(22), dp(8), 0);
        root.addView(next);

        setContentView(scroll);
    }
}
