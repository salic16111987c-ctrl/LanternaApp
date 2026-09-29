package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProdutosActivity extends Activity {
    public static final String EXTRA_PRODUTO_ID = "produto_id_editar";
    public static final String EXTRA_FECHAR_APOS_SALVAR = "fechar_apos_salvar";

    private static final List<String> UNIDADES = Arrays.asList(
            "UN", "PC", "CX", "PCT", "KIT", "PAR", "ROLO",
            "KG", "G", "M", "CM", "L", "ML", "SERVIÇO", "OUTRA"
    );

    private GestaoDbHelper db;
    private LinearLayout lista;
    private TextView contador;
    private EditText busca;
    private Button ordenar;
    private int ordem = 0;
    private boolean syncProdutosRodando;
    private final Handler buscaHandler = new Handler(Looper.getMainLooper());
    private final Runnable buscaRunnable = this::carregar;
    private Button limparBusca;
    private boolean fecharAposSalvar;
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#101828"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = txt(s, 15, true);
        t.setTextColor(Color.parseColor("#344054"));
        t.setPadding(0, dp(14), 0, dp(3));
        return t;
    }

    private TextView label(String s) {
        TextView t = txt(s, 13, true);
        t.setTextColor(Color.parseColor("#475467"));
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setInputType(inputType);
        e.setSingleLine(true);
        return e;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);
        TechCellUi.applyWindowChrome(this);

        LinearLayout tela = new LinearLayout(this);
        tela.setOrientation(LinearLayout.VERTICAL);
        tela.setPadding(dp(14), dp(12), dp(14), dp(10));
        tela.setBackgroundColor(TechCellUi.BG);

        LinearLayout topo = new LinearLayout(this);
        topo.setOrientation(LinearLayout.HORIZONTAL);
        topo.setGravity(Gravity.CENTER_VERTICAL);

        Button voltar = new Button(this);
        voltar.setText("←");
        voltar.setTextSize(20);
        voltar.setMinWidth(0);
        voltar.setMinHeight(0);
        voltar.setPadding(0,0,0,0);
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        titulos.setPadding(dp(10),0,dp(8),0);

        TextView title = txt("Produtos", 25, true);
        titulos.addView(title);

        TextView info = txt("Cadastro e controle • Alpha 42", 11, false);
        info.setTextColor(TechCellUi.MUTED);
        titulos.addView(info);

        topo.addView(titulos, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button novo = new Button(this);
        novo.setText("+ Novo");
        novo.setTextSize(14);
        novo.setAllCaps(false);
        novo.setMinHeight(0);
        TechCellUi.stylePrimary(this, novo);
        novo.setOnClickListener(v -> abrirFormulario(null));
        topo.addView(novo, new LinearLayout.LayoutParams(dp(108), dp(44)));

        tela.addView(topo);

        LinearLayout buscaLinha = new LinearLayout(this);
        buscaLinha.setOrientation(LinearLayout.HORIZONTAL);
        buscaLinha.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        blp.setMargins(0, dp(12), 0, 0);

        busca = field("Buscar nome, código ou código de barras", InputType.TYPE_CLASS_TEXT);
        TechCellUi.styleSearch(this, busca);
        busca.setPadding(dp(14),0,dp(10),0);
        buscaLinha.addView(busca, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        limparBusca = new Button(this);
        limparBusca.setText("×");
        limparBusca.setTextSize(24);
        limparBusca.setAllCaps(false);
        limparBusca.setMinWidth(0);
        limparBusca.setMinHeight(0);
        limparBusca.setPadding(0,0,0,0);
        TechCellUi.styleSecondary(this, limparBusca);
        limparBusca.setVisibility(View.GONE);
        limparBusca.setOnClickListener(v -> {
            busca.setText("");
            busca.requestFocus();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(48), dp(48));
        clp.setMargins(dp(6),0,0,0);
        buscaLinha.addView(limparBusca, clp);

        tela.addView(buscaLinha, blp);

        LinearLayout barraLista = new LinearLayout(this);
        barraLista.setOrientation(LinearLayout.HORIZONTAL);
        barraLista.setGravity(Gravity.CENTER_VERTICAL);
        barraLista.setPadding(0, dp(7), 0, dp(6));

        contador = txt("", 12, true);
        contador.setTextColor(TechCellUi.NAVY);
        barraLista.addView(contador, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ordenar = new Button(this);
        ordenar.setText("Nome ↕");
        ordenar.setTextSize(12);
        ordenar.setAllCaps(false);
        ordenar.setMinHeight(0);
        TechCellUi.styleSecondary(this, ordenar);
        ordenar.setOnClickListener(v -> {
            ordem = (ordem + 1) % 3;
            atualizarRotuloOrdenacao();
            carregar();
        });
        barraLista.addView(ordenar, new LinearLayout.LayoutParams(dp(118), dp(38)));
        tela.addView(barraLista);

        ScrollView scrollLista = new ScrollView(this);
        scrollLista.setFillViewport(true);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0,0,0,dp(24));
        scrollLista.addView(lista);
        tela.addView(scrollLista, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        busca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                limparBusca.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                buscaHandler.removeCallbacks(buscaRunnable);
                buscaHandler.postDelayed(buscaRunnable, 160);
            }
            public void afterTextChanged(Editable e) {}
        });

        setContentView(tela);
        atualizarRotuloOrdenacao();
        carregar();

        long produtoIdDireto = getIntent().getLongExtra(EXTRA_PRODUTO_ID, -1);
        fecharAposSalvar = getIntent().getBooleanExtra(EXTRA_FECHAR_APOS_SALVAR, false);
        if (produtoIdDireto > 0) {
            GestaoDbHelper.Produto direto = db.get(produtoIdDireto);
            if (direto != null) {
                abrirFormulario(direto);
            } else {
                Toast.makeText(this, "Produto não encontrado.", Toast.LENGTH_LONG).show();
                if (fecharAposSalvar) finish();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        TechCellBackgroundSync.garantir(this);
        sincronizarProdutosMaster();
    }

    private void sincronizarProdutosMaster() {
        if (syncProdutosRodando) return;
        GestaoDbHelper dbLocal = db;
        if (dbLocal == null) return;
        GestaoDbHelper.SyncContext ctx = dbLocal.getSyncContext();
        if (!ctx.configurado || "MASTER".equalsIgnoreCase(ctx.papelDispositivo) ||
                ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) return;

        syncProdutosRodando = true;
        new Thread(() -> {
            TechCellSyncCoordinator.Resultado r =
                    TechCellSyncCoordinator.sincronizar(getApplicationContext());
            runOnUiThread(() -> {
                syncProdutosRodando = false;
                if (!r.ocupado && r.produtosAlterados > 0) carregar();
            });
        }, "TechCell-Produtos-Sync").start();
    }

    private void atualizarRotuloOrdenacao() {
        if (ordenar == null) return;
        ordenar.setText(ordem == 0 ? "Nome ↕"
                : ordem == 1 ? "Estoque ↑"
                : "Preço ↓");
    }

    private void carregar() {
        String termo = busca == null ? "" : busca.getText().toString().trim();
        int totalEncontrados = db.countProdutos(termo);
        List<GestaoDbHelper.Produto> produtos =
                db.listProdutosTela(termo, ordem, 80);

        lista.removeAllViews();
        if (totalEncontrados > produtos.size()) {
            contador.setText("Exibindo " + produtos.size() + " de " + totalEncontrados + " produtos");
        } else {
            contador.setText(totalEncontrados + (totalEncontrados==1 ? " produto" : " produtos"));
        }

        if (produtos.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 14, false);
            vazio.setTextColor(TechCellUi.MUTED);
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(8), dp(28), dp(8), dp(28));
            lista.addView(vazio);
            return;
        }

        if (termo.isEmpty() && totalEncontrados > produtos.size()) {
            TextView dica = txt(
                    "Mostrando apenas os primeiros " + produtos.size() +
                            " para manter a tela rápida. Use a busca para localizar qualquer produto.",
                    12, false);
            dica.setTextColor(TechCellUi.MUTED);
            dica.setPadding(dp(8), dp(8), dp(8), dp(8));
            lista.addView(dica);
        }

        for (GestaoDbHelper.Produto p : produtos) {
            LinearLayout card = TechCellUi.card(this);
            card.setPadding(dp(12), dp(11), dp(10), dp(11));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(5), 0, dp(5));
            card.setLayoutParams(cp);

            LinearLayout topo = new LinearLayout(this);
            topo.setOrientation(LinearLayout.HORIZONTAL);
            topo.setGravity(Gravity.CENTER_VERTICAL);

            String inicial = p.nome == null || p.nome.trim().isEmpty()
                    ? "P" : p.nome.trim().substring(0,1).toUpperCase(new Locale("pt","BR"));
            TextView avatar = txt(inicial, 18, true);
            avatar.setTextColor(TechCellUi.BLUE);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(TechCellUi.solid(this, TechCellUi.PALE_BLUE, 12));
            topo.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout dados = new LinearLayout(this);
            dados.setOrientation(LinearLayout.VERTICAL);
            dados.setPadding(dp(10),0,dp(6),0);

            String cab = (p.codigo == null || p.codigo.isEmpty() ? "" : p.codigo + " • ") + p.nome;
            TextView nome = txt(cab, 15, true);
            dados.addView(nome);

            String un = unidadeExibicao(p.unidade);
            TextView linha1 = txt(
                    "Custo " + moeda.format(p.custo) +
                            "   •   Venda " + moeda.format(p.precoVenda),
                    12, false);
            linha1.setTextColor(TechCellUi.MUTED);
            linha1.setPadding(0,dp(3),0,0);
            dados.addView(linha1);

            TextView linha2 = txt(
                    "Lucro/un. " + moeda.format(p.lucroUnitario()) +
                            " (" + fmtPct(p.lucroPercentualSobreCusto()) + ")" +
                            "   •   Estoque " + fmtQtd(p.estoque) + " " + un,
                    12, false);
            linha2.setTextColor(Color.parseColor("#475467"));
            linha2.setPadding(0,dp(2),0,0);
            dados.addView(linha2);

            topo.addView(dados, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView seta = txt("›", 26, true);
            seta.setTextColor(TechCellUi.NAVY);
            topo.addView(seta);

            card.addView(topo);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(dp(54), dp(7), 0, 0);

            TextView fiscal = TechCellUi.chip(
                    this,
                    p.fiscalMinimoPreenchido() ? "✓ Fiscal OK" : "▧ Fiscal pendente",
                    p.fiscalMinimoPreenchido() ? TechCellUi.GREEN : TechCellUi.ORANGE,
                    p.fiscalMinimoPreenchido()
                            ? Color.parseColor("#EAF8F0")
                            : Color.parseColor("#FFF4E5"));
            chips.addView(fiscal);

            if (p.estoque <= p.estoqueMinimo && p.estoqueMinimo > 0) {
                TextView baixo = TechCellUi.chip(
                        this,
                        "⚠ Estoque baixo",
                        TechCellUi.RED,
                        Color.parseColor("#FFF1F0"));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.setMargins(dp(6),0,0,0);
                chips.addView(baixo, blp);
            }

            card.addView(chips);
            card.setOnClickListener(v -> abrirFormulario(p));
            lista.addView(card);
        }
    }

    private String fmtPct(double v) {
        return String.format(new Locale("pt","BR"), "%.1f%%", v);
    }

    private String unidadeExibicao(String unidade) {
        if (unidade == null || unidade.trim().isEmpty()) return "UN";
        return unidade.trim().toUpperCase(new Locale("pt","BR"));
    }

    private String fmtQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }

    private String fmtEdit(double v, boolean quantidade) {
        if (v == 0) return "";
        if (quantidade) return fmtQtd(v).replace(".", ",");
        return String.format(Locale.US, "%.2f", v).replace(".", ",");
    }

    private double num(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        if (s.isEmpty()) return 0;

        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');

        try {
            if (lastComma >= 0 && lastDot >= 0) {
                if (lastComma > lastDot) {
                    s = s.replace(".", "").replace(",", ".");
                } else {
                    s = s.replace(",", "");
                }
            } else if (lastComma >= 0) {
                s = s.replace(",", ".");
            }
            return Double.parseDouble(s);
        } catch(Exception e) {
            return Double.NaN;
        }
    }

    private boolean unidadeFracionada(String unidade) {
        String u = unidade == null ? "" : unidade.toUpperCase(new Locale("pt","BR"));
        return u.equals("KG") || u.equals("G") || u.equals("M") || u.equals("CM") ||
                u.equals("L") || u.equals("ML") || u.equals("OUTRA");
    }

    private void ajustarTecladoQuantidade(EditText estoque, EditText minimo, String unidade) {
        int tipo = InputType.TYPE_CLASS_NUMBER;
        if (unidadeFracionada(unidade)) tipo |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
        estoque.setInputType(tipo);
        minimo.setInputType(tipo);
        estoque.setHint(unidadeFracionada(unidade) ? "Ex.: 2,5" : "Ex.: 10");
        minimo.setHint(unidadeFracionada(unidade) ? "Ex.: 1,5" : "Ex.: 2");
    }

    private int posicaoUnidade(String unidade) {
        if (unidade == null || unidade.trim().isEmpty()) return 0;
        String u = unidade.trim().toUpperCase(new Locale("pt","BR"));
        int pos = UNIDADES.indexOf(u);
        return pos >= 0 ? pos : UNIDADES.indexOf("OUTRA");
    }

    private void abrirFormulario(GestaoDbHelper.Produto original) {
        GestaoDbHelper.Produto p = original == null ? new GestaoDbHelper.Produto() : original;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(12));
        sv.addView(box);

        box.addView(section("IDENTIFICAÇÃO"));

        box.addView(label("Código interno"));
        EditText codigo = field("Ex.: 152", InputType.TYPE_CLASS_TEXT);
        box.addView(codigo);

        box.addView(label("Nome do produto *"));
        EditText nome = field("Ex.: Cabo USB-C 1 m", InputType.TYPE_CLASS_TEXT);
        box.addView(nome);

        box.addView(label("Código de barras"));
        EditText barras = field("Leia ou digite o código de barras", InputType.TYPE_CLASS_TEXT);
        box.addView(barras);

        box.addView(label("Grupo"));
        EditText grupo = field("Ex.: Acessórios", InputType.TYPE_CLASS_TEXT);
        box.addView(grupo);

        box.addView(label("Fornecedor"));
        EditText fornecedor = field("Nome do fornecedor", InputType.TYPE_CLASS_TEXT);
        box.addView(fornecedor);

        box.addView(label("Fabricante"));
        EditText fabricante = field("Nome do fabricante", InputType.TYPE_CLASS_TEXT);
        box.addView(fabricante);

        box.addView(label("Unidade de controle"));
        Spinner unidade = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<>(UNIDADES));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unidade.setAdapter(adapter);
        box.addView(unidade);

        EditText unidadeOutra = field("Digite a unidade", InputType.TYPE_CLASS_TEXT);
        unidadeOutra.setVisibility(View.GONE);
        box.addView(unidadeOutra);

        box.addView(section("PREÇOS E LUCRO"));

        box.addView(label("Valor de compra / custo"));
        int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText custo = field("R$ 0,00", decimal);
        box.addView(custo);

        box.addView(label("Preço de venda à vista"));
        EditText venda = field("R$ 0,00", decimal);
        box.addView(venda);

        box.addView(label("Preço de venda a prazo"));
        EditText prazo = field("Opcional", decimal);
        box.addView(prazo);

        TextView lucroPreview = txt(
                "Lucro por unidade: R$ 0,00\n" +
                "Lucro % sobre custo: 0,0%",
                14, true);
        lucroPreview.setTextColor(Color.parseColor("#176240"));
        lucroPreview.setBackgroundColor(Color.parseColor("#ECFDF3"));
        lucroPreview.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpPreview = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPreview.setMargins(0, dp(10), 0, dp(2));
        lucroPreview.setLayoutParams(lpPreview);
        box.addView(lucroPreview);

        TextView explicacao = txt(
                "Lucro % = (preço de venda − custo) ÷ custo × 100. " +
                "Ex.: custo R$ 1 e venda R$ 10 = lucro de 900%.",
                12, false);
        explicacao.setTextColor(Color.parseColor("#667085"));
        explicacao.setPadding(0, dp(5), 0, dp(2));
        box.addView(explicacao);

        box.addView(section("ESTOQUE"));

        box.addView(label("Quantidade atual"));
        EditText estoque = field("Ex.: 10", InputType.TYPE_CLASS_NUMBER);
        box.addView(estoque);

        box.addView(label("Estoque mínimo"));
        EditText minimo = field("Ex.: 2", InputType.TYPE_CLASS_NUMBER);
        box.addView(minimo);

        TextView ajuda = txt(
                "UN, PC, CX, PCT, KIT, PAR e ROLO usam quantidade inteira. " +
                "KG, G, M, CM, L e ML aceitam quantidade fracionada.",
                12, false);
        ajuda.setTextColor(Color.parseColor("#667085"));
        ajuda.setPadding(0, dp(5), 0, dp(4));
        box.addView(ajuda);

        box.addView(section("DADOS FISCAIS"));
        TextView fiscalAviso = txt(
                "Não preencha códigos tributários por adivinhação. Confirme NCM, CFOP, " +
                "CSOSN/CST, PIS e COFINS com a contabilidade. Estes dados serão usados " +
                "somente quando a emissão fiscal real for ativada.",
                12, false);
        fiscalAviso.setTextColor(Color.parseColor("#B54708"));
        fiscalAviso.setBackgroundColor(Color.parseColor("#FFF6ED"));
        fiscalAviso.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(fiscalAviso);

        box.addView(label("NCM (8 dígitos)"));
        EditText ncm = field("Ex.: 85176259", InputType.TYPE_CLASS_NUMBER);
        box.addView(ncm);

        box.addView(label("CEST (quando aplicável)"));
        EditText cest = field("Opcional", InputType.TYPE_CLASS_NUMBER);
        box.addView(cest);

        box.addView(label("CFOP padrão (4 dígitos)"));
        EditText cfop = field("Ex.: conforme orientação contábil", InputType.TYPE_CLASS_NUMBER);
        box.addView(cfop);

        box.addView(label("Origem da mercadoria"));
        EditText origem = field("Código de origem", InputType.TYPE_CLASS_NUMBER);
        box.addView(origem);

        box.addView(label("CSOSN/CST ICMS padrão"));
        EditText tributacaoIcms = field("Conforme regime/operação", InputType.TYPE_CLASS_TEXT);
        box.addView(tributacaoIcms);

        box.addView(label("Alíquota ICMS % (quando aplicável)"));
        EditText aliquotaIcms = field("0,00", decimal);
        box.addView(aliquotaIcms);

        box.addView(label("CST PIS"));
        EditText cstPis = field("Código CST PIS", InputType.TYPE_CLASS_TEXT);
        box.addView(cstPis);

        box.addView(label("Alíquota PIS % (quando aplicável)"));
        EditText aliquotaPis = field("0,00", decimal);
        box.addView(aliquotaPis);

        box.addView(label("CST COFINS"));
        EditText cstCofins = field("Código CST COFINS", InputType.TYPE_CLASS_TEXT);
        box.addView(cstCofins);

        box.addView(label("Alíquota COFINS % (quando aplicável)"));
        EditText aliquotaCofins = field("0,00", decimal);
        box.addView(aliquotaCofins);

        box.addView(label("Unidade tributável"));
        EditText unidadeTributavel = field("Ex.: UN", InputType.TYPE_CLASS_TEXT);
        box.addView(unidadeTributavel);

        box.addView(label("GTIN tributável / EAN (quando houver)"));
        EditText gtinTributavel = field("Código de barras tributável", InputType.TYPE_CLASS_TEXT);
        box.addView(gtinTributavel);

        codigo.setText(p.codigo);
        nome.setText(p.nome);
        barras.setText(p.codigoBarras);
        grupo.setText(p.grupo);
        fornecedor.setText(p.fornecedor);
        fabricante.setText(p.fabricante);
        custo.setText(fmtEdit(p.custo, false));
        venda.setText(fmtEdit(p.precoVenda, false));
        prazo.setText(fmtEdit(p.precoPrazo, false));
        estoque.setText(fmtEdit(p.estoque, true));
        minimo.setText(fmtEdit(p.estoqueMinimo, true));
        ncm.setText(p.ncm);
        cest.setText(p.cest);
        cfop.setText(p.cfop);
        origem.setText(p.origem);
        tributacaoIcms.setText(p.tributacaoIcms);
        aliquotaIcms.setText(fmtEdit(p.aliquotaIcms, false));
        cstPis.setText(p.cstPis);
        aliquotaPis.setText(fmtEdit(p.aliquotaPis, false));
        cstCofins.setText(p.cstCofins);
        aliquotaCofins.setText(fmtEdit(p.aliquotaCofins, false));
        unidadeTributavel.setText(p.unidadeTributavel);
        gtinTributavel.setText(p.gtinTributavel);

        int pos = posicaoUnidade(p.unidade);
        unidade.setSelection(pos);
        if (pos == UNIDADES.indexOf("OUTRA") && p.unidade != null && !p.unidade.trim().isEmpty()
                && !UNIDADES.contains(p.unidade.trim().toUpperCase(new Locale("pt","BR")))) {
            unidadeOutra.setText(p.unidade);
            unidadeOutra.setVisibility(View.VISIBLE);
        }
        ajustarTecladoQuantidade(estoque, minimo, UNIDADES.get(pos));

        Runnable atualizarLucro = () -> {
            double c = num(custo.getText().toString());
            double v = num(venda.getText().toString());
            if (Double.isNaN(c)) c = 0;
            if (Double.isNaN(v)) v = 0;

            double lucro = v - c;
            double lucroPct = c > 0 ? (lucro / c) * 100.0 : 0.0;

            lucroPreview.setText(
                    "Lucro por unidade: " + moeda.format(lucro) +
                    "\nLucro % sobre custo: " + fmtPct(lucroPct));

            lucroPreview.setTextColor(lucro < 0 ? Color.parseColor("#B42318") : Color.parseColor("#176240"));
            lucroPreview.setBackgroundColor(Color.parseColor(lucro < 0 ? "#FEF3F2" : "#ECFDF3"));
        };

        TextWatcher lucroWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { atualizarLucro.run(); }
            public void afterTextChanged(Editable e) {}
        };
        custo.addTextChangedListener(lucroWatcher);
        venda.addTextChangedListener(lucroWatcher);
        atualizarLucro.run();

        unidade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String u = UNIDADES.get(position);
                unidadeOutra.setVisibility("OUTRA".equals(u) ? View.VISIBLE : View.GONE);
                ajustarTecladoQuantidade(estoque, minimo, u);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(original == null ? "Novo produto" : "Editar produto")
                .setView(sv)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null);

        if (original != null) {
            b.setNeutralButton("Excluir", (d,w) -> new AlertDialog.Builder(this)
                    .setTitle("Excluir produto?")
                    .setMessage(original.nome)
                    .setPositiveButton("Excluir", (dd,ww) -> {
                        db.delete(original.id);
                        if (fecharAposSalvar) finish();
                        else carregar();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
        }

        AlertDialog dialog = b.create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = nome.getText().toString().trim();
            if (n.isEmpty()) {
                nome.setError("Informe o nome do produto");
                return;
            }

            double c = num(custo.getText().toString());
            double pv = num(venda.getText().toString());
            double pp = num(prazo.getText().toString());
            double est = num(estoque.getText().toString());
            double min = num(minimo.getText().toString());
            double aliIcms = num(aliquotaIcms.getText().toString());
            double aliPis = num(aliquotaPis.getText().toString());
            double aliCofins = num(aliquotaCofins.getText().toString());

            if (Double.isNaN(c)) { custo.setError("Valor inválido"); return; }
            if (Double.isNaN(pv)) { venda.setError("Valor inválido"); return; }
            if (Double.isNaN(pp)) { prazo.setError("Valor inválido"); return; }
            if (Double.isNaN(est)) { estoque.setError("Quantidade inválida"); return; }
            if (Double.isNaN(min)) { minimo.setError("Quantidade inválida"); return; }
            if (Double.isNaN(aliIcms)) { aliquotaIcms.setError("Valor inválido"); return; }
            if (Double.isNaN(aliPis)) { aliquotaPis.setError("Valor inválido"); return; }
            if (Double.isNaN(aliCofins)) { aliquotaCofins.setError("Valor inválido"); return; }

            String ncmTxt = CadastroBrasilUtils.apenasDigitos(ncm.getText().toString());
            String cfopTxt = CadastroBrasilUtils.apenasDigitos(cfop.getText().toString());
            if (!ncmTxt.isEmpty() && ncmTxt.length() != 8) {
                ncm.setError("NCM deve ter 8 dígitos");
                return;
            }
            if (!cfopTxt.isEmpty() && cfopTxt.length() != 4) {
                cfop.setError("CFOP deve ter 4 dígitos");
                return;
            }
            if (aliIcms < 0 || aliPis < 0 || aliCofins < 0) {
                Toast.makeText(this, "Não use alíquotas negativas.", Toast.LENGTH_LONG).show();
                return;
            }

            String uSel = UNIDADES.get(unidade.getSelectedItemPosition());
            String uFinal = uSel;
            if ("OUTRA".equals(uSel)) {
                uFinal = unidadeOutra.getText().toString().trim().toUpperCase(new Locale("pt","BR"));
                if (uFinal.isEmpty()) {
                    unidadeOutra.setError("Informe a unidade");
                    return;
                }
            }

            if (!unidadeFracionada(uSel)) {
                if (Math.abs(est - Math.rint(est)) > 0.000001) {
                    estoque.setError("Para " + uSel + ", use quantidade inteira");
                    return;
                }
                if (Math.abs(min - Math.rint(min)) > 0.000001) {
                    minimo.setError("Para " + uSel + ", use quantidade inteira");
                    return;
                }
            }

            if (c < 0 || pv < 0 || pp < 0 || est < 0 || min < 0) {
                Toast.makeText(this, "Não use valores negativos no cadastro.", Toast.LENGTH_LONG).show();
                return;
            }

            p.codigo = codigo.getText().toString().trim();
            p.nome = n;
            p.codigoBarras = barras.getText().toString().trim();
            p.grupo = grupo.getText().toString().trim();
            p.fornecedor = fornecedor.getText().toString().trim();
            p.unidade = uFinal;
            p.fabricante = fabricante.getText().toString().trim();
            p.custo = c;
            p.precoVenda = pv;
            p.precoPrazo = pp;
            p.estoque = est;
            p.estoqueMinimo = min;
            p.ncm = ncmTxt;
            p.cest = CadastroBrasilUtils.apenasDigitos(cest.getText().toString());
            p.cfop = cfopTxt;
            p.origem = origem.getText().toString().trim();
            p.tributacaoIcms = tributacaoIcms.getText().toString().trim().toUpperCase(new Locale("pt","BR"));
            p.aliquotaIcms = aliIcms;
            p.cstPis = cstPis.getText().toString().trim().toUpperCase(new Locale("pt","BR"));
            p.aliquotaPis = aliPis;
            p.cstCofins = cstCofins.getText().toString().trim().toUpperCase(new Locale("pt","BR"));
            p.aliquotaCofins = aliCofins;
            p.unidadeTributavel = unidadeTributavel.getText().toString().trim().toUpperCase(new Locale("pt","BR"));
            p.gtinTributavel = gtinTributavel.getText().toString().trim();

            db.save(p);
            dialog.dismiss();
            Toast.makeText(this, "Produto salvo.", Toast.LENGTH_SHORT).show();
            if (fecharAposSalvar) finish();
            else carregar();
        }));
        dialog.show();
    }

    @Override protected void onDestroy() {
        buscaHandler.removeCallbacks(buscaRunnable);
        if (db != null) db.close();
        super.onDestroy();
    }

}
