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

    private long inicioAtual;
    private long fimAtual;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(48));
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);
        montar();
        periodoMesAtual();
    }

    private void montar() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        Button voltar = action("← Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView titulo = txt("Relatórios", 28, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Vendas, resultados, produtos e estoque • Alpha 25", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        Button vendasProdutos = action("Vendas de Produtos  →");
        vendasProdutos.setTextSize(16);
        vendasProdutos.setOnClickListener(v ->
                startActivity(new Intent(this, RelatorioVendasProdutosActivity.class)));
        LinearLayout.LayoutParams vendasProdutosLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        vendasProdutosLp.setMargins(0, dp(14), 0, dp(4));
        root.addView(vendasProdutos, vendasProdutosLp);

        periodoTexto = txt("", 14, true);
        periodoTexto.setTextColor(Color.parseColor("#344054"));
        periodoTexto.setPadding(0, dp(12), 0, dp(6));
        root.addView(periodoTexto);

        LinearLayout filtros1 = new LinearLayout(this);
        filtros1.setOrientation(LinearLayout.HORIZONTAL);

        Button hoje = action("Hoje");
        hoje.setOnClickListener(v -> periodoHoje());
        filtros1.addView(hoje, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button mes = action("Este mês");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(50), 1);
        mp.setMargins(dp(8), 0, 0, 0);
        mes.setLayoutParams(mp);
        mes.setOnClickListener(v -> periodoMesAtual());
        filtros1.addView(mes);

        root.addView(filtros1);

        LinearLayout filtros2 = new LinearLayout(this);
        filtros2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams f2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        f2p.setMargins(0, dp(8), 0, dp(12));
        filtros2.setLayoutParams(f2p);

        Button ultimos30 = action("Últimos 30 dias");
        ultimos30.setOnClickListener(v -> periodoUltimos30());
        filtros2.addView(ultimos30, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button personalizado = action("Período...");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(50), 1);
        pp.setMargins(dp(8),0,0,0);
        personalizado.setLayoutParams(pp);
        personalizado.setOnClickListener(v -> escolherPeriodo());
        filtros2.addView(personalizado);

        root.addView(filtros2);

        conteudo = new LinearLayout(this);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        root.addView(conteudo);

        setContentView(scroll);
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

            Calendar fimBase = Calendar.getInstance();
            fimBase.setTimeInMillis(Math.max(inicioAtual, System.currentTimeMillis()));

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
            }, fimBase.get(Calendar.YEAR), fimBase.get(Calendar.MONTH),
                    fimBase.get(Calendar.DAY_OF_MONTH)).show();

        }, ini.get(Calendar.YEAR), ini.get(Calendar.MONTH), ini.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void zerarHora(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
    }

    private void carregar() {
        if (conteudo == null) return;
        conteudo.removeAllViews();

        long fimInclusivo = Math.max(inicioAtual, fimAtual - 1);
        periodoTexto.setText("Período: " +
                dataCurta.format(new Date(inicioAtual)) + " a " +
                dataCurta.format(new Date(fimInclusivo)));

        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);

        secao("RESUMO");
        cardResumo("Vendas realizadas", String.valueOf(r.vendas.quantidadeVendas), "#175CD3");
        cardResumo("Total vendido", moeda.format(r.vendas.total), "#07884B");
        cardResumo("Custo das mercadorias vendidas", moeda.format(r.vendas.custo), "#475467");
        cardResumo("Lucro bruto", moeda.format(r.vendas.lucro), "#176240");
        cardResumo("Despesas operacionais", moeda.format(r.despesasOperacionais), "#B42318");
        if (r.outrasSaidas > 0.001) {
            cardResumo("Outras saídas que afetam resultado", moeda.format(r.outrasSaidas), "#B42318");
        }
        cardResumo("Lucro líquido", moeda.format(r.lucroLiquido),
                r.lucroLiquido >= 0 ? "#176240" : "#B42318");
        if (r.comprasEstoque > 0.001) {
            cardResumo("Compras para estoque", moeda.format(r.comprasEstoque), "#B54708");
        }
        if (r.vendas.desconto > 0.001) {
            cardResumo("Descontos concedidos", moeda.format(r.vendas.desconto), "#B54708");
        }

        secao("FORMAS DE RECEBIMENTO");
        linhaValor("Dinheiro", r.vendas.dinheiro, false);
        linhaValor("PIX", r.vendas.pix, false);
        linhaValor("Cartão", r.vendas.cartao, false);

        secao("PRODUTOS MAIS VENDIDOS");
        List<GestaoDbHelper.RelatorioProduto> top = db.topProdutosPeriodo(inicioAtual, fimAtual, 10);
        if (top.isEmpty()) {
            vazio("Nenhum produto vendido neste período.");
        } else {
            int pos = 1;
            for (GestaoDbHelper.RelatorioProduto p : top) {
                LinearLayout card = cardBase();
                TextView nome = txt(pos + "º  " + p.nome, 15, true);
                card.addView(nome);
                TextView detalhe = txt(
                        "Quantidade: " + qtd(p.quantidade) +
                                (p.unidade == null || p.unidade.trim().isEmpty() ? "" : " " + p.unidade) +
                                "\nFaturamento: " + moeda.format(p.faturamento) +
                                "   •   Lucro: " + moeda.format(p.lucro),
                        12, false);
                detalhe.setTextColor(Color.parseColor("#667085"));
                card.addView(detalhe);
                conteudo.addView(card);
                pos++;
            }
        }

        secao("DESPESAS POR CATEGORIA");
        List<GestaoDbHelper.RelatorioGrupoValor> categorias =
                db.despesasPorCategoriaPeriodo(inicioAtual, fimAtual, 10);
        if (categorias.isEmpty()) {
            vazio("Nenhuma despesa operacional neste período.");
        } else {
            for (GestaoDbHelper.RelatorioGrupoValor x : categorias) {
                linhaValor(x.rotulo, x.valor, true);
            }
        }

        secao("SAÍDAS POR FAVORECIDO / FORNECEDOR");
        List<GestaoDbHelper.RelatorioGrupoValor> favorecidos =
                db.saidasPorFavorecidoPeriodo(inicioAtual, fimAtual, 10);
        if (favorecidos.isEmpty()) {
            vazio("Nenhuma saída registrada neste período.");
        } else {
            for (GestaoDbHelper.RelatorioGrupoValor x : favorecidos) {
                linhaValor(x.rotulo, x.valor, true);
            }
        }

        secao("ESTOQUE BAIXO");
        List<GestaoDbHelper.RelatorioEstoque> baixos = db.produtosEstoqueBaixo(30);
        if (baixos.isEmpty()) {
            vazio("Nenhum produto abaixo do estoque mínimo.");
        } else {
            for (GestaoDbHelper.RelatorioEstoque x : baixos) {
                LinearLayout card = cardBase();
                TextView nome = txt(x.nome, 15, true);
                card.addView(nome);
                TextView info = txt(
                        "Atual: " + qtd(x.estoque) +
                                (x.unidade == null || x.unidade.trim().isEmpty() ? "" : " " + x.unidade) +
                                "   •   Mínimo: " + qtd(x.minimo),
                        12, false);
                info.setTextColor(x.estoque <= 0
                        ? Color.parseColor("#B42318")
                        : Color.parseColor("#B54708"));
                card.addView(info);
                conteudo.addView(card);
            }
        }

        TextView nota = txt(
                "Compras para estoque aparecem como saída financeira, mas não são descontadas novamente " +
                        "do lucro líquido porque o custo já entra quando a mercadoria é vendida.",
                11, false);
        nota.setTextColor(Color.parseColor("#667085"));
        nota.setGravity(Gravity.CENTER);
        nota.setPadding(dp(8), dp(18), dp(8), dp(8));
        conteudo.addView(nota);
    }

    private void secao(String titulo) {
        TextView t = txt(titulo, 13, true);
        t.setTextColor(Color.parseColor("#475467"));
        t.setPadding(0, dp(18), 0, dp(4));
        conteudo.addView(t);
    }

    private void cardResumo(String titulo, String valor, String cor) {
        TextView t = txt(titulo + "\n" + valor, 15, true);
        t.setTextColor(Color.parseColor(cor));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        t.setLayoutParams(p);
        conteudo.addView(t);
    }

    private void linhaValor(String rotulo, double valor, boolean destaque) {
        LinearLayout card = cardBase();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = txt(rotulo, 14, destaque);
        card.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView v = txt(moeda.format(valor), 14, true);
        v.setGravity(Gravity.END);
        card.addView(v);
        conteudo.addView(card);
    }

    private LinearLayout cardBase() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(11));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(p);
        return card;
    }

    private void vazio(String texto) {
        TextView t = txt(texto, 13, false);
        t.setTextColor(Color.parseColor("#667085"));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        conteudo.addView(t);
    }

    private String qtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "")
                .replace(".", ",");
    }
}
