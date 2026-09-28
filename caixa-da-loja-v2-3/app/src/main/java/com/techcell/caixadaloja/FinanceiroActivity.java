package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

public class FinanceiroActivity extends Activity {
    private static final int REQ_XML_NFE = 4210;

    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));
    private final SimpleDateFormat dataCurta = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt","BR"));

    private GestaoDbHelper db;
    private LinearLayout resumoBox;
    private LinearLayout lista;
    private TextView periodoTexto;
    private Button abaResumoBtn;
    private Button abaProdutosBtn;
    private Button abaSaidasBtn;
    private Button hojeBtn;
    private Button mesBtn;
    private Button mesAnteriorBtn;
    private Button anoAtualBtn;
    private Button anoAnteriorBtn;
    private Button personalizadoBtn;
    private int periodoAtivo = 1;
    private int abaAtual = 0; // 0 resumo, 1 produtos, 2 saídas
    private boolean produtosDetalhamento;
    private Button novaDespesaBtn;
    private TextView saidasTitulo;
    private boolean mostrarSaidas;
    private long inicioAtual;
    private long fimAtual;

    private AlertDialog despesaDialog;
    private EditText descricaoForm;
    private EditText favorecidoForm;
    private EditText favorecidoDocForm;
    private EditText valorForm;
    private EditText docNumeroForm;
    private EditText docSerieForm;
    private EditText docChaveForm;
    private EditText docEmitenteForm;
    private EditText docEmitenteCnpjForm;
    private Spinner docTipoForm;
    private LinearLayout docDetalhesBox;
    private Button importarXmlBtn;

    private String xmlPendente = "";
    private String xmlUriPendente = "";
    private long xmlEmissaoPendente;
    private double xmlValorPendente;

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
        b.setTextSize(15);
        b.setMinHeight(dp(50));
        return b;
    }

    private EditText campo(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setInputType(inputType);
        return e;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);
        String abaInicial = getIntent().getStringExtra("aba_financeiro");
        if ("produtos".equalsIgnoreCase(abaInicial)) abaAtual = 1;
        else if ("saidas".equalsIgnoreCase(abaInicial)) abaAtual = 2;
        montar();
    }

    @Override protected void onResume() {
        super.onResume();
        if (lista != null) carregar();
    }

    private void montar() {
        TechCellUi.applyWindowChrome(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(36));
        scroll.addView(root);

        Button voltar = action("←  Voltar");
        voltar.setTextSize(14);
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView titulo = txt("Financeiro", 27, true);
        titulo.setPadding(0, dp(14), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Vendas, custos, despesas e documentos • Alpha 42", 12, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        periodoTexto = txt("", 13, true);
        periodoTexto.setTextColor(TechCellUi.NAVY);
        periodoTexto.setBackground(TechCellUi.pillBackground(this));
        periodoTexto.setPadding(dp(12), dp(9), dp(12), dp(9));
        periodoTexto.setOnClickListener(v -> escolherPeriodo());
        root.addView(periodoTexto, TechCellUi.fullCardParams(this, 10));

        LinearLayout filtros1 = new LinearLayout(this);
        filtros1.setOrientation(LinearLayout.HORIZONTAL);
        filtros1.setPadding(0, dp(7), 0, 0);

        hojeBtn = action("Hoje");
        hojeBtn.setTextSize(12);
        hojeBtn.setOnClickListener(v -> periodoHoje());
        filtros1.addView(hojeBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        mesBtn = action("Este mês");
        mesBtn.setTextSize(12);
        mesBtn.setOnClickListener(v -> periodoMesAtual());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(40), 1);
        mp.setMargins(dp(5),0,0,0);
        filtros1.addView(mesBtn, mp);

        mesAnteriorBtn = action("Mês anterior");
        mesAnteriorBtn.setTextSize(12);
        mesAnteriorBtn.setOnClickListener(v -> periodoMesAnterior());
        LinearLayout.LayoutParams map = new LinearLayout.LayoutParams(0, dp(40), 1);
        map.setMargins(dp(5),0,0,0);
        filtros1.addView(mesAnteriorBtn, map);
        root.addView(filtros1);

        LinearLayout filtros2 = new LinearLayout(this);
        filtros2.setOrientation(LinearLayout.HORIZONTAL);
        filtros2.setPadding(0, dp(5), 0, 0);

        anoAtualBtn = action("Este ano");
        anoAtualBtn.setTextSize(12);
        anoAtualBtn.setOnClickListener(v -> periodoAnoAtual());
        filtros2.addView(anoAtualBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        anoAnteriorBtn = action("Ano anterior");
        anoAnteriorBtn.setTextSize(12);
        anoAnteriorBtn.setOnClickListener(v -> periodoAnoAnterior());
        LinearLayout.LayoutParams aap = new LinearLayout.LayoutParams(0, dp(40), 1);
        aap.setMargins(dp(5),0,0,0);
        filtros2.addView(anoAnteriorBtn, aap);

        personalizadoBtn = action("📅 Outro");
        personalizadoBtn.setTextSize(12);
        personalizadoBtn.setOnClickListener(v -> escolherPeriodo());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(40), 1);
        pp.setMargins(dp(5),0,0,0);
        filtros2.addView(personalizadoBtn, pp);
        root.addView(filtros2);

        LinearLayout abas = new LinearLayout(this);
        abas.setOrientation(LinearLayout.HORIZONTAL);
        abas.setPadding(0, dp(8), 0, 0);

        abaResumoBtn = action("Resumo");
        abaResumoBtn.setTextSize(13);
        abaResumoBtn.setOnClickListener(v -> {
            abaAtual = 0;
            carregar();
        });
        abas.addView(abaResumoBtn, new LinearLayout.LayoutParams(0, dp(42), 1));

        abaProdutosBtn = action("Produtos");
        abaProdutosBtn.setTextSize(13);
        abaProdutosBtn.setOnClickListener(v -> {
            abaAtual = 1;
            carregar();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1);
        ap.setMargins(dp(5),0,0,0);
        abas.addView(abaProdutosBtn, ap);

        abaSaidasBtn = action("Saídas");
        abaSaidasBtn.setTextSize(13);
        abaSaidasBtn.setOnClickListener(v -> {
            abaAtual = 2;
            carregar();
        });
        LinearLayout.LayoutParams asp = new LinearLayout.LayoutParams(0, dp(42), 1);
        asp.setMargins(dp(5),0,0,0);
        abas.addView(abaSaidasBtn, asp);
        root.addView(abas);

        resumoBox = new LinearLayout(this);
        resumoBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resumoBox);

        novaDespesaBtn = action("+ Registrar saída / despesa");
        TechCellUi.stylePrimary(this, novaDespesaBtn);
        novaDespesaBtn.setOnClickListener(v -> novaDespesa());
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        np.setMargins(0, dp(12), 0, dp(6));
        root.addView(novaDespesaBtn, np);

        saidasTitulo = txt("Saídas registradas", 17, true);
        saidasTitulo.setPadding(0, dp(10), 0, dp(4));
        root.addView(saidasTitulo);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        setContentView(scroll);
        periodoMesAtual();
    }

    private void zerarHora(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
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

        Calendar ini = (Calendar) fim.clone();
        ini.add(Calendar.MONTH, -1);
        inicioAtual = ini.getTimeInMillis();
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

    private void periodoAnoAnterior() {
        periodoAtivo = 4;
        Calendar fim = Calendar.getInstance();
        fim.set(Calendar.MONTH, Calendar.JANUARY);
        fim.set(Calendar.DAY_OF_MONTH, 1);
        zerarHora(fim);
        fimAtual = fim.getTimeInMillis();

        Calendar ini = (Calendar) fim.clone();
        ini.add(Calendar.YEAR, -1);
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
            long baseMillis = fimAtual > inicioAtual
                    ? fimAtual - 1
                    : System.currentTimeMillis();
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

                periodoAtivo = 5;
                inicioAtual = escolhidoIni.getTimeInMillis();
                fimAtual = escolhidoFim.getTimeInMillis();
                carregar();
            }, baseFim.get(Calendar.YEAR), baseFim.get(Calendar.MONTH),
                    baseFim.get(Calendar.DAY_OF_MONTH)).show();

        }, ini.get(Calendar.YEAR), ini.get(Calendar.MONTH),
                ini.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void carregar() {
        if (inicioAtual <= 0 || fimAtual <= inicioAtual) return;

        long fimInclusivo = fimAtual - 1;
        periodoTexto.setText("📅  Período: " +
                dataCurta.format(new Date(inicioAtual)) + " a " +
                dataCurta.format(new Date(fimInclusivo)) + "   ›");

        atualizarFiltrosPeriodo();
        resumoBox.removeAllViews();
        lista.removeAllViews();

        if (abaAtual == 0) carregarResumoFinanceiro();
        else if (abaAtual == 1) carregarProdutosFinanceiro();
        else carregarSaidasFinanceiro();

        atualizarAbasFinanceiro();
    }

    private void carregarResumoFinanceiro() {
        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);

        double margemLiquida = r.vendas.total > 0
                ? (r.lucroLiquido / r.vendas.total) * 100.0 : 0.0;

        LinearLayout hero = TechCellUi.card(this);
        hero.setBackground(TechCellUi.solid(this,
                r.lucroLiquido >= 0
                        ? TechCellUi.PALE_GREEN
                        : Color.parseColor("#FFF1F0"), 16));
        hero.setLayoutParams(TechCellUi.fullCardParams(this, 10));

        TextView heroTitulo = txt("LUCRO LÍQUIDO", 12, true);
        heroTitulo.setTextColor(TechCellUi.MUTED);
        hero.addView(heroTitulo);

        TextView heroValor = txt(moeda.format(r.lucroLiquido), 28, true);
        heroValor.setTextColor(r.lucroLiquido >= 0 ? TechCellUi.GREEN : TechCellUi.RED);
        heroValor.setPadding(0, dp(4), 0, 0);
        hero.addView(heroValor);

        TextView heroMargem = txt(
                "Margem líquida: " +
                        String.format(new Locale("pt","BR"), "%.2f%%", margemLiquida),
                12, true);
        heroMargem.setTextColor(TechCellUi.NAVY);
        heroMargem.setPadding(0, dp(5), 0, 0);
        hero.addView(heroMargem);

        resumoBox.addView(hero);

        adicionarLinhaResumo(
                cardResumo("Total vendido", moeda.format(r.vendas.total), "#07884B"),
                cardResumo("Lucro bruto", moeda.format(r.vendas.lucro), "#176240"));
        adicionarLinhaResumo(
                cardResumo("Custo vendido", moeda.format(r.vendas.custo), "#475467"),
                cardResumo("Despesas", moeda.format(r.despesasOperacionais), "#B42318"));
        adicionarLinhaResumo(
                cardResumo("Vendas", r.vendas.quantidadeVendas + " venda(s)", "#175CD3"),
                cardResumo("Descontos", moeda.format(r.vendas.desconto), "#B54708"));

        if (r.outrasSaidas > 0.001 || r.comprasEstoque > 0.001) {
            adicionarLinhaResumo(
                    cardResumo("Outras saídas", moeda.format(r.outrasSaidas),
                            r.outrasSaidas > 0.001 ? "#B42318" : "#667085"),
                    cardResumo("Compras p/ estoque", moeda.format(r.comprasEstoque),
                            r.comprasEstoque > 0.001 ? "#B54708" : "#667085"));
        }

        LinearLayout recebimentos = TechCellUi.card(this);
        recebimentos.setLayoutParams(TechCellUi.fullCardParams(this, 8));

        TextView recebTitulo = txt("RECEBIMENTOS", 11, true);
        recebTitulo.setTextColor(TechCellUi.MUTED);
        recebimentos.addView(recebTitulo);

        LinearLayout formas = new LinearLayout(this);
        formas.setOrientation(LinearLayout.HORIZONTAL);
        formas.setPadding(0, dp(7), 0, 0);
        formas.addView(recebimentoItem("Dinheiro", moeda.format(r.vendas.dinheiro), TechCellUi.GREEN),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        formas.addView(recebimentoItem("PIX", moeda.format(r.vendas.pix), TechCellUi.BLUE),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        formas.addView(recebimentoItem("Cartão", moeda.format(r.vendas.cartao), TechCellUi.NAVY),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        recebimentos.addView(formas);
        resumoBox.addView(recebimentos);
    }

    private void carregarProdutosFinanceiro() {
        LinearLayout subAbas = new LinearLayout(this);
        subAbas.setOrientation(LinearLayout.HORIZONTAL);
        subAbas.setPadding(0, dp(8), 0, 0);

        Button resumoProdutos = action("Resumo de produtos");
        resumoProdutos.setTextSize(12);
        resumoProdutos.setOnClickListener(v -> {
            produtosDetalhamento = false;
            carregar();
        });

        Button detalhesProdutos = action("Detalhamento");
        detalhesProdutos.setTextSize(12);
        detalhesProdutos.setOnClickListener(v -> {
            produtosDetalhamento = true;
            carregar();
        });

        if (produtosDetalhamento) {
            TechCellUi.styleSecondary(this, resumoProdutos);
            TechCellUi.stylePrimary(this, detalhesProdutos, TechCellUi.NAVY);
        } else {
            TechCellUi.stylePrimary(this, resumoProdutos, TechCellUi.NAVY);
            TechCellUi.styleSecondary(this, detalhesProdutos);
        }

        subAbas.addView(resumoProdutos, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(0, dp(40), 1);
        dpv.setMargins(dp(5),0,0,0);
        subAbas.addView(detalhesProdutos, dpv);
        resumoBox.addView(subAbas);

        if (produtosDetalhamento) carregarProdutosDetalhamento();
        else carregarProdutosResumo();
    }

    private void carregarProdutosResumo() {
        GestaoDbHelper.ResumoProdutosPeriodo r =
                db.resumoProdutosPeriodo(inicioAtual, fimAtual);

        LinearLayout hero = TechCellUi.card(this);
        hero.setLayoutParams(TechCellUi.fullCardParams(this, 8));
        hero.setBackground(TechCellUi.solid(this,
                r.lucro >= 0 ? TechCellUi.PALE_GREEN : Color.parseColor("#FFF1F0"), 16));

        TextView h1 = txt("LUCRO BRUTO DAS MERCADORIAS", 11, true);
        h1.setTextColor(TechCellUi.MUTED);
        hero.addView(h1);

        TextView h2 = txt(moeda.format(r.lucro), 27, true);
        h2.setTextColor(r.lucro >= 0 ? TechCellUi.GREEN : TechCellUi.RED);
        h2.setPadding(0, dp(4), 0, 0);
        hero.addView(h2);

        TextView h3 = txt("Margem: " + produtoPercentual(r.margemPercentual()), 12, true);
        h3.setTextColor(TechCellUi.NAVY);
        h3.setPadding(0, dp(5), 0, 0);
        hero.addView(h3);
        hero.setOnClickListener(v -> {
            produtosDetalhamento = true;
            carregar();
        });
        resumoBox.addView(hero);

        adicionarLinhaResumo(
                cardResumo("Total vendido", moeda.format(r.faturamento), "#07884B"),
                cardResumo("Custo", moeda.format(r.custo), "#475467"));
        adicionarLinhaResumo(
                cardResumo("Itens vendidos", produtoQtd(r.quantidadeProdutos), "#175CD3"),
                cardResumo("Vendas", String.valueOf(r.quantidadeVendas), "#175CD3"));

        TextView secao = txt("RESUMO POR PRODUTO", 12, true);
        secao.setTextColor(TechCellUi.MUTED);
        secao.setPadding(0, dp(14), 0, dp(4));
        resumoBox.addView(secao);

        List<GestaoDbHelper.RelatorioProduto> produtos =
                db.produtosVendidosPeriodo(inicioAtual, fimAtual);
        if (produtos.isEmpty()) {
            produtoVazio("Nenhuma mercadoria vendida neste período.");
            return;
        }

        if (produtos.size() >= 100) {
            TextView limite = txt(
                    "Exibindo os 100 produtos com maior faturamento. Os totais acima consideram todo o período.",
                    11, false);
            limite.setTextColor(TechCellUi.MUTED);
            limite.setPadding(dp(6), dp(4), dp(6), dp(8));
            resumoBox.addView(limite);
        }

        for (GestaoDbHelper.RelatorioProduto p : produtos) {
            LinearLayout card = produtoCardBase();
            TextView nome = txt(p.nome, 15, true);
            card.addView(nome);

            double margem = p.faturamento > 0 ? (p.lucro / p.faturamento) * 100.0 : 0.0;
            TextView info = txt(
                    produtoQtd(p.quantidade) + " " + produtoUnidade(p.unidade) +
                            "   •   Vendido " + moeda.format(p.faturamento) +
                            "\nCusto " + moeda.format(p.custo) +
                            "   •   Lucro " + moeda.format(p.lucro) +
                            "   •   " + produtoPercentual(margem),
                    12, false);
            info.setTextColor(TechCellUi.MUTED);
            info.setPadding(0, dp(4), 0, 0);
            card.addView(info);
            resumoBox.addView(card);
        }
    }

    private void carregarProdutosDetalhamento() {
        GestaoDbHelper.ResumoProdutosPeriodo r =
                db.resumoProdutosPeriodo(inicioAtual, fimAtual);
        List<GestaoDbHelper.RelatorioItemVendido> itens =
                db.itensProdutosVendidosPeriodo(inicioAtual, fimAtual);

        TextView secao = txt("ITENS VENDIDOS", 12, true);
        secao.setTextColor(TechCellUi.MUTED);
        secao.setPadding(0, dp(14), 0, dp(4));
        resumoBox.addView(secao);

        if (itens.isEmpty()) {
            produtoVazio("Nenhuma mercadoria vendida neste período.");
            return;
        }

        if (itens.size() >= 150) {
            TextView limite = txt(
                    "Exibindo os 150 itens vendidos mais recentes. Os totais abaixo consideram todo o período.",
                    11, false);
            limite.setTextColor(TechCellUi.MUTED);
            limite.setPadding(dp(6), dp(4), dp(6), dp(8));
            resumoBox.addView(limite);
        }

        for (GestaoDbHelper.RelatorioItemVendido x : itens) {
            LinearLayout card = produtoCardBase();

            TextView topo = txt(
                    data.format(new Date(x.dataMillis)) +
                            "   •   Venda #" + db.numeroVendaExibicao(x.vendaId),
                    11, true);
            topo.setTextColor(Color.parseColor("#475467"));
            card.addView(topo);

            TextView nome = txt(x.nome, 15, true);
            nome.setPadding(0, dp(3), 0, dp(2));
            card.addView(nome);

            StringBuilder detalhes = new StringBuilder();
            detalhes.append("Quantidade: ")
                    .append(produtoQtd(x.quantidade)).append(" ").append(produtoUnidade(x.unidade))
                    .append("\nVenda unit.: ").append(moeda.format(x.precoUnitario))
                    .append("   •   Custo unit.: ").append(moeda.format(x.custoUnitario))
                    .append("\nTotal vendido: ").append(moeda.format(x.totalLiquido))
                    .append("   •   Custo: ").append(moeda.format(x.custoTotal))
                    .append("\nLucro: ").append(moeda.format(x.lucro));

            if (x.descontoRateio > 0.001) {
                detalhes.append("   •   Desconto: ").append(moeda.format(x.descontoRateio));
            }

            TextView info = txt(detalhes.toString(), 12, false);
            info.setTextColor(TechCellUi.MUTED);
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

            resumoBox.addView(card);
        }

        TextView total = txt("TOTAL DO PERÍODO", 12, true);
        total.setTextColor(TechCellUi.MUTED);
        total.setPadding(0, dp(14), 0, dp(4));
        resumoBox.addView(total);
        produtoLinhaTexto("Mercadorias vendidas", moeda.format(r.faturamento));
        produtoLinhaTexto("Custo das mercadorias", moeda.format(r.custo));
        produtoLinhaTexto("Lucro bruto", moeda.format(r.lucro));
        produtoLinhaTexto("Margem bruta", produtoPercentual(r.margemPercentual()));
        produtoLinhaTexto("Quantidade de itens", produtoQtd(r.quantidadeProdutos));
        produtoLinhaTexto("Vendas com mercadorias", String.valueOf(r.quantidadeVendas));
    }

    private void carregarSaidasFinanceiro() {
        List<GestaoDbHelper.Despesa> despesas = db.listDespesas(inicioAtual, fimAtual, 120);
        if (despesas.isEmpty()) {
            TextView vazio = txt("Nenhuma saída registrada neste período.", 14, false);
            vazio.setTextColor(TechCellUi.MUTED);
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(22), 0, dp(22));
            lista.addView(vazio);
            return;
        }

        if (despesas.size() >= 120) {
            TextView limite = txt(
                    "Exibindo as 120 saídas mais recentes deste período.",
                    11, false);
            limite.setTextColor(TechCellUi.MUTED);
            limite.setPadding(dp(6), dp(6), dp(6), dp(8));
            lista.addView(limite);
        }

        for (GestaoDbHelper.Despesa d : despesas) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(TechCellUi.cardBackground(this));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(5), 0, dp(5));
            card.setLayoutParams(cp);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView nome = txt(d.descricao, 16, true);
            top.addView(nome, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView valor = txt(moeda.format(d.valor), 16, true);
            valor.setTextColor("CANCELADA".equalsIgnoreCase(d.status)
                    ? TechCellUi.MUTED : TechCellUi.RED);
            top.addView(valor);
            card.addView(top);

            String tipo = "COMPRA_ESTOQUE".equalsIgnoreCase(d.tipo)
                    ? "Compra para estoque"
                    : "OUTRA_SAIDA".equalsIgnoreCase(d.tipo)
                    ? "Outra saída"
                    : "Despesa operacional";

            String doc = rotuloDocumento(d.documentoTipo);
            TextView meta = txt(data.format(new Date(d.dataMillis)) + " • " + tipo +
                    (d.categoria == null || d.categoria.isEmpty() ? "" : " • " + d.categoria) +
                    (doc.isEmpty() ? "" : "\nDocumento: " + doc),
                    12, false);
            meta.setTextColor(TechCellUi.MUTED);
            card.addView(meta);

            if (d.favorecidoNome != null && !d.favorecidoNome.trim().isEmpty()) {
                TextView fav = txt("Favorecido: " + d.favorecidoNome.trim(), 12, false);
                fav.setTextColor(Color.parseColor("#475467"));
                card.addView(fav);
            }

            if ("CANCELADA".equalsIgnoreCase(d.status)) {
                TextView st = txt("CANCELADA" +
                        (d.cancelamentoMotivo == null || d.cancelamentoMotivo.isEmpty()
                                ? "" : " • " + d.cancelamentoMotivo), 12, true);
                st.setTextColor(TechCellUi.RED);
                card.addView(st);
            } else {
                card.setOnClickListener(v -> abrirDespesa(d));
            }
            lista.addView(card);
        }
    }

    private LinearLayout produtoCardBase() {
        LinearLayout card = TechCellUi.card(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(p);
        return card;
    }

    private void produtoVazio(String texto) {
        TextView t = txt(texto, 13, false);
        t.setTextColor(TechCellUi.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(18), dp(8), dp(18));
        resumoBox.addView(t);
    }

    private void produtoLinhaTexto(String rotulo, String valor) {
        LinearLayout card = produtoCardBase();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = txt(rotulo, 14, false);
        card.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView v = txt(valor, 14, true);
        v.setGravity(Gravity.END);
        card.addView(v);
        resumoBox.addView(card);
    }

    private String produtoQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "")
                .replace(".", ",");
    }

    private String produtoUnidade(String unidade) {
        if (unidade == null || unidade.trim().isEmpty()) return "UN";
        return unidade.trim().toUpperCase(new Locale("pt","BR"));
    }

    private String produtoPercentual(double v) {
        return String.format(new Locale("pt","BR"), "%.2f%%", v);
    }

    private void atualizarFiltrosPeriodo() {
        Button[] botoes = {
                hojeBtn, mesBtn, mesAnteriorBtn,
                anoAtualBtn, anoAnteriorBtn, personalizadoBtn
        };
        for (int i=0; i<botoes.length; i++) {
            if (botoes[i] == null) continue;
            if (i == periodoAtivo) TechCellUi.stylePrimary(this, botoes[i]);
            else TechCellUi.styleFilter(this, botoes[i]);
        }
    }

    private LinearLayout recebimentoItem(String titulo, String valor, int cor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(2), dp(4), dp(2));

        TextView t = txt(titulo, 10, false);
        t.setTextColor(TechCellUi.MUTED);
        box.addView(t);

        TextView v = txt(valor, 12, true);
        v.setTextColor(cor);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        return box;
    }

    private void atualizarAbasFinanceiro() {
        Button[] abas = {abaResumoBtn, abaProdutosBtn, abaSaidasBtn};
        for (int i = 0; i < abas.length; i++) {
            if (abas[i] == null) continue;
            if (i == abaAtual) TechCellUi.stylePrimary(this, abas[i], TechCellUi.NAVY);
            else TechCellUi.styleSecondary(this, abas[i]);
        }

        boolean saidas = abaAtual == 2;
        resumoBox.setVisibility(saidas ? View.GONE : View.VISIBLE);
        novaDespesaBtn.setVisibility(saidas ? View.VISIBLE : View.GONE);
        saidasTitulo.setVisibility(saidas ? View.VISIBLE : View.GONE);
        lista.setVisibility(saidas ? View.VISIBLE : View.GONE);
    }

    private LinearLayout cardResumo(String titulo, String valor, String cor) {
        LinearLayout card = TechCellUi.card(this);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView t = txt(titulo, 11, true);
        t.setTextColor(TechCellUi.MUTED);
        card.addView(t);
        TextView v = txt(valor, 17, true);
        v.setTextColor(Color.parseColor(cor));
        v.setPadding(0, dp(3), 0, 0);
        card.addView(v);
        return card;
    }

    private void adicionarLinhaResumo(LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(rp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ap.setMargins(0,0,dp(4),0);
        row.addView(a, ap);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bp.setMargins(dp(4),0,0,0);
        row.addView(b, bp);
        resumoBox.addView(row);
    }

    private void novaDespesa() {
        xmlPendente = "";
        xmlUriPendente = "";
        xmlEmissaoPendente = 0;
        xmlValorPendente = 0;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(12));
        sv.addView(box);

        TextView aviso = txt(
                "Compra para estoque não é descontada novamente do lucro líquido. " +
                "O documento pode ser um recibo ou uma nota recebida.",
                12, true);
        aviso.setTextColor(Color.parseColor("#B54708"));
        aviso.setPadding(0,0,0,dp(8));
        box.addView(aviso);

        descricaoForm = campo("Descrição *", InputType.TYPE_CLASS_TEXT);
        box.addView(descricaoForm);

        EditText categoria = campo("Categoria (ex.: aluguel, energia, frete)", InputType.TYPE_CLASS_TEXT);
        box.addView(categoria);

        Spinner tipo = new Spinner(this);
        String[] tipos = {"Despesa operacional", "Compra para estoque", "Outra saída"};
        ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tipo.setAdapter(ta);
        box.addView(tipo);

        Spinner forma = new Spinner(this);
        String[] formas = {"Dinheiro", "PIX", "Cartão", "Boleto", "Transferência", "Outro"};
        ArrayAdapter<String> fa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formas);
        fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        forma.setAdapter(fa);
        box.addView(forma);

        valorForm = campo("Valor R$ *",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(valorForm);

        favorecidoForm = campo("Favorecido / fornecedor", InputType.TYPE_CLASS_TEXT);
        box.addView(favorecidoForm);

        favorecidoDocForm = campo("CPF/CNPJ do favorecido", InputType.TYPE_CLASS_NUMBER);
        CadastroBrasilUtils.aplicarMascaraDocumento(
                favorecidoDocForm,
                () -> CadastroBrasilUtils.apenasDigitos(
                        favorecidoDocForm.getText().toString()).length() > 11);
        box.addView(favorecidoDocForm);

        TextView docTitulo = txt("DOCUMENTO DA DESPESA", 13, true);
        docTitulo.setTextColor(Color.parseColor("#475467"));
        docTitulo.setPadding(0, dp(14), 0, dp(4));
        box.addView(docTitulo);

        docTipoForm = new Spinner(this);
        String[] docs = {
                "Recibo",
                "NF-e recebida",
                "NFC-e / Cupom",
                "Boleto",
                "Outro",
                "Sem documento"
        };
        ArrayAdapter<String> da = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, docs);
        da.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        docTipoForm.setAdapter(da);
        box.addView(docTipoForm);

        docDetalhesBox = new LinearLayout(this);
        docDetalhesBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(docDetalhesBox);

        importarXmlBtn = action("Importar XML da NF-e");
        importarXmlBtn.setVisibility(View.GONE);
        importarXmlBtn.setOnClickListener(v -> escolherXmlNfe());
        docDetalhesBox.addView(importarXmlBtn);

        docNumeroForm = campo("Número do documento / NF-e", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docNumeroForm);

        docSerieForm = campo("Série (quando houver)", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docSerieForm);

        docChaveForm = campo("Chave de acesso da NF-e (44 dígitos)", InputType.TYPE_CLASS_NUMBER);
        docDetalhesBox.addView(docChaveForm);

        docEmitenteForm = campo("Emitente da nota", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docEmitenteForm);

        docEmitenteCnpjForm = campo("CNPJ do emitente", InputType.TYPE_CLASS_NUMBER);
        CadastroBrasilUtils.aplicarMascaraDocumento(docEmitenteCnpjForm, () -> true);
        docDetalhesBox.addView(docEmitenteCnpjForm);

        EditText obs = campo("Observação", InputType.TYPE_CLASS_TEXT);
        box.addView(obs);

        docTipoForm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean sem = pos == 5;
                boolean nfe = pos == 1;
                docDetalhesBox.setVisibility(sem ? View.GONE : View.VISIBLE);
                importarXmlBtn.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docChaveForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docSerieForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docEmitenteForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docEmitenteCnpjForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        despesaDialog = new AlertDialog.Builder(this)
                .setTitle("Registrar saída")
                .setView(sv)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        despesaDialog.setOnDismissListener(x -> limparFormularioDocumento());
        despesaDialog.setOnShowListener(x ->
                despesaDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String desc = descricaoForm.getText().toString().trim();
                            if (desc.isEmpty()) {
                                descricaoForm.setError("Informe a descrição");
                                return;
                            }

                            double vl = numero(valorForm.getText().toString());
                            if (Double.isNaN(vl) || vl <= 0) {
                                valorForm.setError("Informe um valor válido");
                                return;
                            }

                            String favDoc = CadastroBrasilUtils.apenasDigitos(
                                    favorecidoDocForm.getText().toString());
                            if (!favDoc.isEmpty()) {
                                boolean ok = favDoc.length() == 11
                                        ? CadastroBrasilUtils.cpfValido(favDoc)
                                        : favDoc.length() == 14 && CadastroBrasilUtils.cnpjValido(favDoc);
                                if (!ok) {
                                    favorecidoDocForm.setError("CPF/CNPJ inválido");
                                    return;
                                }
                            }

                            String docTipo = codigoDocumento(docTipoForm.getSelectedItemPosition());
                            String chave = CadastroBrasilUtils.apenasDigitos(
                                    docChaveForm.getText().toString());
                            if ("NFE_RECEBIDA".equals(docTipo) && !chave.isEmpty() && chave.length() != 44) {
                                docChaveForm.setError("A chave da NF-e deve ter 44 dígitos");
                                return;
                            }

                            String emitCnpj = CadastroBrasilUtils.apenasDigitos(
                                    docEmitenteCnpjForm.getText().toString());
                            if ("NFE_RECEBIDA".equals(docTipo) && !emitCnpj.isEmpty()
                                    && !CadastroBrasilUtils.cnpjValido(emitCnpj)) {
                                docEmitenteCnpjForm.setError("CNPJ do emitente inválido");
                                return;
                            }

                            GestaoDbHelper.Despesa d = new GestaoDbHelper.Despesa();
                            d.dataMillis = System.currentTimeMillis();
                            d.descricao = desc;
                            d.categoria = categoria.getText().toString().trim();
                            d.tipo = tipo.getSelectedItemPosition() == 1
                                    ? "COMPRA_ESTOQUE"
                                    : tipo.getSelectedItemPosition() == 2
                                    ? "OUTRA_SAIDA" : "OPERACIONAL";
                            d.formaPagamento = forma.getSelectedItem().toString();
                            d.valor = vl;
                            d.observacao = obs.getText().toString().trim();
                            d.favorecidoNome = favorecidoForm.getText().toString().trim();
                            d.favorecidoDocumento = favDoc;
                            d.documentoTipo = docTipo;
                            d.documentoNumero = docNumeroForm.getText().toString().trim();
                            d.documentoSerie = docSerieForm.getText().toString().trim();
                            d.documentoChave = chave;
                            d.documentoEmissaoMillis = xmlEmissaoPendente;
                            d.documentoEmitenteNome = docEmitenteForm.getText().toString().trim();
                            d.documentoEmitenteCnpj = emitCnpj;
                            d.documentoValor = xmlValorPendente > 0 ? xmlValorPendente : vl;
                            d.documentoXml = xmlPendente;
                            d.documentoUri = xmlUriPendente;

                            db.saveDespesa(d);
                            despesaDialog.dismiss();
                            carregar();
                            Toast.makeText(this, "Saída registrada.", Toast.LENGTH_SHORT).show();
                        }));
        despesaDialog.show();
    }

    private void escolherXmlNfe() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/xml", "text/xml", "text/plain"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_XML_NFE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode != REQ_XML_NFE || resultCode != RESULT_OK || intent == null) return;
        Uri uri = intent.getData();
        if (uri == null) return;

        try {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            byte[] bytes = lerTudo(uri);
            DocumentoNfe x = lerNfe(bytes);

            if (!formularioDocumentoDisponivel()) {
                Toast.makeText(this,
                        "A tela foi recarregada pelo Android. Abra novamente 'Registrar saída' e importe o XML.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            xmlPendente = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            xmlUriPendente = uri.toString();
            xmlEmissaoPendente = x.emissaoMillis;
            xmlValorPendente = x.valor;

            docTipoForm.setSelection(1);
            docNumeroForm.setText(x.numero);
            docSerieForm.setText(x.serie);
            docChaveForm.setText(x.chave);
            docEmitenteForm.setText(x.emitenteNome);
            docEmitenteCnpjForm.setText(x.emitenteCnpj);

            if (favorecidoForm.getText().toString().trim().isEmpty()) {
                favorecidoForm.setText(x.emitenteNome);
            }
            if (CadastroBrasilUtils.apenasDigitos(
                    favorecidoDocForm.getText().toString()).isEmpty()) {
                favorecidoDocForm.setText(x.emitenteCnpj);
            }
            if (x.valor > 0) {
                valorForm.setText(String.format(Locale.US, "%.2f", x.valor).replace(".", ","));
            }
            if (descricaoForm.getText().toString().trim().isEmpty()) {
                descricaoForm.setText("NF-e " + (x.numero.isEmpty() ? "recebida" : "nº " + x.numero));
            }

            Toast.makeText(this,
                    "XML importado. Confira os dados antes de salvar.",
                    Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this,
                    e.getMessage() == null || e.getMessage().trim().isEmpty()
                            ? "Este arquivo não é uma NF-e válida."
                            : e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Não foi possível ler este XML. Se for uma NF-e real, tente novamente ou envie o arquivo para conferência.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean formularioDocumentoDisponivel() {
        return docTipoForm != null &&
                docNumeroForm != null &&
                docSerieForm != null &&
                docChaveForm != null &&
                docEmitenteForm != null &&
                docEmitenteCnpjForm != null &&
                favorecidoForm != null &&
                favorecidoDocForm != null &&
                valorForm != null &&
                descricaoForm != null;
    }

    private byte[] lerTudo(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Arquivo indisponível.");
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("O XML é muito grande para importação.");
                }
                out.write(buffer, 0, n);
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("O arquivo XML está vazio.");
            }
            return bytes;
        }
    }

    private DocumentoNfe lerNfe(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("O arquivo XML está vazio.");
        }

        String inicio = new String(
                bytes, 0, Math.min(bytes.length, 4096),
                java.nio.charset.StandardCharsets.UTF_8);
        if (inicio.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("XML não aceito por segurança.");
        }

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        try { f.setXIncludeAware(false); } catch (Exception ignored) {}
        try { f.setExpandEntityReferences(false); } catch (Exception ignored) {}

        Document doc;
        try {
            doc = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("O arquivo não contém um XML válido.");
        }

        if (doc == null || doc.getDocumentElement() == null) {
            throw new IllegalArgumentException("O arquivo não contém uma NF-e.");
        }
        doc.getDocumentElement().normalize();

        Element inf = primeiro(doc, "infNFe");
        Element ide = primeiro(doc, "ide");
        Element emit = primeiro(doc, "emit");
        Element total = primeiro(doc, "ICMSTot");

        if (inf == null || ide == null || emit == null || total == null) {
            throw new IllegalArgumentException("Este XML não possui a estrutura de uma NF-e.");
        }

        String modelo = texto(ide, "mod");
        if ("65".equals(modelo)) {
            throw new IllegalArgumentException("Este XML é de NFC-e (modelo 65). Selecione NFC-e / Cupom.");
        }
        if (!"55".equals(modelo)) {
            throw new IllegalArgumentException("Este XML não é uma NF-e modelo 55.");
        }

        DocumentoNfe x = new DocumentoNfe();

        String id = inf.getAttribute("Id");
        if (id != null && !id.trim().isEmpty()) {
            x.chave = CadastroBrasilUtils.apenasDigitos(id);
        }
        if (x.chave.length() != 44) {
            Element prot = primeiro(doc, "infProt");
            if (prot != null) {
                x.chave = CadastroBrasilUtils.apenasDigitos(texto(prot, "chNFe"));
            }
        }
        if (x.chave.length() != 44) {
            throw new IllegalArgumentException("A NF-e não possui uma chave de acesso válida com 44 dígitos.");
        }

        x.numero = texto(ide, "nNF");
        x.serie = texto(ide, "serie");
        if (x.numero.isEmpty()) {
            throw new IllegalArgumentException("A NF-e não possui número identificável.");
        }

        String dh = texto(ide, "dhEmi");
        if (dh.isEmpty()) dh = texto(ide, "dEmi");
        x.emissaoMillis = parseDataXml(dh);

        x.emitenteCnpj = CadastroBrasilUtils.apenasDigitos(texto(emit, "CNPJ"));
        x.emitenteNome = texto(emit, "xNome");
        if (x.emitenteNome.isEmpty()) x.emitenteNome = texto(emit, "xFant");

        if (x.emitenteCnpj.length() != 14 || !CadastroBrasilUtils.cnpjValido(x.emitenteCnpj)) {
            throw new IllegalArgumentException("O CNPJ do emitente da NF-e é inválido.");
        }
        if (x.emitenteNome.isEmpty()) {
            throw new IllegalArgumentException("A NF-e não informa o nome do emitente.");
        }

        String vNf = texto(total, "vNF");
        try {
            x.valor = Double.parseDouble(vNf.replace(",", "."));
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível identificar o valor total da NF-e.");
        }
        if (x.valor < 0) {
            throw new IllegalArgumentException("O valor total da NF-e é inválido.");
        }

        return x;
    }

    private Element primeiro(Document doc, String localName) {
        NodeList n = doc.getElementsByTagNameNS("*", localName);
        if (n.getLength() == 0) n = doc.getElementsByTagName(localName);
        return n.getLength() > 0 && n.item(0) instanceof Element ? (Element) n.item(0) : null;
    }

    private String texto(Element parent, String localName) {
        NodeList n = parent.getElementsByTagNameNS("*", localName);
        if (n.getLength() == 0) n = parent.getElementsByTagName(localName);
        if (n.getLength() == 0 || n.item(0) == null) return "";
        String s = n.item(0).getTextContent();
        return s == null ? "" : s.trim();
    }

    private long parseDataXml(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String s = raw.trim();
        try {
            if (s.length() >= 19) {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        .parse(s.substring(0, 19)).getTime();
            }
            if (s.length() >= 10) {
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .parse(s.substring(0, 10)).getTime();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static class DocumentoNfe {
        String numero = "";
        String serie = "";
        String chave = "";
        String emitenteNome = "";
        String emitenteCnpj = "";
        long emissaoMillis;
        double valor;
    }

    private void abrirDespesa(GestaoDbHelper.Despesa d) {
        StringBuilder msg = new StringBuilder();
        msg.append(data.format(new Date(d.dataMillis)));
        msg.append("\nCategoria: ")
                .append(d.categoria == null || d.categoria.isEmpty() ? "—" : d.categoria);
        msg.append("\nPagamento: ").append(d.formaPagamento);
        msg.append("\nValor: ").append(moeda.format(d.valor));

        if (d.favorecidoNome != null && !d.favorecidoNome.trim().isEmpty()) {
            msg.append("\nFavorecido: ").append(d.favorecidoNome.trim());
        }
        String favDoc = CadastroBrasilUtils.apenasDigitos(d.favorecidoDocumento);
        if (!favDoc.isEmpty()) {
            msg.append("\nCPF/CNPJ: ")
                    .append(CadastroBrasilUtils.formatarDocumento(favDoc, favDoc.length() > 11));
        }

        String rotulo = rotuloDocumento(d.documentoTipo);
        if (!rotulo.isEmpty()) msg.append("\nDocumento: ").append(rotulo);
        if (d.documentoNumero != null && !d.documentoNumero.trim().isEmpty()) {
            msg.append("\nNúmero: ").append(d.documentoNumero.trim());
        }
        if (d.documentoSerie != null && !d.documentoSerie.trim().isEmpty()) {
            msg.append("  Série: ").append(d.documentoSerie.trim());
        }
        if (d.documentoChave != null && !d.documentoChave.trim().isEmpty()) {
            msg.append("\nChave NF-e: ").append(d.documentoChave.trim());
        }
        if (d.documentoEmissaoMillis > 0) {
            msg.append("\nEmissão do documento: ")
                    .append(new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"))
                            .format(new Date(d.documentoEmissaoMillis)));
        }
        if (d.documentoXml != null && !d.documentoXml.isEmpty()) {
            msg.append("\nXML da NF-e: armazenado no lançamento");
        }
        if (d.observacao != null && !d.observacao.isEmpty()) {
            msg.append("\nObservação: ").append(d.observacao);
        }

        new AlertDialog.Builder(this)
                .setTitle(d.descricao)
                .setMessage(msg.toString())
                .setPositiveButton("Recibo / comprovante", (x,w) -> {
                    Intent i = new Intent(this, ComprovanteDespesaActivity.class);
                    i.putExtra("despesa_id", d.id);
                    startActivity(i);
                })
                .setNeutralButton("Fechar", null)
                .setNegativeButton("Cancelar lançamento", (x,w) -> cancelarDespesa(d))
                .show();
    }

    private void cancelarDespesa(GestaoDbHelper.Despesa d) {
        EditText motivo = campo("Motivo do cancelamento", InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cancelar lançamento?")
                .setMessage("O lançamento continuará no histórico e deixará de compor os totais.")
                .setView(motivo)
                .setPositiveButton("Confirmar", null)
                .setNegativeButton("Voltar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String m = motivo.getText().toString().trim();
                    if (m.isEmpty()) { motivo.setError("Informe o motivo"); return; }
                    db.cancelarDespesa(d.id, m);
                    dialog.dismiss();
                    carregar();
                    Toast.makeText(this, "Lançamento cancelado.", Toast.LENGTH_SHORT).show();
                }));
        dialog.show();
    }

    private String codigoDocumento(int pos) {
        switch (pos) {
            case 0: return "RECIBO";
            case 1: return "NFE_RECEBIDA";
            case 2: return "NFCE_CUPOM";
            case 3: return "BOLETO";
            case 4: return "OUTRO";
            default: return "SEM_DOCUMENTO";
        }
    }

    private String rotuloDocumento(String tipo) {
        if (tipo == null || tipo.trim().isEmpty() || "SEM_DOCUMENTO".equalsIgnoreCase(tipo)) return "";
        if ("RECIBO".equalsIgnoreCase(tipo)) return "Recibo";
        if ("NFE_RECEBIDA".equalsIgnoreCase(tipo)) return "NF-e recebida";
        if ("NFCE_CUPOM".equalsIgnoreCase(tipo)) return "NFC-e / Cupom";
        if ("BOLETO".equalsIgnoreCase(tipo)) return "Boleto";
        return "Outro";
    }

    private void limparFormularioDocumento() {
        despesaDialog = null;
        descricaoForm = null;
        favorecidoForm = null;
        favorecidoDocForm = null;
        valorForm = null;
        docNumeroForm = null;
        docSerieForm = null;
        docChaveForm = null;
        docEmitenteForm = null;
        docEmitenteCnpjForm = null;
        docTipoForm = null;
        docDetalhesBox = null;
        importarXmlBtn = null;
    }

    private double numero(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        if (s.isEmpty()) return Double.NaN;
        int c = s.lastIndexOf(',');
        int p = s.lastIndexOf('.');
        try {
            if (c >= 0 && p >= 0) {
                if (c > p) s = s.replace(".", "").replace(",", ".");
                else s = s.replace(",", "");
            } else if (c >= 0) {
                s = s.replace(",", ".");
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
