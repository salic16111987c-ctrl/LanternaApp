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

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class EstoqueActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private int ordem = 3;
    private boolean syncProdutosRodando;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TechCellUi.TEXT);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        super.onResume();
        TechCellBackgroundSync.garantir(this);
        if (getWindow() != null && getWindow().getDecorView() != null) render();
        sincronizarProdutosMaster();
    }

    private void sincronizarProdutosMaster() {
        if (syncProdutosRodando) return;
        GestaoDbHelper dbLocal = new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx;
        try {
            ctx = dbLocal.getSyncContext();
        } finally {
            dbLocal.close();
        }
        if (!ctx.configurado || "MASTER".equalsIgnoreCase(ctx.papelDispositivo) ||
                ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) return;

        syncProdutosRodando = true;
        new Thread(() -> {
            TechCellSyncCoordinator.Resultado r =
                    TechCellSyncCoordinator.sincronizar(getApplicationContext());
            runOnUiThread(() -> {
                syncProdutosRodando = false;
                if (!r.ocupado && r.produtosAlterados > 0) render();
            });
        }, "TechCell-Estoque-Sync").start();
    }

    private LinearLayout kpi(String label, String value, int color) {
        LinearLayout c = TechCellUi.card(this);
        c.setPadding(dp(11), dp(9), dp(11), dp(9));
        TextView l = txt(label, 11, true);
        l.setTextColor(TechCellUi.MUTED);
        c.addView(l);
        TextView v = txt(value, 17, true);
        v.setTextColor(color);
        v.setPadding(0, dp(3), 0, 0);
        c.addView(v);
        return c;
    }

    private void addKpiRow(LinearLayout root, LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = TechCellUi.fullCardParams(this, 7);
        row.setLayoutParams(rp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ap.setMargins(0,0,dp(4),0);
        row.addView(a, ap);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bp.setMargins(dp(4),0,0,0);
        row.addView(b, bp);
        root.addView(row);
    }

    private void render() {
        TechCellUi.applyWindowChrome(this);
        GestaoDbHelper db = new GestaoDbHelper(this);
        GestaoDbHelper.ResumoEstoqueGeral resumoGeral;
        List<GestaoDbHelper.Produto> ps;
        try {
            resumoGeral = db.resumoEstoqueGeral();
            ps = db.listProdutosTela("", ordem, 80);
        } finally {
            db.close();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(root);

        Button voltar = new Button(this);
        voltar.setText("←  Voltar");
        voltar.setTextSize(14);
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView title = txt("Estoque", 27, true);
        title.setPadding(0, dp(14), 0, dp(2));
        root.addView(title);

        TextView sub = txt("Visão geral do seu estoque • Alpha 42", 12, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout resumo = TechCellUi.card(this);
        resumo.setLayoutParams(TechCellUi.fullCardParams(this, 12));

        TextView resumoTitulo = txt("▣  Resumo do estoque", 15, true);
        resumoTitulo.setTextColor(TechCellUi.NAVY);
        resumo.addView(resumoTitulo);

        addKpiRow(resumo,
                kpi("Produtos cadastrados", String.valueOf(resumoGeral.produtos), TechCellUi.NAVY),
                kpi("Soma das quantidades", fmt(resumoGeral.quantidadeTotal), TechCellUi.NAVY));
        addKpiRow(resumo,
                kpi("Custo do estoque", moeda.format(resumoGeral.custoTotal), TechCellUi.TEXT),
                kpi("Venda potencial", moeda.format(resumoGeral.vendaPotencial), TechCellUi.GREEN));
        addKpiRow(resumo,
                kpi("Lucro bruto potencial", moeda.format(resumoGeral.lucroPotencial), TechCellUi.GREEN),
                kpi("Estoque baixo", String.valueOf(resumoGeral.estoqueBaixo),
                        resumoGeral.estoqueBaixo > 0 ? TechCellUi.RED : TechCellUi.GREEN));

        TextView obs = txt(
                "ⓘ A soma das quantidades é apenas informativa porque o estoque pode misturar UN, PC, CX, KG, M etc.",
                11, false);
        obs.setTextColor(TechCellUi.MUTED);
        obs.setPadding(0, dp(9), 0, 0);
        resumo.addView(obs);
        root.addView(resumo);

        Button produtos = new Button(this);
        produtos.setText("▣  Abrir cadastro de produtos   ›");
        produtos.setTextSize(14);
        TechCellUi.styleSecondary(this, produtos);
        produtos.setOnClickListener(v -> startActivity(new Intent(this, ProdutosActivity.class)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        bp.setMargins(0, dp(10), 0, dp(12));
        root.addView(produtos, bp);

        Button revisarSuspeitos = new Button(this);
        revisarSuspeitos.setText("⚠  Revisar estoque suspeito");
        revisarSuspeitos.setTextSize(14);
        TechCellUi.stylePrimary(this, revisarSuspeitos, TechCellUi.ORANGE);
        revisarSuspeitos.setOnClickListener(v ->
                startActivity(new Intent(this, SaneamentoValoresActivity.class)));
        LinearLayout.LayoutParams rpSuspeitos = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rpSuspeitos.setMargins(0, 0, 0, dp(12));
        root.addView(revisarSuspeitos, rpSuspeitos);

        LinearLayout cabLista = new LinearLayout(this);
        cabLista.setOrientation(LinearLayout.HORIZONTAL);
        cabLista.setGravity(Gravity.CENTER_VERTICAL);

        TextView listaTitulo = txt("Produtos em estoque", 16, true);
        cabLista.addView(listaTitulo, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button ordenar = new Button(this);
        String ordemTexto = ordem == 3 ? "Maior → menor" :
                ordem == 1 ? "Menor → maior" : "Nome A-Z";
        ordenar.setText(ordemTexto);
        ordenar.setTextSize(11);
        TechCellUi.styleSecondary(this, ordenar);
        ordenar.setOnClickListener(v -> {
            if (ordem == 3) ordem = 1;
            else if (ordem == 1) ordem = 0;
            else ordem = 3;
            render();
        });
        cabLista.addView(ordenar, new LinearLayout.LayoutParams(dp(138), dp(40)));
        root.addView(cabLista);

        TextView limiteLista = txt(
                (ordem == 3 ? "Ranking: " : "") +
                        "exibindo " + ps.size() + " de " + resumoGeral.produtos +
                        " produtos. O resumo acima considera o estoque completo.",
                11, false);
        limiteLista.setTextColor(TechCellUi.MUTED);
        limiteLista.setPadding(0, dp(5), 0, dp(4));
        root.addView(limiteLista);

        int posicaoRanking = 1;
        for (GestaoDbHelper.Produto p : ps) {
            LinearLayout card = TechCellUi.card(this);
            card.setPadding(dp(12), dp(10), dp(10), dp(10));
            card.setLayoutParams(TechCellUi.fullCardParams(this, 7));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            String inicial;
            if (ordem == 3) {
                inicial = posicaoRanking + "º";
            } else {
                inicial = p.nome == null || p.nome.trim().isEmpty()
                        ? "P" : p.nome.trim().substring(0,1).toUpperCase(new Locale("pt","BR"));
            }
            TextView avatar = txt(inicial, ordem == 3 ? 13 : 18, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setTextColor(TechCellUi.BLUE);
            avatar.setBackground(TechCellUi.solid(this, TechCellUi.PALE_BLUE, 12));
            top.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout dados = new LinearLayout(this);
            dados.setOrientation(LinearLayout.VERTICAL);
            dados.setPadding(dp(10),0,dp(6),0);
            dados.addView(txt(p.nome, 15, true));

            String un = p.unidade == null || p.unidade.trim().isEmpty() ? "UN" : p.unidade.trim();
            TextView l1 = txt(
                    "Qtd. " + fmt(p.estoque) + " " + un +
                            "   •   Custo " + moeda.format(p.valorEstoqueCusto()),
                    12, false);
            l1.setTextColor(TechCellUi.MUTED);
            l1.setPadding(0,dp(3),0,0);
            dados.addView(l1);

            TextView l2 = txt(
                    "Venda potencial " + moeda.format(p.valorEstoqueVenda()) +
                            "   •   Lucro " + moeda.format(p.lucroPotencial()),
                    12, false);
            l2.setTextColor(Color.parseColor("#475467"));
            l2.setPadding(0,dp(2),0,0);
            dados.addView(l2);

            top.addView(dados, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView seta = txt("›", 25, true);
            seta.setTextColor(TechCellUi.NAVY);
            top.addView(seta);
            card.addView(top);

            if (p.estoqueMinimo > 0 && p.estoque <= p.estoqueMinimo) {
                TextView baixo = TechCellUi.chip(
                        this,
                        "⚠ Repor estoque — mínimo: " + fmt(p.estoqueMinimo) + " " + un,
                        TechCellUi.RED,
                        Color.parseColor("#FFF1F0"));
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                alp.setMargins(dp(54), dp(6), 0, 0);
                card.addView(baixo, alp);
            }

            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ProdutosActivity.class);
                i.putExtra(ProdutosActivity.EXTRA_PRODUTO_ID, p.id);
                i.putExtra(ProdutosActivity.EXTRA_FECHAR_APOS_SALVAR, true);
                startActivity(i);
            });
            root.addView(card);
            posicaoRanking++;
        }

        setContentView(scroll);
    }

    private String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }
}
