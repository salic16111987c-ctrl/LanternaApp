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

public class RelatorioVendasProdutosActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat dataCurta = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt","BR"));
    private final SimpleDateFormat dataHora = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));

    private GestaoDbHelper db;
    private LinearLayout conteudo;
    private TextView periodoTexto;
    private Button abaResumo;
    private Button abaDetalhamento;
    private Button hojeBtn;
    private Button mesBtn;
    private Button mesAnteriorBtn;
    private Button anoBtn;
    private int periodoAtivo = 1;

    private long inicioAtual;
    private long fimAtual;
    private boolean detalhamento;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

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
        b.setTextSize(13);
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
        TechCellUi.applyWindowChrome(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        Button voltar = action("← Relatórios");
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView titulo = txt("Vendas de Produtos", 28, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);

        TextView sub = txt(
                "Mercadorias vendidas, custo e lucratividade • Alpha 41",
                14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        TextView aviso = txt(
                "Este relatório considera somente mercadorias. Serviços ficam fora desta apuração.",
                12, false);
        aviso.setTextColor(Color.parseColor("#475467"));
        aviso.setPadding(0, dp(8), 0, dp(8));
        root.addView(aviso);

        periodoTexto = txt("", 13, true);
        periodoTexto.setTextColor(TechCellUi.NAVY);
        periodoTexto.setBackground(TechCellUi.pillBackground(this));
        periodoTexto.setPadding(dp(12), dp(9), dp(12), dp(9));
        periodoTexto.setOnClickListener(v -> escolherPeriodo());
        root.addView(periodoTexto);

        LinearLayout f1 = new LinearLayout(this);
        f1.setOrientation(LinearLayout.HORIZONTAL);
        f1.setPadding(0, dp(7), 0, 0);

        hojeBtn = action("Hoje");
        hojeBtn.setTextSize(12);
        hojeBtn.setOnClickListener(v -> periodoHoje());
        f1.addView(hojeBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        mesBtn = action("Este mês");
        mesBtn.setTextSize(12);
        LinearLayout.LayoutParams mesLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        mesLp.setMargins(dp(6), 0, 0, 0);
        mesBtn.setOnClickListener(v -> periodoMesAtual());
        f1.addView(mesBtn, mesLp);
        root.addView(f1);

        LinearLayout f2 = new LinearLayout(this);
        f2.setOrientation(LinearLayout.HORIZONTAL);
        f2.setPadding(0, dp(5), 0, 0);

        mesAnteriorBtn = action("Mês anterior");
        mesAnteriorBtn.setTextSize(12);
        mesAnteriorBtn.setOnClickListener(v -> periodoMesAnterior());
        f2.addView(mesAnteriorBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        anoBtn = action("Este ano");
        anoBtn.setTextSize(12);
        LinearLayout.LayoutParams anoLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        anoLp.setMargins(dp(6), 0, 0, 0);
        anoBtn.setOnClickListener(v -> periodoAnoAtual());
        f2.addView(anoBtn, anoLp);
        root.addView(f2);

        TextView dicaPeriodo = txt("Toque no período acima para escolher outras datas.", 11, false);
        dicaPeriodo.setTextColor(TechCellUi.MUTED);
        dicaPeriodo.setPadding(dp(2), dp(5), 0, dp(8));
        root.addView(dicaPeriodo);

        LinearLayout abas = new LinearLayout(this);
        abas.setOrientation(LinearLayout.HORIZONTAL);

        abaResumo = action("Resumo");
        abaResumo.setTextSize(14);
        abaResumo.setOnClickListener(v -> selecionarAba(false));
        abas.addView(abaResumo, new LinearLayout.LayoutParams(0, dp(42), 1));

        abaDetalhamento = action("Detalhamento");
        abaDetalhamento.setTextSize(14);
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        detLp.setMargins(dp(6), 0, 0, 0);
        abaDetalhamento.setOnClickListener(v -> selecionarAba(true));
        abas.addView(abaDetalhamento, detLp);
        root.addView(abas);

        conteudo = new LinearLayout(this);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        root.addView(conteudo);

        setContentView(scroll);
    }

    private void selecionarAba(boolean abrirDetalhamento) {
        detalhamento = abrirDetalhamento;
        carregar();
    }

    private void periodoHoje() {
        periodoAtivo = 0;
        Calendar c = Calendar.getInstance();
        zerarHora(c);
        inicioAtual = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        fimAtual = c.getTimeInMillis();
        carregar();
    }

    private void periodoMesAtual() {
        periodoAtivo = 1;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        zerarHora(c);
        inicioAtual = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        fimAtual = c.getTimeInMillis();
        carregar();
    }

    private void periodoMesAnterior() {
        periodoAtivo = 2;
        Calendar fim = Calendar.getInstance();
        fim.set(Calendar.DAY_OF_MONTH, 1);
        zerarHora(fim);
        fimAtual = fim.getTimeInMillis();

        Calendar inicio = (Calendar) fim.clone();
        inicio.add(Calendar.MONTH, -1);
        inicioAtual = inicio.getTimeInMillis();
        carregar();
    }

    private void periodoAnoAtual() {
        periodoAtivo = 3;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        zerarHora(c);
        inicioAtual = c.getTimeInMillis();
        c.add(Calendar.YEAR, 1);
        fimAtual = c.getTimeInMillis();
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

                periodoAtivo = 4;
                inicioAtual = escolhidoIni.getTimeInMillis();
                fimAtual = escolhidoFim.getTimeInMillis();
                carregar();
            }, baseFim.get(Calendar.YEAR), baseFim.get(Calendar.MONTH),
                    baseFim.get(Calendar.DAY_OF_MONTH)).show();

        }, ini.get(Calendar.YEAR), ini.get(Calendar.MONTH), ini.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void zerarHora(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private void carregar() {
        if (conteudo == null || inicioAtual <= 0 || fimAtual <= inicioAtual) return;

        long fimInclusivo = fimAtual - 1;
        periodoTexto.setText("📅  Período: " +
                dataCurta.format(new Date(inicioAtual)) + " a " +
                dataCurta.format(new Date(fimInclusivo)) + "   ›");

        atualizarFiltrosPeriodo();
        atualizarAbas();

        conteudo.removeAllViews();
        if (detalhamento) carregarDetalhamento();
        else carregarResumo();
    }

    private void atualizarFiltrosPeriodo() {
        Button[] botoes = {hojeBtn, mesBtn, mesAnteriorBtn, anoBtn};
        for (int i = 0; i < botoes.length; i++) {
            if (botoes[i] == null) continue;
            if (i == periodoAtivo) TechCellUi.stylePrimary(this, botoes[i]);
            else TechCellUi.styleFilter(this, botoes[i]);
        }
    }

    private void atualizarAbas() {
        if (detalhamento) {
            TechCellUi.styleSecondary(this, abaResumo);
            TechCellUi.stylePrimary(this, abaDetalhamento, TechCellUi.NAVY);
        } else {
            TechCellUi.stylePrimary(this, abaResumo, TechCellUi.NAVY);
            TechCellUi.styleSecondary(this, abaDetalhamento);
        }
    }

    private void carregarResumo() {
        GestaoDbHelper.ResumoProdutosPeriodo r =
                db.resumoProdutosPeriodo(inicioAtual, fimAtual);

        LinearLayout hero = TechCellUi.card(this);
        hero.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        hero.setBackground(TechCellUi.solid(this,
                r.lucro >= 0 ? TechCellUi.PALE_GREEN : Color.parseColor("#FFF1F0"), 16));

        TextView h1 = txt("LUCRO BRUTO DAS MERCADORIAS", 11, true);
        h1.setTextColor(TechCellUi.MUTED);
        hero.addView(h1);

        TextView h2 = txt(moeda.format(r.lucro), 27, true);
        h2.setTextColor(r.lucro >= 0 ? TechCellUi.GREEN : TechCellUi.RED);
        h2.setPadding(0, dp(4), 0, 0);
        hero.addView(h2);

        TextView h3 = txt("Margem: " + percentual(r.margemPercentual()), 12, true);
        h3.setTextColor(TechCellUi.NAVY);
        h3.setPadding(0, dp(5), 0, 0);
        hero.addView(h3);
        hero.setOnClickListener(v -> selecionarAba(true));
        conteudo.addView(hero);

        addKpiRow(
                cardResumoBox("Total vendido", moeda.format(r.faturamento), TechCellUi.GREEN),
                cardResumoBox("Custo", moeda.format(r.custo), TechCellUi.TEXT));
        addKpiRow(
                cardResumoBox("Itens vendidos", qtd(r.quantidadeProdutos), TechCellUi.BLUE),
                cardResumoBox("Vendas", String.valueOf(r.quantidadeVendas), TechCellUi.BLUE));

        TextView dica = txt(
                "Toque no lucro acima para abrir o detalhamento dos itens vendidos.",
                11, false);
        dica.setTextColor(TechCellUi.MUTED);
        dica.setPadding(dp(8), dp(8), dp(8), dp(2));
        conteudo.addView(dica);

        secao("RESUMO POR PRODUTO");
        List<GestaoDbHelper.RelatorioProduto> produtos =
                db.produtosVendidosPeriodo(inicioAtual, fimAtual);

        if (produtos.isEmpty()) {
            vazio("Nenhuma mercadoria vendida neste período.");
            return;
        }

        for (GestaoDbHelper.RelatorioProduto p : produtos) {
            LinearLayout card = cardBase();

            TextView nome = txt(p.nome, 15, true);
            card.addView(nome);

            double margem = p.faturamento > 0 ? (p.lucro / p.faturamento) * 100.0 : 0.0;
            String unidade = unidade(p.unidade);

            TextView info = txt(
                    qtd(p.quantidade) + " " + unidade +
                            "   •   Vendido " + moeda.format(p.faturamento) +
                            "\nCusto " + moeda.format(p.custo) +
                            "   •   Lucro " + moeda.format(p.lucro) +
                            "   •   " + percentual(margem),
                    12, false);
            info.setTextColor(TechCellUi.MUTED);
            info.setPadding(0, dp(4), 0, 0);
            card.addView(info);

            conteudo.addView(card);
        }
    }

    private void carregarDetalhamento() {
        GestaoDbHelper.ResumoProdutosPeriodo r =
                db.resumoProdutosPeriodo(inicioAtual, fimAtual);
        List<GestaoDbHelper.RelatorioItemVendido> itens =
                db.itensProdutosVendidosPeriodo(inicioAtual, fimAtual);

        secao("ITENS VENDIDOS");

        if (itens.isEmpty()) {
            vazio("Nenhuma mercadoria vendida neste período.");
            return;
        }

        for (GestaoDbHelper.RelatorioItemVendido x : itens) {
            LinearLayout card = cardBase();

            TextView topo = txt(
                    dataHora.format(new Date(x.dataMillis)) +
                            "   •   Venda #" + db.numeroVendaExibicao(x.vendaId),
                    11, true);
            topo.setTextColor(Color.parseColor("#475467"));
            card.addView(topo);

            TextView nome = txt(x.nome, 15, true);
            nome.setPadding(0, dp(3), 0, dp(2));
            card.addView(nome);

            StringBuilder detalhes = new StringBuilder();
            detalhes.append("Quantidade: ")
                    .append(qtd(x.quantidade))
                    .append(" ")
                    .append(unidade(x.unidade))
                    .append("\nVenda unit.: ")
                    .append(moeda.format(x.precoUnitario))
                    .append("   •   Custo unit.: ")
                    .append(moeda.format(x.custoUnitario))
                    .append("\nTotal vendido: ")
                    .append(moeda.format(x.totalLiquido))
                    .append("   •   Custo: ")
                    .append(moeda.format(x.custoTotal))
                    .append("\nLucro: ")
                    .append(moeda.format(x.lucro));

            if (x.descontoRateio > 0.001) {
                detalhes.append("   •   Desconto: ")
                        .append(moeda.format(x.descontoRateio));
            }

            TextView info = txt(detalhes.toString(), 12, false);
            info.setTextColor(Color.parseColor("#667085"));
            card.addView(info);

            TextView abrir = txt("Toque para abrir o comprovante desta venda", 11, true);
            abrir.setTextColor(Color.parseColor("#175CD3"));
            abrir.setPadding(0, dp(5), 0, 0);
            card.addView(abrir);

            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ComprovanteVendaActivity.class);
                i.putExtra("venda_id", x.vendaId);
                startActivity(i);
            });

            conteudo.addView(card);
        }

        secao("TOTAL DO PERÍODO");
        linhaValor("Mercadorias vendidas", r.faturamento);
        linhaValor("Custo das mercadorias", r.custo);
        linhaValor("Lucro bruto", r.lucro);
        linhaTexto("Margem bruta", percentual(r.margemPercentual()));
        linhaTexto("Quantidade de itens", qtd(r.quantidadeProdutos));
        linhaTexto("Vendas com mercadorias", String.valueOf(r.quantidadeVendas));
    }

    private LinearLayout cardResumoBox(String titulo, String valor, int cor) {
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
        TextView t = txt(titulo, 13, true);
        t.setTextColor(Color.parseColor("#475467"));
        t.setPadding(0, dp(18), 0, dp(4));
        conteudo.addView(t);
    }

    private void linhaValor(String rotulo, double valor) {
        linhaTexto(rotulo, moeda.format(valor));
    }

    private void linhaTexto(String rotulo, String valor) {
        LinearLayout card = cardBase();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = txt(rotulo, 14, false);
        card.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView v = txt(valor, 14, true);
        v.setGravity(Gravity.END);
        card.addView(v);

        conteudo.addView(card);
    }

    private LinearLayout cardBase() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(11));
        card.setBackground(TechCellUi.cardBackground(this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(p);
        return card;
    }

    private void vazio(String texto) {
        TextView t = txt(texto, 13, false);
        t.setTextColor(Color.parseColor("#667085"));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(18), dp(8), dp(18));
        conteudo.addView(t);
    }

    private String unidade(String raw) {
        return raw == null || raw.trim().isEmpty() ? "UN" : raw.trim();
    }

    private String qtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) {
            return String.valueOf((long)Math.rint(v));
        }
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "")
                .replace(".", ",");
    }

    private String percentual(double v) {
        return String.format(new Locale("pt","BR"), "%.2f%%", v);
    }
}
