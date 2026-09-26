package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PdvActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final List<GestaoDbHelper.VendaItem> carrinho = new ArrayList<>();

    private GestaoDbHelper db;
    private EditText busca;
    private LinearLayout resultados;
    private LinearLayout itensCarrinho;
    private TextView statusCarrinho;
    private TextView subtotalValor;
    private TextView descontoValor;
    private TextView totalValor;
    private Button finalizar;
    private Button removerDesconto;

    private boolean descontoPercentual = false;
    private double descontoEntrada = 0;

    private final int NAVY = Color.parseColor("#0B1F3A");
    private final int NAVY_2 = Color.parseColor("#123A68");
    private final int BLUE = Color.parseColor("#1769C2");
    private final int GREEN = Color.parseColor("#07884B");
    private final int RED = Color.parseColor("#D92D20");
    private final int ORANGE = Color.parseColor("#F97316");
    private final int BG = Color.parseColor("#F5F7FB");
    private final int TEXT = Color.parseColor("#182230");
    private final int MUTED = Color.parseColor("#667085");
    private final int BORDER = Color.parseColor("#DCE2EA");
    private final int PALE_BLUE = Color.parseColor("#EFF6FF");
    private final int PALE_GREEN = Color.parseColor("#EAF8F0");
    private final int PALE_RED = Color.parseColor("#FFF1F0");

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable solid(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable bordered(int color, int radius, int strokeColor) {
        GradientDrawable d = solid(color, radius);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private GradientDrawable headerBg() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY_2, NAVY});
        d.setCornerRadii(new float[]{
                0,0,0,0,
                dp(22),dp(22),dp(22),dp(22)
        });
        return d;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private TextView txt(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView iconCircle(String icon, int bgColor, int textColor, int size) {
        TextView v = txt(icon, 18, true);
        v.setTextColor(textColor);
        v.setGravity(Gravity.CENTER);
        v.setBackground(circle(bgColor));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return v;
    }

    private Button filledButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(solid(color, 12));
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setElevation(dp(1));
        return b;
    }

    private Button outlineButton(String label, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setBackground(bordered(Color.WHITE, 10, BORDER));
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(bordered(Color.WHITE, 16, BORDER));
        v.setPadding(dp(14), dp(14), dp(14), dp(14));
        v.setElevation(dp(1));
        return v;
    }

    private LinearLayout.LayoutParams cardParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private LinearLayout sectionTitle(String icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(18), 0, dp(7));

        TextView i = txt(icon, 21, true);
        i.setTextColor(NAVY);
        row.addView(i);

        TextView t = txt(title, 15, true);
        t.setTextColor(NAVY);
        t.setPadding(dp(8), 0, 0, 0);
        row.addView(t);
        return row;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            db = new GestaoDbHelper(getApplicationContext());
            db.getWritableDatabase();
            montarTela();
            long vendaFiscal = getIntent().getLongExtra("emitir_nota_venda_id", -1);
            if (vendaFiscal > 0) {
                busca.post(() -> abrirEmissaoNota(vendaFiscal));
            }
        } catch (Throwable e) {
            mostrarFalhaInicial(e);
        }
    }

    @Override public void onBackPressed() {
        tentarSair();
    }

    private void mostrarFalhaInicial(Throwable e) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(BG);

        root.addView(txt("PDV / Frente de Caixa", 26, true));

        TextView aviso = txt("O PDV encontrou um erro ao iniciar.", 16, true);
        aviso.setTextColor(RED);
        aviso.setPadding(0, dp(16), 0, dp(10));
        root.addView(aviso);

        String detalhe = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            detalhe += "\n" + e.getMessage();
        }

        TextView erro = txt(detalhe, 13, false);
        erro.setBackground(bordered(Color.WHITE, 12, BORDER));
        erro.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(erro);

        Button voltar = outlineButton("Voltar", NAVY);
        LinearLayout.LayoutParams bp = cardParams(14);
        bp.height = dp(48);
        voltar.setLayoutParams(bp);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        setContentView(root);
    }

    private void montarTela() {
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.WHITE);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        screen.addView(scroll, sp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), 0, dp(14), dp(24));
        scroll.addView(root);

        montarCabecalho(root);
        montarBusca(root);
        montarItens(root);
        montarFerramentas(root);
        montarResumo(root);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14), dp(10), dp(14), dp(12));
        bottom.setBackground(bordered(Color.WHITE, 0, BORDER));
        bottom.setElevation(dp(8));

        finalizar = filledButton("▣   FINALIZAR VENDA   →", GREEN);
        finalizar.setTextSize(18);
        finalizar.setMinHeight(dp(58));
        finalizar.setOnClickListener(v -> abrirPagamento());
        bottom.addView(finalizar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        screen.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        busca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                atualizarResultados();
            }
            public void afterTextChanged(Editable e) {}
        });

        busca.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH) {
                adicionarCorrespondenciaDireta();
                return true;
            }
            return false;
        });

        setContentView(screen);
        atualizarResultados();
        atualizarCarrinho();
    }

    private void montarCabecalho(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(headerBg());
        header.setPadding(dp(16), dp(18), dp(16), dp(18));

        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(-dp(14), 0, -dp(14), dp(10));
        header.setLayoutParams(hp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button voltar = outlineButton("←", Color.WHITE);
        voltar.setTextSize(23);
        voltar.setBackground(bordered(Color.parseColor("#244B78"), 13, Color.parseColor("#3D648F")));
        voltar.setOnClickListener(v -> tentarSair());
        top.addView(voltar, new LinearLayout.LayoutParams(dp(58), dp(52)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);

        TextView title = txt("TECH CELL • PDV", 25, true);
        title.setTextColor(Color.WHITE);
        titles.addView(title);

        TextView sub = txt("Frente de Caixa • Alpha 23", 13, false);
        sub.setTextColor(Color.parseColor("#D9E3F0"));
        sub.setPadding(0, dp(2), 0, 0);
        titles.addView(sub);

        top.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(top);

        statusCarrinho = txt("", 14, true);
        statusCarrinho.setTextColor(Color.WHITE);
        statusCarrinho.setPadding(dp(2), dp(16), 0, 0);
        header.addView(statusCarrinho);

        root.addView(header);
    }

    private void montarBusca(LinearLayout root) {
        LinearLayout buscaCard = card();
        buscaCard.setLayoutParams(cardParams(10));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = txt("⌕", 28, true);
        icon.setTextColor(BLUE);
        titleRow.addView(icon);

        TextView label = txt("LOCALIZAR PRODUTO", 14, true);
        label.setTextColor(Color.parseColor("#3D4F6A"));
        label.setPadding(dp(8), 0, 0, 0);
        titleRow.addView(label);
        buscaCard.addView(titleRow);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(10), 0, 0);

        LinearLayout inputBox = new LinearLayout(this);
        inputBox.setOrientation(LinearLayout.HORIZONTAL);
        inputBox.setGravity(Gravity.CENTER_VERTICAL);
        inputBox.setBackground(bordered(Color.WHITE, 12, Color.parseColor("#C9D4E2")));
        inputBox.setPadding(dp(12), 0, dp(6), 0);

        TextView searchIcon = txt("🔎", 18, false);
        inputBox.addView(searchIcon);

        busca = new EditText(this);
        busca.setHint("Nome, código ou código de barras");
        busca.setTextSize(17);
        busca.setTextColor(TEXT);
        busca.setHintTextColor(Color.parseColor("#8793A5"));
        busca.setSingleLine(true);
        busca.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        busca.setInputType(InputType.TYPE_CLASS_TEXT);
        busca.setBackgroundColor(Color.TRANSPARENT);
        busca.setPadding(dp(8), 0, dp(4), 0);
        inputBox.addView(busca, new LinearLayout.LayoutParams(
                0, dp(54), 1));

        inputRow.addView(inputBox, new LinearLayout.LayoutParams(
                0, dp(56), 1));

        Button lista = filledButton("LISTA", BLUE);
        lista.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(54));
        lp.setMargins(dp(8), 0, 0, 0);
        lista.setLayoutParams(lp);
        lista.setOnClickListener(v -> abrirBuscaProduto());
        inputRow.addView(lista);

        buscaCard.addView(inputRow);

        resultados = new LinearLayout(this);
        resultados.setOrientation(LinearLayout.VERTICAL);
        resultados.setPadding(0, dp(6), 0, 0);
        buscaCard.addView(resultados);

        root.addView(buscaCard);
    }

    private void montarItens(LinearLayout root) {
        root.addView(sectionTitle("🛒", "ITENS DA VENDA"));

        itensCarrinho = new LinearLayout(this);
        itensCarrinho.setOrientation(LinearLayout.VERTICAL);
        root.addView(itensCarrinho);
    }

    private void montarFerramentas(LinearLayout root) {
        root.addView(sectionTitle("⚙", "FERRAMENTAS DO CAIXA"));

        LinearLayout tools = card();
        tools.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button desconto = filledButton("🏷  Desconto", ORANGE);
        desconto.setOnClickListener(v -> abrirDesconto());
        row1.addView(desconto, toolParams(1f, 0, dp(4)));

        Button cancelar = filledButton("✕  Cancelar venda", RED);
        cancelar.setOnClickListener(v -> cancelarVenda());
        row1.addView(cancelar, toolParams(1f, dp(4), 0));
        tools.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        r2p.setMargins(0, dp(8), 0, 0);
        row2.setLayoutParams(r2p);

        removerDesconto = outlineButton("⊖  Remover desconto", NAVY);
        removerDesconto.setOnClickListener(v -> {
            descontoEntrada = 0;
            descontoPercentual = false;
            atualizarCarrinho();
        });
        row2.addView(removerDesconto, toolParams(1f, 0, dp(4)));

        Button produtos = outlineButton("⌕  Buscar produto", NAVY);
        produtos.setOnClickListener(v -> abrirBuscaProduto());
        row2.addView(produtos, toolParams(1f, dp(4), 0));

        tools.addView(row2);
        root.addView(tools);
    }

    private LinearLayout.LayoutParams toolParams(float weight, int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, dp(52), weight);
        p.setMargins(left, 0, right, 0);
        return p;
    }

    private void montarResumo(LinearLayout root) {
        LinearLayout resumoCard = card();
        resumoCard.setLayoutParams(cardParams(14));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = iconCircle("▤", PALE_BLUE, NAVY, 38);
        title.addView(icon);

        TextView t = txt("RESUMO DA VENDA", 14, true);
        t.setTextColor(Color.parseColor("#53637A"));
        t.setPadding(dp(10), 0, 0, 0);
        title.addView(t);
        resumoCard.addView(title);

        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMargins(0, dp(12), 0, 0);

        subtotalValor = txt(moeda.format(0), 18, true);
        LinearLayout subRow = resumoRow("Subtotal", subtotalValor);
        subRow.setLayoutParams(gap);
        resumoCard.addView(subRow);

        descontoValor = txt("- " + moeda.format(0), 18, true);
        LinearLayout descRow = resumoRow("Desconto", descontoValor);
        resumoCard.addView(descRow);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams divp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divp.setMargins(0, dp(12), 0, dp(12));
        divider.setLayoutParams(divp);
        resumoCard.addView(divider);

        LinearLayout totalBox = new LinearLayout(this);
        totalBox.setOrientation(LinearLayout.HORIZONTAL);
        totalBox.setGravity(Gravity.CENTER_VERTICAL);
        totalBox.setBackground(solid(PALE_GREEN, 13));
        totalBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView money = iconCircle("$", GREEN, Color.WHITE, 42);
        totalBox.addView(money);

        TextView totalLabel = txt("TOTAL A RECEBER", 16, true);
        totalLabel.setTextColor(TEXT);
        totalLabel.setPadding(dp(12), 0, 0, 0);
        totalBox.addView(totalLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        totalValor = txt(moeda.format(0), 23, true);
        totalValor.setTextColor(GREEN);
        totalBox.addView(totalValor);

        resumoCard.addView(totalBox);
        root.addView(resumoCard);
    }

    private LinearLayout resumoRow(String label, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(5), dp(2), dp(5));

        TextView l = txt(label, 17, false);
        l.setTextColor(Color.parseColor("#4B5C72"));
        row.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.addView(value);
        return row;
    }

    private void tentarSair() {
        if (carrinho.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Sair do PDV?")
                .setMessage("Há uma venda em andamento. Os itens ainda não foram baixados do estoque.")
                .setPositiveButton("Descartar e sair", (d,w) -> finish())
                .setNegativeButton("Continuar venda", null)
                .show();
    }

    private void atualizarResultados() {
        resultados.removeAllViews();
        String q = busca.getText().toString().trim();

        if (q.isEmpty()) {
            resultados.setVisibility(View.GONE);
            return;
        }
        resultados.setVisibility(View.VISIBLE);

        List<GestaoDbHelper.Produto> produtos = db.list(q);
        if (produtos.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 13, false);
            vazio.setTextColor(RED);
            vazio.setPadding(dp(6), dp(10), dp(6), dp(6));
            resultados.addView(vazio);
            return;
        }

        int limite = Math.min(produtos.size(), 4);
        for (int i=0; i<limite; i++) {
            GestaoDbHelper.Produto p = produtos.get(i);
            resultados.addView(linhaProdutoBusca(p, null));
        }
    }

    private View linhaProdutoBusca(GestaoDbHelper.Produto produto, AlertDialog dialog) {
        boolean disponivel = produto.ehServico() || produto.estoque > 0.000001;

        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(dp(10), dp(10), dp(10), dp(10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, 0);
        linha.setLayoutParams(lp);
        linha.setBackground(solid(
                disponivel ? PALE_BLUE : Color.parseColor("#F2F4F7"), 10));

        String inicial = produto.nome == null || produto.nome.trim().isEmpty()
                ? "P" : produto.nome.trim().substring(0,1).toUpperCase(new Locale("pt","BR"));
        TextView avatar = iconCircle(inicial,
                disponivel ? Color.parseColor("#D7E9FF") : Color.parseColor("#E5E7EB"),
                disponivel ? BLUE : MUTED, 40);
        linha.addView(avatar);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(8), 0);

        TextView nome = txt(produto.nome, 14, true);
        info.addView(nome);

        String codigo = produto.codigo == null || produto.codigo.trim().isEmpty()
                ? "" : produto.codigo + " • ";
        String estoque = produto.ehServico()
                ? "Serviço"
                : "Estoque " + fmtQtd(produto.estoque) + " " + unidade(produto);

        TextView sub = txt(codigo + estoque, 11, false);
        sub.setTextColor(MUTED);
        info.addView(sub);

        linha.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView preco = txt(moeda.format(produto.precoVenda), 15, true);
        preco.setTextColor(disponivel ? GREEN : MUTED);
        linha.addView(preco);

        if (disponivel) {
            linha.setOnClickListener(v -> {
                adicionarProduto(produto);
                if (dialog != null) dialog.dismiss();
            });
        }

        return linha;
    }

    private void abrirBuscaProduto() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(8));

        EditText campo = new EditText(this);
        campo.setHint("Nome, código ou código de barras");
        campo.setSingleLine(true);
        campo.setTextSize(17);
        campo.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        campo.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(campo);

        TextView dica = txt("Toque no produto para adicionar à venda.", 12, false);
        dica.setTextColor(MUTED);
        dica.setPadding(0, dp(2), 0, dp(6));
        box.addView(dica);

        ScrollView scrollLista = new ScrollView(this);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        scrollLista.addView(lista);
        box.addView(scrollLista, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(390)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Buscar produto")
                .setView(box)
                .setNegativeButton("Fechar", null)
                .create();

        Runnable atualizar = () -> preencherBuscaProduto(
                lista, campo.getText().toString(), dialog);

        campo.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                atualizar.run();
            }
            public void afterTextChanged(Editable e) {}
        });

        campo.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE) {
                List<GestaoDbHelper.Produto> r = db.list(campo.getText().toString());
                if (r.size() == 1) {
                    adicionarProduto(r.get(0));
                    dialog.dismiss();
                }
                return true;
            }
            return false;
        });

        dialog.setOnShowListener(x -> atualizar.run());
        dialog.show();
    }

    private void preencherBuscaProduto(LinearLayout lista, String termo, AlertDialog dialog) {
        lista.removeAllViews();
        List<GestaoDbHelper.Produto> produtos = db.list(
                termo == null ? "" : termo.trim());

        if (produtos.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 14, false);
            vazio.setTextColor(RED);
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(8), dp(26), dp(8), dp(26));
            lista.addView(vazio);
            return;
        }

        int limite = Math.min(produtos.size(), 50);
        for (int i=0; i<limite; i++) {
            lista.addView(linhaProdutoBusca(produtos.get(i), dialog));
        }
    }

    private void adicionarCorrespondenciaDireta() {
        String q = busca.getText().toString().trim();
        if (q.isEmpty()) return;

        List<GestaoDbHelper.Produto> produtos = db.list(q);
        if (produtos.isEmpty()) return;

        GestaoDbHelper.Produto escolhido = null;
        for (GestaoDbHelper.Produto p : produtos) {
            if (q.equalsIgnoreCase(p.codigo) ||
                    q.equalsIgnoreCase(p.codigoBarras)) {
                escolhido = p;
                break;
            }
        }
        if (escolhido == null && produtos.size() == 1) {
            escolhido = produtos.get(0);
        }
        if (escolhido != null) adicionarProduto(escolhido);
    }

    private void adicionarProduto(GestaoDbHelper.Produto produto) {
        GestaoDbHelper.VendaItem existente = localizarNoCarrinho(produto.id);

        if (existente != null) {
            double nova = existente.quantidade + 1;
            if (!produto.ehServico() &&
                    nova > produto.estoque + 0.000001) {
                Toast.makeText(this,
                        "Quantidade maior que o estoque disponível.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            existente.quantidade = nova;
        } else {
            if (!produto.ehServico() && produto.estoque < 1 - 0.000001) {
                Toast.makeText(this,
                        "Produto sem estoque suficiente.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            GestaoDbHelper.VendaItem item = new GestaoDbHelper.VendaItem();
            item.produto = produto;
            item.quantidade = 1;
            item.precoUnitario = produto.precoVenda;
            carrinho.add(item);
        }

        busca.setText("");
        atualizarCarrinho();
    }

    private GestaoDbHelper.VendaItem localizarNoCarrinho(long produtoId) {
        for (GestaoDbHelper.VendaItem item : carrinho) {
            if (item.produto.id == produtoId) return item;
        }
        return null;
    }

    private void atualizarCarrinho() {
        itensCarrinho.removeAllViews();

        double quantidadeTotal = 0;
        for (GestaoDbHelper.VendaItem item : carrinho) {
            quantidadeTotal += item.quantidade;
        }

        statusCarrinho.setText("🛒  " +
                carrinho.size() + (carrinho.size()==1 ? " item" : " itens") +
                " no carrinho  •  " + fmtQtd(quantidadeTotal) + " unidade(s)");

        if (carrinho.isEmpty()) {
            LinearLayout vazio = card();
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(18), dp(24), dp(18), dp(24));

            TextView ico = txt("🛒", 29, false);
            ico.setGravity(Gravity.CENTER);
            vazio.addView(ico);

            TextView msg = txt("Carrinho vazio", 16, true);
            msg.setGravity(Gravity.CENTER);
            msg.setPadding(0, dp(5), 0, 0);
            vazio.addView(msg);

            TextView dica = txt("Busque um produto acima para iniciar a venda.", 12, false);
            dica.setTextColor(MUTED);
            dica.setGravity(Gravity.CENTER);
            dica.setPadding(0, dp(3), 0, 0);
            vazio.addView(dica);

            itensCarrinho.addView(vazio);
        } else {
            int numero = 1;
            for (GestaoDbHelper.VendaItem item : new ArrayList<>(carrinho)) {
                itensCarrinho.addView(cardItem(item, numero));
                numero++;
            }
        }

        double subtotal = subtotal();
        double desconto = valorDesconto();
        double total = Math.max(0, subtotal - desconto);

        subtotalValor.setText(moeda.format(subtotal));

        String desc = "- " + moeda.format(desconto);
        if (desconto > 0 && descontoPercentual) {
            desc += "  (" + fmtPct(descontoEntrada) + ")";
        }
        descontoValor.setText(desc);
        descontoValor.setTextColor(desconto > 0 ? ORANGE : TEXT);

        totalValor.setText(moeda.format(total));

        boolean temVenda = !carrinho.isEmpty();
        finalizar.setEnabled(temVenda);
        finalizar.setAlpha(temVenda ? 1f : 0.45f);

        removerDesconto.setEnabled(desconto > 0);
        removerDesconto.setAlpha(desconto > 0 ? 1f : 0.45f);
    }

    private View cardItem(GestaoDbHelper.VendaItem item, int numero) {
        LinearLayout card = card();
        LinearLayout.LayoutParams cp = cardParams(numero == 1 ? 0 : 8);
        card.setLayoutParams(cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = iconCircle(String.valueOf(numero),
                Color.parseColor("#E9EEF5"), NAVY, 42);
        top.addView(badge);

        LinearLayout nomeBox = new LinearLayout(this);
        nomeBox.setOrientation(LinearLayout.VERTICAL);
        nomeBox.setPadding(dp(10), 0, dp(8), 0);

        TextView nome = txt(item.produto.nome, 16, true);
        nomeBox.addView(nome);

        TextView info = txt(
                fmtQtd(item.quantidade) + " " + unidade(item.produto) +
                        " × " + moeda.format(item.precoUnitario),
                12, false);
        info.setTextColor(MUTED);
        info.setPadding(0, dp(2), 0, 0);
        nomeBox.addView(info);

        top.addView(nomeBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView total = txt(moeda.format(item.total()), 18, true);
        total.setTextColor(GREEN);
        top.addView(total);

        card.addView(top);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ctrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        ctrp.setMargins(0, dp(12), 0, 0);
        controls.setLayoutParams(ctrp);

        Button menos = outlineButton("−", NAVY);
        menos.setTextSize(20);
        menos.setOnClickListener(v -> diminuir(item));
        controls.addView(menos, controlParams(0.65f, 0, dp(3)));

        Button qtd = outlineButton(fmtQtd(item.quantidade), NAVY);
        qtd.setTypeface(null, Typeface.BOLD);
        qtd.setOnClickListener(v -> editarQuantidade(item));
        controls.addView(qtd, controlParams(0.95f, dp(3), dp(3)));

        Button mais = outlineButton("+", BLUE);
        mais.setTextSize(19);
        mais.setTypeface(null, Typeface.BOLD);
        mais.setOnClickListener(v -> aumentar(item));
        controls.addView(mais, controlParams(0.65f, dp(3), dp(7)));

        Button preco = outlineButton("✎  Preço", NAVY);
        preco.setOnClickListener(v -> editarPreco(item));
        controls.addView(preco, controlParams(1.15f, 0, dp(4)));

        Button excluir = outlineButton("Excluir", RED);
        excluir.setBackground(bordered(PALE_RED, 10, Color.parseColor("#FFD1CC")));
        excluir.setOnClickListener(v -> confirmarRemocao(item));
        controls.addView(excluir, controlParams(1.05f, dp(4), 0));

        card.addView(controls);
        return card;
    }

    private LinearLayout.LayoutParams controlParams(float weight, int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, dp(46), weight);
        p.setMargins(left, 0, right, 0);
        return p;
    }

    private void diminuir(GestaoDbHelper.VendaItem item) {
        if (item.quantidade - 1 <= 0.000001) {
            confirmarRemocao(item);
            return;
        }
        item.quantidade -= 1;
        atualizarCarrinho();
    }

    private void aumentar(GestaoDbHelper.VendaItem item) {
        double nova = item.quantidade + 1;
        if (!item.produto.ehServico() &&
                nova > item.produto.estoque + 0.000001) {
            Toast.makeText(this,
                    "Limite do estoque: " + fmtQtd(item.produto.estoque),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        item.quantidade = nova;
        atualizarCarrinho();
    }

    private boolean fracionada(GestaoDbHelper.Produto p) {
        String u = unidade(p).toUpperCase(new Locale("pt","BR"));
        return u.equals("KG") || u.equals("G") || u.equals("M") ||
                u.equals("CM") || u.equals("L") || u.equals("ML") ||
                u.equals("OUTRA");
    }

    private void editarQuantidade(GestaoDbHelper.VendaItem item) {
        EditText campo = inputNumero("Quantidade");
        campo.setText(fmtQtd(item.quantidade).replace(".", ","));
        campo.setSelectAllOnFocus(true);
        campo.setInputType(InputType.TYPE_CLASS_NUMBER |
                (fracionada(item.produto)
                        ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alterar quantidade")
                .setMessage(item.produto.nome +
                        (item.produto.ehServico() ? "" :
                                "\nDisponível: " +
                                fmtQtd(item.produto.estoque) + " " +
                                unidade(item.produto)))
                .setView(campo)
                .setPositiveButton("Aplicar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            double q = numero(campo.getText().toString());
                            if (Double.isNaN(q) || q <= 0) {
                                campo.setError("Quantidade inválida");
                                return;
                            }
                            if (!fracionada(item.produto) &&
                                    Math.abs(q - Math.rint(q)) > 0.000001) {
                                campo.setError("Use quantidade inteira para " +
                                        unidade(item.produto));
                                return;
                            }
                            if (!item.produto.ehServico() &&
                                    q > item.produto.estoque + 0.000001) {
                                campo.setError("Máximo disponível: " +
                                        fmtQtd(item.produto.estoque));
                                return;
                            }
                            item.quantidade = q;
                            dialog.dismiss();
                            atualizarCarrinho();
                        }));
        dialog.show();
    }

    private void editarPreco(GestaoDbHelper.VendaItem item) {
        EditText campo = inputNumero("Novo preço unitário");
        campo.setText(String.format(new Locale("pt","BR"),
                "%.2f", item.precoUnitario));
        campo.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alterar preço")
                .setMessage(item.produto.nome +
                        "\nPreço cadastrado: " +
                        moeda.format(item.produto.precoVenda))
                .setView(campo)
                .setPositiveButton("Aplicar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            double preco = numero(campo.getText().toString());
                            if (Double.isNaN(preco) || preco < 0) {
                                campo.setError("Preço inválido");
                                return;
                            }
                            item.precoUnitario = preco;
                            dialog.dismiss();
                            atualizarCarrinho();
                        }));
        dialog.show();
    }

    private void confirmarRemocao(GestaoDbHelper.VendaItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir item?")
                .setMessage(item.produto.nome + "\n" +
                        fmtQtd(item.quantidade) + " " +
                        unidade(item.produto))
                .setPositiveButton("Excluir", (d,w) -> {
                    carrinho.remove(item);
                    atualizarCarrinho();
                })
                .setNegativeButton("Manter", null)
                .show();
    }

    private void cancelarVenda() {
        if (carrinho.isEmpty()) {
            Toast.makeText(this,
                    "Não há venda em andamento.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Cancelar venda atual?")
                .setMessage("Todos os itens e o desconto serão removidos. " +
                        "O estoque não será alterado.")
                .setPositiveButton("Cancelar venda", (d,w) -> {
                    carrinho.clear();
                    descontoEntrada = 0;
                    descontoPercentual = false;
                    busca.setText("");
                    atualizarCarrinho();
                    Toast.makeText(this,
                            "Venda cancelada.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Continuar venda", null)
                .show();
    }

    private void abrirDesconto() {
        if (carrinho.isEmpty()) {
            Toast.makeText(this,
                    "Adicione um produto antes de aplicar desconto.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(8));

        Spinner tipo = new Spinner(this);
        String[] tipos = {"Desconto em R$", "Desconto em %"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, tipos);
        ad.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        tipo.setAdapter(ad);
        tipo.setSelection(descontoPercentual ? 1 : 0);
        box.addView(tipo);

        EditText valor = inputNumero("Valor do desconto");
        if (descontoEntrada > 0) {
            valor.setText(String.format(new Locale("pt","BR"),
                    "%.2f", descontoEntrada));
        }
        box.addView(valor);

        TextView limite = txt("Subtotal atual: " +
                moeda.format(subtotal()), 13, false);
        limite.setTextColor(MUTED);
        limite.setPadding(0, dp(8), 0, 0);
        box.addView(limite);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Aplicar desconto")
                .setView(box)
                .setPositiveButton("Aplicar", null)
                .setNeutralButton("Remover", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        double d = numero(valor.getText().toString());
                        if (Double.isNaN(d) || d < 0) {
                            valor.setError("Informe um desconto válido");
                            return;
                        }

                        boolean percentual =
                                tipo.getSelectedItemPosition() == 1;
                        if (percentual && d > 100) {
                            valor.setError("O percentual não pode passar de 100%");
                            return;
                        }
                        if (!percentual && d > subtotal() + 0.001) {
                            valor.setError("O desconto não pode passar do subtotal");
                            return;
                        }

                        descontoPercentual = percentual;
                        descontoEntrada = d;
                        dialog.dismiss();
                        atualizarCarrinho();
                    });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> {
                        descontoEntrada = 0;
                        descontoPercentual = false;
                        dialog.dismiss();
                        atualizarCarrinho();
                    });
        });
        dialog.show();
    }

    private void abrirPagamento() {
        if (carrinho.isEmpty()) return;

        double subtotal = subtotal();
        double desconto = valorDesconto();
        double total = Math.max(0, subtotal - desconto);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(8));

        TextView totalTxt = txt("TOTAL A RECEBER\n" +
                moeda.format(total), 23, true);
        totalTxt.setTextColor(GREEN);
        totalTxt.setGravity(Gravity.CENTER);
        totalTxt.setPadding(0, 0, 0, dp(12));
        box.addView(totalTxt);

        if (desconto > 0) {
            TextView desc = txt("Subtotal " + moeda.format(subtotal) +
                    "  •  desconto " + moeda.format(desconto), 12, false);
            desc.setTextColor(MUTED);
            desc.setGravity(Gravity.CENTER);
            desc.setPadding(0, 0, 0, dp(8));
            box.addView(desc);
        }

        TextView formaLabel = txt("Forma de pagamento", 13, true);
        formaLabel.setPadding(0, dp(4), 0, dp(3));
        box.addView(formaLabel);

        Spinner forma = new Spinner(this);
        String[] formas = {
                "DINHEIRO", "PIX", "CARTÃO DÉBITO",
                "CARTÃO CRÉDITO", "MISTO"
        };
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, formas);
        ad.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        forma.setAdapter(ad);
        box.addView(forma);

        EditText recebido = inputNumero("Valor recebido em dinheiro");
        EditText mistoDinheiro = inputNumero("Dinheiro");
        EditText mistoPix = inputNumero("PIX");
        EditText mistoCartao = inputNumero("Cartão");

        TextView troco = txt("", 16, true);
        troco.setTextColor(GREEN);
        troco.setPadding(0, dp(6), 0, 0);

        box.addView(recebido);
        box.addView(mistoDinheiro);
        box.addView(mistoPix);
        box.addView(mistoCartao);
        box.addView(troco);

        Runnable ajustarVisibilidade = () -> {
            String f = forma.getSelectedItem().toString();
            recebido.setVisibility(
                    f.equals("DINHEIRO") ? View.VISIBLE : View.GONE);
            boolean misto = f.equals("MISTO");
            mistoDinheiro.setVisibility(misto ? View.VISIBLE : View.GONE);
            mistoPix.setVisibility(misto ? View.VISIBLE : View.GONE);
            mistoCartao.setVisibility(misto ? View.VISIBLE : View.GONE);
            troco.setVisibility(
                    f.equals("DINHEIRO") ? View.VISIBLE : View.GONE);
        };

        Runnable atualizarTroco = () -> {
            if (!forma.getSelectedItem().toString()
                    .equals("DINHEIRO")) return;

            double r = numero(recebido.getText().toString());
            if (Double.isNaN(r)) r = 0;

            double t = r - total;
            troco.setText(t >= 0
                    ? "Troco: " + moeda.format(t)
                    : "Falta: " + moeda.format(-t));
            troco.setTextColor(t >= 0 ? GREEN : RED);
        };

        forma.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(
                            AdapterView<?> parent, View view,
                            int position, long id) {
                        ajustarVisibilidade.run();
                        atualizarTroco.run();
                    }
                    @Override public void onNothingSelected(
                            AdapterView<?> parent) {}
                });

        recebido.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                atualizarTroco.run();
            }
            public void afterTextChanged(Editable e) {}
        });

        ajustarVisibilidade.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Finalizar venda")
                .setView(box)
                .setPositiveButton("CONFIRMAR VENDA", null)
                .setNegativeButton("Voltar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String f = forma.getSelectedItem().toString();
                            GestaoDbHelper.Pagamento pg =
                                    new GestaoDbHelper.Pagamento();
                            pg.forma = f;

                            if (f.equals("DINHEIRO")) {
                                double r = numero(
                                        recebido.getText().toString());
                                if (Double.isNaN(r) ||
                                        r + 0.001 < total) {
                                    recebido.setError(
                                            "Valor recebido deve ser igual ou maior que o total");
                                    return;
                                }
                                pg.dinheiro = total;
                                pg.recebido = r;
                                pg.troco = r - total;
                            } else if (f.equals("PIX")) {
                                pg.pix = total;
                                pg.recebido = total;
                            } else if (f.startsWith("CARTÃO")) {
                                pg.cartao = total;
                                pg.recebido = total;
                            } else {
                                double d = zeroSeVazio(mistoDinheiro);
                                double p = zeroSeVazio(mistoPix);
                                double c = zeroSeVazio(mistoCartao);

                                if (Double.isNaN(d) ||
                                        Double.isNaN(p) ||
                                        Double.isNaN(c)) {
                                    Toast.makeText(this,
                                            "Confira os valores do pagamento misto.",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                double soma = d + p + c;
                                if (Math.abs(soma - total) > 0.011) {
                                    Toast.makeText(this,
                                            "Pagamento misto soma " +
                                                    moeda.format(soma) +
                                                    " e precisa fechar " +
                                                    moeda.format(total),
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                pg.dinheiro = d;
                                pg.pix = p;
                                pg.cartao = c;
                                pg.recebido = total;
                            }

                            try {
                                String tipoDesc = desconto > 0
                                        ? (descontoPercentual
                                        ? "PERCENTUAL" : "VALOR")
                                        : "";

                                long idVenda = db.finalizarVenda(
                                        carrinho, pg, desconto,
                                        tipoDesc, descontoEntrada);

                                double trocoFinal = pg.troco;
                                double totalFinal = total;

                                carrinho.clear();
                                descontoEntrada = 0;
                                descontoPercentual = false;
                                dialog.dismiss();
                                busca.setText("");
                                atualizarResultados();
                                atualizarCarrinho();

                                mostrarPosVenda(
                                        idVenda, totalFinal, trocoFinal);

                            } catch (Exception ex) {
                                Toast.makeText(this,
                                        ex.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                atualizarResultados();
                            }
                        }));

        dialog.show();
    }

    private void mostrarPosVenda(long vendaId, double total, double troco) {
        String msg = "Venda #" + vendaId +
                "\nTotal: " + moeda.format(total);
        if (troco > 0.001) {
            msg += "\nTroco: " + moeda.format(troco);
        }
        msg += "\n\nEstoque atualizado automaticamente.";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Venda concluída ✓")
                .setMessage(msg)
                .setPositiveButton("Nova venda", null)
                .setNegativeButton("Comprovante", null)
                .setNeutralButton("Emitir nota", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> dialog.dismiss());

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> compartilharComprovante(vendaId));

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> abrirEmissaoNota(vendaId));
        });

        dialog.show();
    }

    private void abrirEmissaoNota(long vendaId) {
        if (!ConfiguracoesFiscaisActivity.estaMinimamenteConfigurada(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Dados da empresa incompletos")
                    .setMessage("Antes de preparar NFC-e ou NF-e, cadastre os dados fiscais da empresa emitente.")
                    .setPositiveButton("Configurar agora", (d,w) ->
                            startActivity(new Intent(this, ConfiguracoesFiscaisActivity.class)))
                    .setNegativeButton("Depois", null)
                    .show();
            return;
        }

        String[] opcoes = {
                "NFC-e • Cupom fiscal",
                "NF-e • Nota fiscal"
        };

        new AlertDialog.Builder(this)
                .setTitle("Qual documento deseja emitir?")
                .setItems(opcoes, (d, which) -> {
                    if (which == 0) abrirNfce(vendaId);
                    else abrirNfe(vendaId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrirNfce(long vendaId) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(8));

        TextView info = txt(
                "NFC-e / cupom fiscal\nCPF ou CNPJ do consumidor é opcional nesta tela.",
                13, false);
        info.setTextColor(MUTED);
        info.setPadding(0, 0, 0, dp(10));
        box.addView(info);

        EditText documento = new EditText(this);
        documento.setHint("CPF/CNPJ do consumidor (opcional)");
        documento.setSingleLine(true);
        documento.setInputType(InputType.TYPE_CLASS_NUMBER);
        CadastroBrasilUtils.aplicarMascaraDocumento(documento,
                () -> CadastroBrasilUtils.apenasDigitos(documento.getText().toString()).length() > 11);
        box.addView(documento);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Preparar NFC-e")
                .setView(box)
                .setPositiveButton("Registrar NFC-e", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String doc = documento.getText().toString()
                                    .replaceAll("[^0-9]", "");

                            if (!doc.isEmpty()) {
                                boolean valido = doc.length() == 11
                                        ? CadastroBrasilUtils.cpfValido(doc)
                                        : doc.length() == 14 && CadastroBrasilUtils.cnpjValido(doc);
                                if (!valido) {
                                    documento.setError("CPF/CNPJ inválido");
                                    return;
                                }
                            }

                            db.registrarSolicitacaoNfce(vendaId, doc);
                            dialog.dismiss();
                            mostrarNotaPreparada(vendaId, "NFC-e");
                        }));
        dialog.show();
    }

    private EditText campoFiscal(LinearLayout root, String hint, boolean numerico) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        if (numerico) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(e);
        return e;
    }

    private void abrirNfe(long vendaId) {
        List<GestaoDbHelper.Cliente> clientes = db.listClientes("");
        if (clientes.isEmpty()) {
            abrirNfeManual(vendaId);
            return;
        }

        String[] opcoes = new String[]{
                "Selecionar cliente cadastrado",
                "Preencher destinatário agora"
        };

        new AlertDialog.Builder(this)
                .setTitle("Destinatário da NF-e")
                .setItems(opcoes, (d, which) -> {
                    if (which == 0) selecionarClienteNfe(vendaId);
                    else abrirNfeManual(vendaId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void selecionarClienteNfe(long vendaId) {
        List<GestaoDbHelper.Cliente> clientes = db.listClientes("");
        String[] nomes = new String[clientes.size()];
        for (int i=0; i<clientes.size(); i++) {
            GestaoDbHelper.Cliente c = clientes.get(i);
            nomes[i] = c.nome + " • " + c.tipo + " • " + c.documento;
        }

        new AlertDialog.Builder(this)
                .setTitle("Selecionar cliente")
                .setItems(nomes, (d, which) -> registrarNfeCliente(vendaId, clientes.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void registrarNfeCliente(long vendaId, GestaoDbHelper.Cliente c) {
        if (c.logradouro == null || c.logradouro.trim().isEmpty() ||
                c.numero == null || c.numero.trim().isEmpty() ||
                c.bairro == null || c.bairro.trim().isEmpty() ||
                c.cep == null || c.cep.replaceAll("[^0-9]", "").length() != 8 ||
                c.municipio == null || c.municipio.trim().isEmpty() ||
                c.uf == null || c.uf.trim().length() != 2) {
            new AlertDialog.Builder(this)
                    .setTitle("Cadastro incompleto")
                    .setMessage("Este cliente precisa de endereço completo para a NF-e. Abra o módulo Clientes e complete o cadastro.")
                    .setPositiveButton("Abrir Clientes", (d,w) ->
                            startActivity(new Intent(this, ClientesActivity.class)))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        db.registrarSolicitacaoNfe(
                vendaId, c.nome, c.documento, c.ie,
                c.logradouro, c.numero, c.complemento,
                c.bairro, c.cep, c.municipio, c.uf,
                c.telefone, c.email);
        mostrarNotaPreparada(vendaId, "NF-e");
    }

    private void abrirNfeManual(long vendaId) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(12));
        scroll.addView(box);

        TextView info = txt(
                "NF-e / nota fiscal\nPreencha os dados do destinatário.",
                13, false);
        info.setTextColor(MUTED);
        info.setPadding(0, 0, 0, dp(8));
        box.addView(info);

        EditText nome = campoFiscal(box, "Nome / Razão social *", false);
        EditText documento = campoFiscal(box, "CPF / CNPJ *", true);
        EditText ie = campoFiscal(box, "Inscrição Estadual (quando houver)", false);
        EditText logradouro = campoFiscal(box, "Logradouro *", false);
        EditText numero = campoFiscal(box, "Número *", false);
        EditText complemento = campoFiscal(box, "Complemento", false);
        EditText bairro = campoFiscal(box, "Bairro *", false);
        EditText cep = campoFiscal(box, "CEP *", true);
        EditText municipio = campoFiscal(box, "Município *", false);
        EditText uf = campoFiscal(box, "UF *", false);
        EditText telefone = campoFiscal(box, "Telefone", false);
        EditText email = campoFiscal(box, "E-mail", false);

        CadastroBrasilUtils.aplicarMascaraDocumento(documento,
                () -> CadastroBrasilUtils.apenasDigitos(documento.getText().toString()).length() > 11);
        CadastroBrasilUtils.aplicarMascaraCep(cep);

        final String[] ultimoCepNfe = {""};
        cep.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String d = CadastroBrasilUtils.apenasDigitos(e.toString());
                if (d.length() != 8 || d.equals(ultimoCepNfe[0])) return;
                ultimoCepNfe[0] = d;
                CadastroBrasilUtils.buscarCep(PdvActivity.this, d,
                        new CadastroBrasilUtils.CepCallback() {
                            @Override public void onSuccess(CadastroBrasilUtils.CepData x) {
                                if (!x.logradouro.isEmpty()) logradouro.setText(x.logradouro);
                                if (!x.bairro.isEmpty()) bairro.setText(x.bairro);
                                if (!x.municipio.isEmpty()) municipio.setText(x.municipio);
                                if (!x.uf.isEmpty()) uf.setText(x.uf);
                                numero.requestFocus();
                            }
                            @Override public void onError(String mensagem) {
                                Toast.makeText(PdvActivity.this, mensagem, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Preparar NF-e")
                .setView(scroll)
                .setPositiveButton("Registrar NF-e", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String doc = documento.getText().toString().replaceAll("[^0-9]", "");
                            String cepLimpo = cep.getText().toString().replaceAll("[^0-9]", "");
                            String ufTxt = uf.getText().toString().trim().toUpperCase();

                            if (nome.getText().toString().trim().isEmpty()) {
                                nome.setError("Informe o destinatário");
                                return;
                            }
                            boolean docValido = doc.length() == 11
                                    ? CadastroBrasilUtils.cpfValido(doc)
                                    : doc.length() == 14 && CadastroBrasilUtils.cnpjValido(doc);
                            if (!docValido) {
                                documento.setError("CPF/CNPJ inválido");
                                return;
                            }
                            if (logradouro.getText().toString().trim().isEmpty()) {
                                logradouro.setError("Informe o logradouro");
                                return;
                            }
                            if (numero.getText().toString().trim().isEmpty()) {
                                numero.setError("Informe o número");
                                return;
                            }
                            if (bairro.getText().toString().trim().isEmpty()) {
                                bairro.setError("Informe o bairro");
                                return;
                            }
                            if (cepLimpo.length() != 8) {
                                cep.setError("Informe CEP com 8 dígitos");
                                return;
                            }
                            if (municipio.getText().toString().trim().isEmpty()) {
                                municipio.setError("Informe o município");
                                return;
                            }
                            if (ufTxt.length() != 2) {
                                uf.setError("Informe a UF com 2 letras");
                                return;
                            }

                            db.registrarSolicitacaoNfe(
                                    vendaId,
                                    nome.getText().toString(),
                                    doc,
                                    ie.getText().toString(),
                                    logradouro.getText().toString(),
                                    numero.getText().toString(),
                                    complemento.getText().toString(),
                                    bairro.getText().toString(),
                                    cepLimpo,
                                    municipio.getText().toString(),
                                    ufTxt,
                                    telefone.getText().toString(),
                                    email.getText().toString());

                            dialog.dismiss();
                            mostrarNotaPreparada(vendaId, "NF-e");
                        }));
        dialog.show();
    }

    private void mostrarNotaPreparada(long vendaId, String tipo) {
        new AlertDialog.Builder(this)
                .setTitle(tipo + " preparada")
                .setMessage(
                        "A venda #" + vendaId +
                        " ficou registrada para emissão de " + tipo + ".\n\n" +
                        "Quando conectarmos certificado digital, CSC e serviços da SEFAZ, " +
                        "o sistema fará a transmissão fiscal real usando estes mesmos dados.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void compartilharComprovante(long vendaId) {
        Intent i = new Intent(this, ComprovanteVendaActivity.class);
        i.putExtra("venda_id", vendaId);
        startActivity(i);
    }

    private String montarTextoComprovante(GestaoDbHelper.VendaDetalhe venda) {
        SimpleDateFormat df = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm", new Locale("pt","BR"));

        StringBuilder s = new StringBuilder();
        s.append(ConfiguracoesFiscaisActivity.nomeEmpresa(this)).append("\n");
        s.append("COMPROVANTE DE VENDA - NÃO FISCAL\n");
        s.append("--------------------------------\n");
        s.append("Venda #").append(venda.id).append("\n");
        s.append("Data: ").append(
                df.format(new Date(venda.dataMillis))).append("\n");
        s.append("--------------------------------\n");

        for (GestaoDbHelper.VendaItemRegistro item : venda.itens) {
            s.append(item.nome).append("\n");
            s.append(fmtQtd(item.quantidade))
                    .append(" ")
                    .append(item.unidade == null || item.unidade.trim().isEmpty()
                            ? "UN" : item.unidade)
                    .append(" x ")
                    .append(moeda.format(item.precoUnitario))
                    .append(" = ")
                    .append(moeda.format(item.total))
                    .append("\n");
        }

        s.append("--------------------------------\n");
        s.append("Subtotal: ").append(
                moeda.format(venda.subtotal)).append("\n");
        s.append("Desconto: - ").append(
                moeda.format(venda.desconto)).append("\n");
        s.append("TOTAL: ").append(
                moeda.format(venda.total)).append("\n");
        s.append("Pagamento: ").append(
                venda.formaPagamento).append("\n");

        if (venda.troco > 0.001) {
            s.append("Troco: ").append(
                    moeda.format(venda.troco)).append("\n");
        }

        s.append("--------------------------------\n");
        s.append("Obrigado pela preferência!\n");
        return s.toString();
    }

    private EditText inputNumero(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private double zeroSeVazio(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        return numero(s);
    }

    private double subtotal() {
        double total = 0;
        for (GestaoDbHelper.VendaItem item : carrinho) {
            total += item.total();
        }
        return total;
    }

    private double valorDesconto() {
        if (descontoEntrada <= 0) return 0;
        double sub = subtotal();
        if (descontoPercentual) {
            return Math.min(sub,
                    sub * (descontoEntrada / 100.0));
        }
        return Math.min(sub, descontoEntrada);
    }

    private String unidade(GestaoDbHelper.Produto p) {
        return p.unidade == null || p.unidade.trim().isEmpty()
                ? "UN" : p.unidade.trim();
    }

    private String fmtQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) {
            return String.valueOf((long)Math.rint(v));
        }
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$","")
                .replaceAll("\\.$","");
    }

    private String fmtPct(double v) {
        return String.format(new Locale("pt","BR"),
                "%.2f%%", v);
    }

    private double numero(String raw) {
        if (raw == null) return Double.NaN;

        String s = raw.trim()
                .replace("R$", "")
                .replace(" ", "");

        if (s.isEmpty()) return Double.NaN;

        int c = s.lastIndexOf(',');
        int d = s.lastIndexOf('.');

        try {
            if (c >= 0 && d >= 0) {
                if (c > d) {
                    s = s.replace(".", "")
                            .replace(",", ".");
                } else {
                    s = s.replace(",", "");
                }
            } else if (c >= 0) {
                s = s.replace(",", ".");
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
