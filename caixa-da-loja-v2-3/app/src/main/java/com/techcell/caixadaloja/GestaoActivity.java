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
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

public class GestaoActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
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
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        GestaoDbHelper db = new GestaoDbHelper(this);
        GestaoDbHelper.ResumoVendas hoje = db.resumoHoje();

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
                "Produtos cadastrados: " + db.count() +
                "\nO banco SMB ainda NÃO foi importado.",
                13, true);
        safe.setTextColor(Color.parseColor("#176240"));
        safe.setBackgroundColor(Color.parseColor("#ECFDF3"));
        safe.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams safeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        safeLp.setMargins(0, dp(18), 0, dp(18));
        safe.setLayoutParams(safeLp);
        root.addView(safe);

        TextView fin = text("Hoje", 20, true);
        root.addView(fin);
        root.addView(text("Vendas realizadas: " + hoje.quantidadeVendas, 15, false));
        root.addView(text("Total vendido: " + moeda.format(hoje.total), 17, true));
        root.addView(text("Custo das mercadorias: " + moeda.format(hoje.custo), 15, false));
        root.addView(text("Lucro bruto: " + moeda.format(hoje.lucro), 16, true));
        root.addView(text("Dinheiro: " + moeda.format(hoje.dinheiro) +
                "   PIX: " + moeda.format(hoje.pix), 14, false));
        root.addView(text("Cartão: " + moeda.format(hoje.cartao), 14, false));

        TextView menu = text("Módulos", 20, true);
        menu.setPadding(0, dp(22), 0, dp(6));
        root.addView(menu);

        Button pdv = action("🛒  PDV / Frente de Caixa");
        pdv.setOnClickListener(v -> startActivity(new Intent(this, PdvActivity.class)));
        root.addView(pdv);

        Button prod = action("📦  Produtos");
        prod.setOnClickListener(v -> startActivity(new Intent(this, ProdutosActivity.class)));
        root.addView(prod);

        Button est = action("🧮  Estoque");
        est.setOnClickListener(v -> startActivity(new Intent(this, EstoqueActivity.class)));
        root.addView(est);

        Button financeiro = action("💰  Financeiro");
        financeiro.setOnClickListener(v -> Toast.makeText(this, "Financeiro detalhado em construção.", Toast.LENGTH_SHORT).show());
        root.addView(financeiro);

        Button clientes = action("👤  Clientes");
        clientes.setOnClickListener(v -> Toast.makeText(this, "Clientes em construção.", Toast.LENGTH_SHORT).show());
        root.addView(clientes);

        Button fornecedores = action("🚚  Fornecedores");
        fornecedores.setOnClickListener(v -> Toast.makeText(this, "Fornecedores em construção.", Toast.LENGTH_SHORT).show());
        root.addView(fornecedores);

        Button rel = action("📊  Relatórios");
        rel.setOnClickListener(v -> Toast.makeText(this, "Relatórios em construção.", Toast.LENGTH_SHORT).show());
        root.addView(rel);

        Button smb = action("🗃️  Importação do SMB");
        smb.setOnClickListener(v -> Toast.makeText(this, "Importação bloqueada até conferirmos os registros do SMB.", Toast.LENGTH_LONG).show());
        root.addView(smb);

        TextView next = text(
                "Já funcional: Produtos, Estoque e PDV com baixa automática no estoque e cálculo do lucro da venda.",
                13, false);
        next.setTextColor(Color.parseColor("#667085"));
        next.setGravity(Gravity.CENTER);
        next.setPadding(dp(8), dp(22), dp(8), 0);
        root.addView(next);

        setContentView(scroll);
    }
}
