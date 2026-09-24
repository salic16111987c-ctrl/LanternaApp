package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class EstoqueActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.parseColor("#101828"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (getWindow() != null && getWindow().getDecorView() != null) render();
    }

    private void render() {
        GestaoDbHelper db = new GestaoDbHelper(this);
        List<GestaoDbHelper.Produto> ps = db.list("");

        double custo=0, venda=0, lucro=0, itens=0;
        int baixos=0;
        for (GestaoDbHelper.Produto p : ps) {
            custo += p.valorEstoqueCusto();
            venda += p.valorEstoqueVenda();
            lucro += p.lucroPotencial();
            itens += p.estoque;
            if (p.estoqueMinimo > 0 && p.estoque <= p.estoqueMinimo) baixos++;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(30));
        scroll.addView(root);

        Button voltar = new Button(this);
        voltar.setText("← Voltar"); voltar.setAllCaps(false); voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView title = txt("Estoque", 28, true);
        title.setPadding(0, dp(16), 0, dp(12)); root.addView(title);

        LinearLayout resumo = new LinearLayout(this);
        resumo.setOrientation(LinearLayout.VERTICAL);
        resumo.setBackgroundColor(Color.WHITE);
        resumo.setPadding(dp(14), dp(14), dp(14), dp(14));
        resumo.addView(txt("Produtos cadastrados: " + ps.size(), 16, true));
        resumo.addView(txt("Quantidade em estoque: " + fmt(itens), 15, false));
        resumo.addView(txt("Custo do estoque: " + moeda.format(custo), 15, false));
        resumo.addView(txt("Venda potencial: " + moeda.format(venda), 15, false));
        resumo.addView(txt("Lucro bruto potencial: " + moeda.format(lucro), 15, true));
        TextView alert = txt("Itens em estoque baixo: " + baixos, 15, baixos>0);
        if (baixos>0) alert.setTextColor(Color.parseColor("#B42318"));
        resumo.addView(alert);
        root.addView(resumo);

        Button produtos = new Button(this);
        produtos.setText("Abrir cadastro de produtos"); produtos.setAllCaps(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(12), 0, dp(12)); produtos.setLayoutParams(bp);
        produtos.setOnClickListener(v -> startActivity(new Intent(this, ProdutosActivity.class)));
        root.addView(produtos);

        for (GestaoDbHelper.Produto p : ps) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(4), 0, dp(4)); card.setLayoutParams(cp);
            card.addView(txt(p.nome, 16, true));
            TextView linha = txt("Qtd.: " + fmt(p.estoque) +
                    "   Custo estoque: " + moeda.format(p.valorEstoqueCusto()) +
                    "\nVenda potencial: " + moeda.format(p.valorEstoqueVenda()) +
                    "   Lucro: " + moeda.format(p.lucroPotencial()), 13, false);
            linha.setTextColor(Color.parseColor("#475467"));
            card.addView(linha);
            if (p.estoqueMinimo>0 && p.estoque<=p.estoqueMinimo) {
                TextView baixo = txt("⚠ Repor estoque — mínimo: " + fmt(p.estoqueMinimo), 13, true);
                baixo.setTextColor(Color.parseColor("#B42318"));
                card.addView(baixo);
            }
            root.addView(card);
        }
        setContentView(scroll);
    }

    private String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }
}
