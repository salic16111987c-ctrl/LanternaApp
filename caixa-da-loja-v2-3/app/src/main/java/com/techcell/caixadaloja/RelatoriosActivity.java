package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.DatePickerDialog;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RelatoriosActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat dataCurta = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt","BR"));

    private GestaoDbHelper db;
    private LinearLayout conteudo;
    private TextView periodoTexto;
    private Button abaResumo;
    private Button abaProdutos;
    private Button abaDespesas;
    private Button abaEstoque;

    private long inicioAtual;
    private long fimAtual;
    private int abaAtual = 0;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TechCellUi.TEXT);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinHeight(dp(46));
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);
        montar();
        periodoMesAtual();
    }

    private void montar() {
        TechCellUi.applyWindowChrome(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(root);

        Button voltar = action("← Voltar");
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        TextView titulo = txt("Relatórios", 27, true);
        titulo.setPadding(0, dp(14), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Visão rápida de resultados • Alpha 42", 13, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        periodoTexto = txt("", 13, true);
        periodoTexto.setTextColor(TechCellUi.NAVY);
        periodoTexto.setBackground(TechCellUi.pillBackground(this));
        periodoTexto.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams perLp = TechCellUi.fullCardParams(this, 12);
        root.addView(periodoTexto, perLp);

        LinearLayout f1 = new LinearLayout(this);
        f1.setOrientation(LinearLayout.HORIZONTAL);
        f1.setPadding(0, dp(8), 0, 0);

        Button hoje = action("Hoje");
        TechCellUi.styleFilter(this, hoje);
        hoje.setOnClickListener(v -> periodoHoje());
        f1.addView(hoje, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button mes = action("Este mês");
        TechCellUi.styleFilter(this, mes);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(46), 1);
        mp.setMargins(dp(6),0,0,0);
        mes.setOnClickListener(v -> periodoMesAtual());
        f1.addView(mes, mp);
        root.addView(f1);

        LinearLayout f2 = new LinearLayout(this);
        f2.setOrientation(LinearLayout.HORIZONTAL);
        f2.setPadding(0, dp(6), 0, 0);

        Button ultimos30 = action("30 dias");
        TechCellUi.styleFilter(this, ultimos30);
        ultimos30.setOnClickListener(v -> periodoUltimos30());
        f2.addView(ultimos30, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button personalizado = action("Período...");
        TechCellUi.styleFilter(this, personalizado);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(46), 1);
        pp.setMargins(dp(6),0,0,0);
        personalizado.setOnClickListener(v -> escolherPeriodo());
        f2.addView(personalizado, pp);
        root.addView(f2);

        TextView navegar = txt("Exibir", 12, true);
        navegar.setTextColor(TechCellUi.MUTED);
        navegar.setPadding(0, dp(14), 0, dp(4));
        root.addView(navegar);

        LinearLayout tabs1 = new LinearLayout(this);
        tabs1.setOrientation(LinearLayout.HORIZONTAL);
        abaResumo = action("Resumo");
        abaProdutos = action("Produtos");
        abaResumo.setOnClickListener(v -> selecionarAba(0));
        abaProdutos.setOnClickListener(v -> selecionarAba(1));
        tabs1.addView(abaResumo, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(46), 1);
        p2.setMargins(dp(6),0,0,0);
        tabs1.addView(abaProdutos, p2);
        root.addView(tabs1);

        LinearLayout tabs2 = new LinearLayout(this);
        tabs2.setOrientation(LinearLayout.HORIZONTAL);
        tabs2.setPadding(0, dp(6), 0, 0);
        abaDespesas = action("Despesas");
        abaEstoque = action("Estoque");
        abaDespesas.setOnClickListener(v -> selecionarAba(2));
        abaEstoque.setOnClickListener(v -> selecionarAba(3));
        tabs2.addView(abaDespesas, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(0, dp(46), 1);
        p4.setMargins(dp(6),0,0,0);
        tabs2.addView(abaEstoque, p4);
        root.addView(tabs2);

        conteudo = new LinearLayout(this);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        root.addView(conteudo);

        setContentView(scroll);
    }

    private void selecionarAba(int aba) {
        abaAtual = aba;
        carregar();
    }

    private void atualizarAbas() {
        Button[] abas = {abaResumo, abaProdutos, abaDespesas, abaEstoque};
        for (int i=0; i<abas.length; i++) {
            if (i == abaAtual) TechCellUi.stylePrimary(this, abas[i], TechCellUi.NAVY);
            else TechCellUi.styleSecondary(this, abas[i]);
        }
    }

    private void periodoHoje() {
        Calendar c = Calendar.getInstance();
        zerarHora(c);
        inicioAtual = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        fimAtual = c.getTimeInMillis();
        carregar();
    }

    private void periodoMesAtual() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        zerarHora(c);
        inicioAtual = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        fimAtual = c.getTimeInMillis();
        carregar();
    }

    private void periodoUltimos30() {
        Calendar fim = Calendar.getInstance();
        fim.add(Calendar.DAY_OF_MONTH, 1);
        zerarHora(fim);
        fimAtual = fim.getTimeInMillis();
        Calendar ini = (Calendar) fim.clone();
        ini.add(Calendar.DAY_OF_MONTH, -30);
        inicioAtual = ini.getTimeInMillis();
        carregar();
    }

    private void escolherPeriodo() {
        Calendar ini = Calendar.getInstance();
        if (inicioAtual > 0) ini.setTimeInMillis(inicioAtual);

        new DatePickerDialog(this, (v, ano, mes, dia) -> {
            Calendar escolhidoIni = Calendar.getInstance();
            escolhidoIni.set(ano, mes, dia);
            zerarHora(escolhidoIni);

            Calendar baseFim = Calendar.getInstance();
            long baseMillis = fimAtual > inicioAtual ? fimAtual - 1 : System.currentTimeMillis();
            baseFim.setTimeInMillis(baseMillis);

            new DatePickerDialog(this, (v2, ano2, mes2, dia2) -> {
                Calendar escolhidoFim = Calendar.getInstance();
                escolhidoFim.set(ano2, mes2, dia2);
                zerarHora(escolhidoFim);
                escolhidoFim.add(Calendar.DAY_OF_MONTH, 1);

                if (escolhidoFim.getTimeInMillis() <= escolhidoIni.getTimeInMillis()) {
                    escolhidoFim.setTimeInMillis(escolhidoIni.getTimeInMillis());
                    escolhidoFim.add(Calendar.DAY_OF_MONTH, 1);
                }

                inicioAtual = escolhidoIni.getTimeInMillis();
                fimAtual = escolhidoFim.getTimeInMillis();
                carregar();
            }, baseFim.get(Calendar.YEAR), baseFim.get(Calendar.MONTH),
                    baseFim.get(Calendar.DAY_OF_MONTH)).show();

        }, ini.get(Calendar.YEAR), ini.get(Calendar.MONTH), ini.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void zerarHora(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
    }

    private void carregar() {
        if (conteudo == null || inicioAtual <= 0 || fimAtual <= inicioAtual) return;

        long fimInclusivo = fimAtual - 1;
        periodoTexto.setText(
                dataCurta.format(new Date(inicioAtual)) + "  →  " +
                dataCurta.format(new Date(fimInclusivo)));

        atualizarAbas();
        conteudo.removeAllViews();

        if (abaAtual == 0) carregarResumo();
        else if (abaAtual == 1) carregarProdutos();
        else if (abaAtual == 2) carregarDespesas();
        else carregarEstoque();
    }

    private void carregarResumo() {
        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);
        double margemLiquida = r.vendas.total > 0 ? (r.lucroLiquido / r.vendas.total) * 100.0 : 0;

        LinearLayout hero = TechCellUi.card(this);
        hero.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        hero.setBackground(TechCellUi.solid(this,
                r.lucroLiquido >= 0 ? TechCellUi.PALE_GREEN : Color.parseColor("#FFF1F0"), 16));

        TextView h1 = txt("LUCRO LÍQUIDO", 12, true);
        h1.setTextColor(TechCellUi.MUTED);
        hero.addView(h1);

        TextView h2 = txt(moeda.format(r.lucroLiquido), 28, true);
        h2.setTextColor(r.lucroLiquido >= 0 ? TechCellUi.GREEN : TechCellUi.RED);
        h2.setPadding(0, dp(4), 0, 0);
        hero.addView(h2);

        TextView h3 = txt("Margem líquida: " + percentual(margemLiquida), 12, true);
        h3.setTextColor(TechCellUi.NAVY);
        h3.setPadding(0, dp(5), 0, 0);
        hero.addView(h3);
        conteudo.addView(hero);

        addKpiRow(
                kpi("Total vendido", moeda.format(r.vendas.total), TechCellUi.GREEN),
                kpi("Lucro bruto", moeda.format(r.vendas.lucro), TechCellUi.GREEN));
        addKpiRow(
                kpi("Custo vendido", moeda.format(r.vendas.custo), TechCellUi.TEXT),
                kpi("Despesas", moeda.format(r.despesasOperacionais), TechCellUi.RED));
        addKpiRow(
                kpi("Vendas", String.valueOf(r.vendas.quantidadeVendas), TechCellUi.BLUE),
                kpi("Descontos", moeda.format(r.vendas.desconto), TechCellUi.ORANGE));

        LinearLayout rec = TechCellUi.card(this);
        rec.setLayoutParams(TechCellUi.fullCardParams(this, 8));
        TextView t = txt("RECEBIMENTOS", 11, true);
        t.setTextColor(TechCellUi.MUTED);
        rec.addView(t);
        double outrosRecebimentos = Math.max(0,
                r.vendas.total - r.vendas.dinheiro - r.vendas.pix - r.vendas.cartao);
        String recebimentosTexto =
                "Dinheiro " + moeda.format(r.vendas.dinheiro) +
                        "   •   PIX " + moeda.format(r.vendas.pix) +
                        "   •   Cartão " + moeda.format(r.vendas.cartao);
        if (outrosRecebimentos > 0.005) {
            recebimentosTexto += "   •   Outros/prazo " + moeda.format(outrosRecebimentos);
        }
        TextView valores = txt(recebimentosTexto, 13, true);
        valores.setPadding(0, dp(5), 0, 0);
        rec.addView(valores);
        conteudo.addView(rec);

        Button mercadorias = action("Abrir Financeiro • Produtos  →");
        TechCellUi.stylePrimary(this, mercadorias);
        mercadorias.setOnClickListener(v ->
                abrirFinanceiroProdutos());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        mp.setMargins(0, dp(10), 0, 0);
        conteudo.addView(mercadorias, mp);
    }

    private void carregarProdutos() {
        Button detalhado = action("Abrir Financeiro • Produtos  →");
        TechCellUi.stylePrimary(this, detalhado);
        detalhado.setOnClickListener(v ->
                abrirFinanceiroProdutos());
        conteudo.addView(detalhado, TechCellUi.fullCardParams(this, 12));

        secao("MAIS VENDIDOS");
        List<GestaoDbHelper.RelatorioProduto> top = db.topProdutosPeriodo(inicioAtual, fimAtual, 10);
        if (top.isEmpty()) {
            vazio("Nenhum produto vendido neste período.");
            return;
        }

        int pos = 1;
        for (GestaoDbHelper.RelatorioProduto p : top) {
            LinearLayout card = cardBase();
            TextView nome = txt(pos + "º  " + p.nome, 15, true);
            card.addView(nome);
            TextView detalhe = txt(
                    qtd(p.quantidade) +
                            (p.unidade == null || p.unidade.trim().isEmpty() ? "" : " " + p.unidade) +
                            "   •   Vendido " + moeda.format(p.faturamento) +
                            "   •   Lucro " + moeda.format(p.lucro),
                    12, false);
            detalhe.setTextColor(TechCellUi.MUTED);
            detalhe.setPadding(0, dp(4), 0, 0);
            card.addView(detalhe);
            conteudo.addView(card);
            pos++;
        }
    }

    private void abrirFinanceiroProdutos() {
        Intent i = new Intent(this, FinanceiroActivity.class);
        i.putExtra("aba_financeiro", "produtos");
        startActivity(i);
    }

    private void carregarDespesas() {
        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);

        addKpiRow(
                kpi("Despesas operacionais", moeda.format(r.despesasOperacionais), TechCellUi.RED),
                kpi("Outras saídas", moeda.format(r.outrasSaidas), TechCellUi.RED));
        addKpiRow(
                kpi("Compras p/ estoque", moeda.format(r.comprasEstoque), TechCellUi.ORANGE),
                kpi("Total vendido", moeda.format(r.vendas.total), TechCellUi.GREEN));

        secao("POR CATEGORIA");
        List<GestaoDbHelper.RelatorioGrupoValor> categorias =
                db.despesasPorCategoriaPeriodo(inicioAtual, fimAtual, 10);
        if (categorias.isEmpty()) vazio("Nenhuma despesa operacional neste período.");
        else for (GestaoDbHelper.RelatorioGrupoValor x : categorias) linhaValor(x.rotulo, x.valor);

        secao("POR FAVORECIDO / FORNECEDOR");
        List<GestaoDbHelper.RelatorioGrupoValor> favorecidos =
                db.saidasPorFavorecidoPeriodo(inicioAtual, fimAtual, 10);
        if (favorecidos.isEmpty()) vazio("Nenhuma saída registrada neste período.");
        else for (GestaoDbHelper.RelatorioGrupoValor x : favorecidos) linhaValor(x.rotulo, x.valor);
    }

    private void carregarEstoque() {
        secao("ESTOQUE BAIXO");
        List<GestaoDbHelper.RelatorioEstoque> baixos = db.produtosEstoqueBaixo(30);
        if (baixos.isEmpty()) {
            vazio("Nenhum produto abaixo do estoque mínimo.");
            return;
        }

        for (GestaoDbHelper.RelatorioEstoque x : baixos) {
            LinearLayout card = cardBase();
            TextView nome = txt(x.nome, 15, true);
            card.addView(nome);
            TextView info = txt(
                    "Atual " + qtd(x.estoque) +
                            (x.unidade == null || x.unidade.trim().isEmpty() ? "" : " " + x.unidade) +
                            "   •   Mínimo " + qtd(x.minimo),
                    12, true);
            info.setTextColor(x.estoque <= 0 ? TechCellUi.RED : TechCellUi.ORANGE);
            info.setPadding(0, dp(4), 0, 0);
            card.addView(info);
            conteudo.addView(card);
        }
    }

    private LinearLayout kpi(String titulo, String valor, int cor) {
        LinearLayout card = TechCellUi.card(this);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView t = txt(titulo, 11, true);
        t.setTextColor(TechCellUi.MUTED);
        card.addView(t);

        TextView v = txt(valor, 17, true);
        v.setTextColor(cor);
        v.setPadding(0, dp(3), 0, 0);
        card.addView(v);
        return card;
    }

    private void addKpiRow(LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(TechCellUi.fullCardParams(this, 8));

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ap.setMargins(0,0,dp(4),0);
        row.addView(a, ap);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bp.setMargins(dp(4),0,0,0);
        row.addView(b, bp);

        conteudo.addView(row);
    }

    private void secao(String titulo) {
        TextView t = txt(titulo, 12, true);
        t.setTextColor(TechCellUi.MUTED);
        t.setPadding(0, dp(16), 0, dp(4));
        conteudo.addView(t);
    }

    private void linhaValor(String rotulo, double valor) {
        LinearLayout card = cardBase();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = txt(rotulo, 13, false);
        card.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView v = txt(moeda.format(valor), 13, true);
        v.setGravity(Gravity.END);
        card.addView(v);
        conteudo.addView(card);
    }

    private LinearLayout cardBase() {
        LinearLayout card = TechCellUi.card(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(p);
        return card;
    }

    private void vazio(String texto) {
        TextView t = txt(texto, 13, false);
        t.setTextColor(TechCellUi.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(18), dp(8), dp(18));
        conteudo.addView(t);
    }

    private String qtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "")
                .replace(".", ",");
    }

    private String percentual(double v) {
        return String.format(new Locale("pt","BR"), "%.2f%%", v);
    }
}
