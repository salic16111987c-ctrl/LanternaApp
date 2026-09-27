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
    private Button abaSaidasBtn;
    private Button hojeBtn;
    private Button mesBtn;
    private Button mesAnteriorBtn;
    private Button anoAtualBtn;
    private Button anoAnteriorBtn;
    private Button personalizadoBtn;
    private int periodoAtivo = 1;
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

        TextView sub = txt("Vendas, custos, despesas e documentos • Alpha 40", 12, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout filtros1 = new LinearLayout(this);
        filtros1.setOrientation(LinearLayout.HORIZONTAL);
        filtros1.setPadding(0, dp(12), 0, 0);

        hojeBtn = action("Hoje");
        hojeBtn.setOnClickListener(v -> periodoHoje());
        filtros1.addView(hojeBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        mesBtn = action("Este mês");
        mesBtn.setOnClickListener(v -> periodoMesAtual());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(46), 1);
        mp.setMargins(dp(6),0,0,0);
        filtros1.addView(mesBtn, mp);
        root.addView(filtros1);

        LinearLayout filtros2 = new LinearLayout(this);
        filtros2.setOrientation(LinearLayout.HORIZONTAL);
        filtros2.setPadding(0, dp(6), 0, 0);

        mesAnteriorBtn = action("Mês anterior");
        mesAnteriorBtn.setOnClickListener(v -> periodoMesAnterior());
        filtros2.addView(mesAnteriorBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        anoAtualBtn = action("Este ano");
        anoAtualBtn.setOnClickListener(v -> periodoAnoAtual());
        LinearLayout.LayoutParams aa = new LinearLayout.LayoutParams(0, dp(46), 1);
        aa.setMargins(dp(6),0,0,0);
        filtros2.addView(anoAtualBtn, aa);
        root.addView(filtros2);

        LinearLayout filtros3 = new LinearLayout(this);
        filtros3.setOrientation(LinearLayout.HORIZONTAL);
        filtros3.setPadding(0, dp(6), 0, 0);

        anoAnteriorBtn = action("Ano anterior");
        anoAnteriorBtn.setOnClickListener(v -> periodoAnoAnterior());
        filtros3.addView(anoAnteriorBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        personalizadoBtn = action("Período...");
        personalizadoBtn.setOnClickListener(v -> escolherPeriodo());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(46), 1);
        pp.setMargins(dp(6),0,0,0);
        filtros3.addView(personalizadoBtn, pp);
        root.addView(filtros3);

        periodoTexto = txt("", 13, true);
        periodoTexto.setTextColor(TechCellUi.NAVY);
        periodoTexto.setBackground(TechCellUi.pillBackground(this));
        periodoTexto.setPadding(dp(12), dp(9), dp(12), dp(9));
        root.addView(periodoTexto, TechCellUi.fullCardParams(this, 6));

        LinearLayout abas = new LinearLayout(this);
        abas.setOrientation(LinearLayout.HORIZONTAL);
        abas.setPadding(0, dp(8), 0, 0);

        abaResumoBtn = action("Resumo");
        abaResumoBtn.setOnClickListener(v -> {
            mostrarSaidas = false;
            carregar();
        });
        abas.addView(abaResumoBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        abaSaidasBtn = action("Saídas / despesas");
        abaSaidasBtn.setOnClickListener(v -> {
            mostrarSaidas = true;
            carregar();
        });
        LinearLayout.LayoutParams asp = new LinearLayout.LayoutParams(0, dp(46), 1);
        asp.setMargins(dp(6),0,0,0);
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
        periodoTexto.setText("Período: " +
                dataCurta.format(new Date(inicioAtual)) + " a " +
                dataCurta.format(new Date(fimInclusivo)));

        atualizarFiltrosPeriodo();
        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);

        resumoBox.removeAllViews();

        double margemLiquida = r.vendas.total > 0
                ? (r.lucroLiquido / r.vendas.total) * 100.0 : 0.0;

        LinearLayout hero = TechCellUi.card(this);
        hero.setBackground(TechCellUi.solid(this,
                r.lucroLiquido >= 0
                        ? TechCellUi.PALE_GREEN
                        : Color.parseColor("#FFF1F0"), 16));
        hero.setLayoutParams(TechCellUi.fullCardParams(this, 12));

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
            LinearLayout a = cardResumo(
                    "Outras saídas", moeda.format(r.outrasSaidas),
                    r.outrasSaidas > 0.001 ? "#B42318" : "#667085");
            LinearLayout b = cardResumo(
                    "Compras p/ estoque", moeda.format(r.comprasEstoque),
                    r.comprasEstoque > 0.001 ? "#B54708" : "#667085");
            adicionarLinhaResumo(a, b);
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

        atualizarAbasFinanceiro();

        if (!mostrarSaidas) {
            lista.removeAllViews();
            return;
        }

        lista.removeAllViews();
        List<GestaoDbHelper.Despesa> despesas = db.listDespesas(inicioAtual, fimAtual, 500);
        if (despesas.isEmpty()) {
            TextView vazio = txt("Nenhuma saída registrada neste período.", 14, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(22), 0, dp(22));
            lista.addView(vazio);
            return;
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
                    ? Color.parseColor("#667085") : Color.parseColor("#B42318"));
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
            meta.setTextColor(Color.parseColor("#667085"));
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
                st.setTextColor(Color.parseColor("#B42318"));
                card.addView(st);
            } else {
                card.setOnClickListener(v -> abrirDespesa(d));
            }
            lista.addView(card);
        }
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
        if (mostrarSaidas) {
            TechCellUi.styleSecondary(this, abaResumoBtn);
            TechCellUi.stylePrimary(this, abaSaidasBtn, TechCellUi.NAVY);
        } else {
            TechCellUi.stylePrimary(this, abaResumoBtn, TechCellUi.NAVY);
            TechCellUi.styleSecondary(this, abaSaidasBtn);
        }

        resumoBox.setVisibility(mostrarSaidas ? View.GONE : View.VISIBLE);
        novaDespesaBtn.setVisibility(mostrarSaidas ? View.VISIBLE : View.GONE);
        saidasTitulo.setVisibility(mostrarSaidas ? View.VISIBLE : View.GONE);
        lista.setVisibility(mostrarSaidas ? View.VISIBLE : View.GONE);
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
