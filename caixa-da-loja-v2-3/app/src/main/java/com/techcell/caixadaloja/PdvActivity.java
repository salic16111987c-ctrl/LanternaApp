package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
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
    private TextView resumo;
    private TextView statusCarrinho;
    private Button finalizar;

    private boolean descontoPercentual = false;
    private double descontoEntrada = 0;

    private final int NAVY = Color.parseColor("#101828");
    private final int BLUE = Color.parseColor("#175CD3");
    private final int GREEN = Color.parseColor("#067647");
    private final int RED = Color.parseColor("#B42318");
    private final int ORANGE = Color.parseColor("#B54708");
    private final int BG = Color.parseColor("#F2F4F7");
    private final int BORDER = Color.parseColor("#D0D5DD");
    private final int MUTED = Color.parseColor("#667085");

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable cardBg() {
        GradientDrawable d = bg(Color.WHITE, 12);
        d.setStroke(dp(1), Color.parseColor("#E4E7EC"));
        return d;
    }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(NAVY);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button action(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(bg(color, 10));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    private Button lightAction(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(NAVY);
        b.setTextSize(13);
        b.setAllCaps(false);
        GradientDrawable d = bg(Color.WHITE, 8);
        d.setStroke(dp(1), BORDER);
        b.setBackground(d);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            db = new GestaoDbHelper(getApplicationContext());
            db.getWritableDatabase();
            montarTela();
        } catch (Throwable e) {
            mostrarFalhaInicial(e);
        }
    }

    private void mostrarFalhaInicial(Throwable e) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(30));
        scroll.addView(root);

        root.addView(txt("PDV / Frente de Caixa", 26, true));

        TextView aviso = txt("O PDV encontrou um erro ao iniciar, mas o aplicativo permaneceu aberto.", 16, true);
        aviso.setTextColor(RED);
        aviso.setPadding(0, dp(16), 0, dp(10));
        root.addView(aviso);

        String detalhe = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            detalhe += "\n" + e.getMessage();
        }

        TextView erro = txt(detalhe, 13, false);
        erro.setTextColor(Color.parseColor("#475467"));
        erro.setBackground(cardBg());
        erro.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(erro);

        Button voltar = lightAction("Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        setContentView(scroll);
    }

    private void montarTela() {
        getWindow().setStatusBarColor(Color.parseColor("#0B1220"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), 0, dp(12), dp(28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(bg(NAVY, 0));
        header.setPadding(dp(16), dp(18), dp(16), dp(18));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(-dp(12), 0, -dp(12), dp(12));
        header.setLayoutParams(hp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button voltar = action("←", Color.parseColor("#344054"));
        voltar.setMinWidth(dp(54));
        voltar.setOnClickListener(v -> tentarSair());
        top.addView(voltar, new LinearLayout.LayoutParams(dp(58), dp(48)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = txt("TECH CELL • PDV", 24, true);
        title.setTextColor(Color.WHITE);
        titleBox.addView(title);
        TextView sub = txt("Frente de Caixa • Alpha 9", 12, false);
        sub.setTextColor(Color.parseColor("#D0D5DD"));
        titleBox.addView(sub);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(top);

        statusCarrinho = txt("", 13, true);
        statusCarrinho.setTextColor(Color.parseColor("#EAECF0"));
        statusCarrinho.setPadding(0, dp(12), 0, 0);
        header.addView(statusCarrinho);
        root.addView(header);

        LinearLayout buscaCard = new LinearLayout(this);
        buscaCard.setOrientation(LinearLayout.VERTICAL);
        buscaCard.setBackground(cardBg());
        buscaCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(buscaCard);

        TextView buscaLabel = txt("LOCALIZAR PRODUTO", 12, true);
        buscaLabel.setTextColor(BLUE);
        buscaCard.addView(buscaLabel);

        busca = new EditText(this);
        busca.setHint("Nome, código ou código de barras");
        busca.setTextSize(18);
        busca.setSingleLine(true);
        busca.setImeOptions(EditorInfo.IME_ACTION_DONE);
        busca.setInputType(InputType.TYPE_CLASS_TEXT);
        buscaCard.addView(busca);

        resultados = new LinearLayout(this);
        resultados.setOrientation(LinearLayout.VERTICAL);
        buscaCard.addView(resultados);

        TextView carrinhoTitle = txt("ITENS DA VENDA", 14, true);
        carrinhoTitle.setTextColor(Color.parseColor("#344054"));
        carrinhoTitle.setPadding(dp(2), dp(16), 0, dp(5));
        root.addView(carrinhoTitle);

        itensCarrinho = new LinearLayout(this);
        itensCarrinho.setOrientation(LinearLayout.VERTICAL);
        root.addView(itensCarrinho);

        TextView ferramentasTitle = txt("FERRAMENTAS DO CAIXA", 14, true);
        ferramentasTitle.setTextColor(Color.parseColor("#344054"));
        ferramentasTitle.setPadding(dp(2), dp(16), 0, dp(6));
        root.addView(ferramentasTitle);

        GridLayout tools = new GridLayout(this);
        tools.setColumnCount(2);
        tools.setUseDefaultMargins(false);

        Button desconto = action("🏷  Desconto", ORANGE);
        desconto.setOnClickListener(v -> abrirDesconto());
        addGridButton(tools, desconto, 0);

        Button cancelar = action("✕  Cancelar venda", RED);
        cancelar.setOnClickListener(v -> cancelarVenda());
        addGridButton(tools, cancelar, 1);

        Button limparDesc = lightAction("Remover desconto");
        limparDesc.setOnClickListener(v -> {
            descontoEntrada = 0;
            descontoPercentual = false;
            atualizarCarrinho();
        });
        addGridButton(tools, limparDesc, 0);

        Button focarBusca = lightAction("⌨ Buscar produto");
        focarBusca.setOnClickListener(v -> abrirBuscaProduto());
        addGridButton(tools, focarBusca, 1);

        root.addView(tools);

        resumo = txt("", 16, false);
        resumo.setBackground(cardBg());
        resumo.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(14), 0, dp(10));
        resumo.setLayoutParams(rp);
        root.addView(resumo);

        finalizar = action("FINALIZAR VENDA  →", GREEN);
        finalizar.setTextSize(18);
        finalizar.setMinHeight(dp(58));
        finalizar.setOnClickListener(v -> abrirPagamento());
        root.addView(finalizar);

        TextView rodape = txt(
                "O estoque só é baixado quando a venda é finalizada. Cancelar uma venda em andamento não altera o estoque.",
                11, false);
        rodape.setTextColor(MUTED);
        rodape.setGravity(Gravity.CENTER);
        rodape.setPadding(dp(8), dp(10), dp(8), 0);
        root.addView(rodape);

        busca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { atualizarResultados(); }
            public void afterTextChanged(Editable e) {}
        });

        busca.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                adicionarCorrespondenciaDireta();
                return true;
            }
            return false;
        });

        setContentView(scroll);
        atualizarResultados();
        atualizarCarrinho();
    }

    private void addGridButton(GridLayout grid, View view, int column) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0;
        p.height = dp(52);
        p.columnSpec = GridLayout.spec(column, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        view.setLayoutParams(p);
        grid.addView(view);
    }

    private void tentarSair() {
        if (carrinho.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Sair do PDV?")
                .setMessage("Há uma venda em andamento. Os itens do carrinho serão descartados.")
                .setPositiveButton("Descartar e sair", (d,w) -> finish())
                .setNegativeButton("Continuar venda", null)
                .show();
    }

    private void atualizarResultados() {
        resultados.removeAllViews();
        String q = busca.getText().toString().trim();

        if (q.isEmpty()) return;

        List<GestaoDbHelper.Produto> ps = db.list(q);
        if (ps.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 13, false);
            vazio.setTextColor(RED);
            vazio.setPadding(0, dp(6), 0, 0);
            resultados.addView(vazio);
            return;
        }

        int limite = Math.min(ps.size(), 6);
        for (int i=0; i<limite; i++) {
            GestaoDbHelper.Produto p = ps.get(i);

            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.VERTICAL);
            linha.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, 0);
            linha.setLayoutParams(lp);

            boolean disponivel = p.ehServico() || p.estoque > 0.000001;
            linha.setBackground(bg(disponivel ? Color.parseColor("#EFF8FF") : Color.parseColor("#F2F4F7"), 8));

            TextView nome = txt(p.nome, 15, true);
            linha.addView(nome);

            String estoque = p.ehServico() ? "Serviço" : "Estoque: " + fmtQtd(p.estoque) + " " + unidade(p);
            TextView detalhe = txt(moeda.format(p.precoVenda) + "   •   " + estoque, 13, false);
            detalhe.setTextColor(disponivel ? BLUE : MUTED);
            linha.addView(detalhe);

            if (disponivel) linha.setOnClickListener(v -> adicionarProduto(p));
            resultados.addView(linha);
        }
    }

    private void abrirBuscaProduto() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), dp(6));

        EditText campo = new EditText(this);
        campo.setHint("Digite nome, código ou código de barras");
        campo.setSingleLine(true);
        campo.setTextSize(17);
        campo.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(campo);

        TextView dica = txt("Toque em um produto para adicionar ao carrinho.", 12, false);
        dica.setTextColor(MUTED);
        dica.setPadding(0, dp(4), 0, dp(6));
        box.addView(dica);

        ScrollView scrollLista = new ScrollView(this);
        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        scrollLista.addView(lista);
        box.addView(scrollLista, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Buscar produto")
                .setView(box)
                .setNegativeButton("Fechar", null)
                .create();

        campo.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                preencherBuscaProduto(lista, s == null ? "" : s.toString(), dialog);
            }
            public void afterTextChanged(Editable e) {}
        });

        dialog.setOnShowListener(x -> {
            preencherBuscaProduto(lista, "", dialog);
            campo.requestFocus();
        });
        dialog.show();
    }

    private void preencherBuscaProduto(LinearLayout lista, String termo, AlertDialog dialog) {
        lista.removeAllViews();

        List<GestaoDbHelper.Produto> produtos = db.list(termo == null ? "" : termo.trim());
        if (produtos.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 14, false);
            vazio.setTextColor(RED);
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(8), dp(22), dp(8), dp(22));
            lista.addView(vazio);
            return;
        }

        int limite = Math.min(produtos.size(), 40);
        for (int i=0; i<limite; i++) {
            GestaoDbHelper.Produto produto = produtos.get(i);
            boolean disponivel = produto.ehServico() || produto.estoque > 0.000001;

            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.VERTICAL);
            linha.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            linha.setLayoutParams(lp);
            linha.setBackground(bg(
                    disponivel ? Color.parseColor("#EFF8FF") : Color.parseColor("#F2F4F7"), 8));

            TextView nome = txt(produto.nome, 15, true);
            linha.addView(nome);

            String cod = (produto.codigo == null || produto.codigo.trim().isEmpty())
                    ? "" : produto.codigo + "  •  ";
            String est = produto.ehServico()
                    ? "Serviço"
                    : "Estoque: " + fmtQtd(produto.estoque) + " " + unidade(produto);

            TextView detalhe = txt(cod + moeda.format(produto.precoVenda) + "  •  " + est, 12, false);
            detalhe.setTextColor(disponivel ? BLUE : MUTED);
            linha.addView(detalhe);

            if (disponivel) {
                linha.setOnClickListener(v -> {
                    adicionarProduto(produto);
                    dialog.dismiss();
                });
            }

            lista.addView(linha);
        }
    }

    private void adicionarCorrespondenciaDireta() {
        String q = busca.getText().toString().trim();
        if (q.isEmpty()) return;
        List<GestaoDbHelper.Produto> ps = db.list(q);
        if (ps.isEmpty()) return;

        GestaoDbHelper.Produto escolhido = null;
        for (GestaoDbHelper.Produto p : ps) {
            if (q.equalsIgnoreCase(p.codigo) || q.equalsIgnoreCase(p.codigoBarras)) {
                escolhido = p;
                break;
            }
        }
        if (escolhido == null && ps.size() == 1) escolhido = ps.get(0);
        if (escolhido != null) adicionarProduto(escolhido);
    }

    private void adicionarProduto(GestaoDbHelper.Produto p) {
        GestaoDbHelper.VendaItem existente = localizarNoCarrinho(p.id);

        if (existente != null) {
            double nova = existente.quantidade + 1;
            if (!p.ehServico() && nova > p.estoque + 0.000001) {
                Toast.makeText(this, "Quantidade maior que o estoque disponível.", Toast.LENGTH_SHORT).show();
                return;
            }
            existente.quantidade = nova;
        } else {
            if (!p.ehServico() && p.estoque < 1 - 0.000001) {
                Toast.makeText(this, "Produto sem estoque suficiente.", Toast.LENGTH_SHORT).show();
                return;
            }
            GestaoDbHelper.VendaItem item = new GestaoDbHelper.VendaItem();
            item.produto = p;
            item.quantidade = 1;
            item.precoUnitario = p.precoVenda;
            carrinho.add(item);
        }

        busca.setText("");
        atualizarCarrinho();
    }

    private GestaoDbHelper.VendaItem localizarNoCarrinho(long produtoId) {
        for (GestaoDbHelper.VendaItem i : carrinho) {
            if (i.produto.id == produtoId) return i;
        }
        return null;
    }

    private void atualizarCarrinho() {
        itensCarrinho.removeAllViews();

        int unidades = 0;
        for (GestaoDbHelper.VendaItem item : carrinho) {
            unidades += (int)Math.ceil(item.quantidade);
        }
        statusCarrinho.setText(carrinho.size() + (carrinho.size()==1 ? " item" : " itens") +
                " no carrinho  •  " + unidades + " unidade(s)");

        if (carrinho.isEmpty()) {
            TextView vazio = txt("Nenhum item adicionado. Localize um produto acima para iniciar a venda.", 14, false);
            vazio.setTextColor(MUTED);
            vazio.setGravity(Gravity.CENTER);
            vazio.setBackground(cardBg());
            vazio.setPadding(dp(16), dp(24), dp(16), dp(24));
            itensCarrinho.addView(vazio);
        } else {
            int numeroItem = 1;
            for (GestaoDbHelper.VendaItem item : new ArrayList<>(carrinho)) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(cardBg());
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.setMargins(0, dp(4), 0, dp(4));
                card.setLayoutParams(cp);

                LinearLayout cab = new LinearLayout(this);
                cab.setOrientation(LinearLayout.HORIZONTAL);
                cab.setGravity(Gravity.CENTER_VERTICAL);

                TextView nome = txt(numeroItem + ". " + item.produto.nome, 16, true);
                cab.addView(nome, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView totalItem = txt(moeda.format(item.total()), 16, true);
                totalItem.setTextColor(GREEN);
                cab.addView(totalItem);
                card.addView(cab);

                TextView info = txt(
                        fmtQtd(item.quantidade) + " " + unidade(item.produto) +
                        " × " + moeda.format(item.precoUnitario),
                        12, false);
                info.setTextColor(MUTED);
                info.setPadding(0, dp(4), 0, dp(6));
                card.addView(info);

                LinearLayout botoes = new LinearLayout(this);
                botoes.setOrientation(LinearLayout.HORIZONTAL);

                Button menos = lightAction("−");
                menos.setOnClickListener(v -> diminuir(item));
                botoes.addView(menos, lpPeso(0.7f));

                Button qtd = lightAction("Qtd.");
                qtd.setOnClickListener(v -> editarQuantidade(item));
                botoes.addView(qtd, lpPeso(1f));

                Button mais = lightAction("+");
                mais.setOnClickListener(v -> aumentar(item));
                botoes.addView(mais, lpPeso(0.7f));

                Button preco = lightAction("Preço");
                preco.setOnClickListener(v -> editarPreco(item));
                botoes.addView(preco, lpPeso(1f));

                Button remover = lightAction("Excluir");
                remover.setTextColor(RED);
                remover.setOnClickListener(v -> confirmarRemocao(item));
                botoes.addView(remover, lpPeso(1f));

                card.addView(botoes);
                itensCarrinho.addView(card);
                numeroItem++;
            }
        }

        double subtotal = subtotal();
        double desconto = valorDesconto();
        double total = Math.max(0, subtotal - desconto);
        String descTexto = desconto > 0
                ? (descontoPercentual
                    ? moeda.format(desconto) + "  (" + fmtPct(descontoEntrada) + ")"
                    : moeda.format(desconto))
                : moeda.format(0);

        String texto = "Subtotal                                      " + moeda.format(subtotal) +
                "\nDesconto                                      − " + descTexto +
                "\n────────────────────────" +
                "\nTOTAL A RECEBER                      " + moeda.format(total);

        resumo.setText(texto);
        resumo.setTypeface(null, Typeface.BOLD);
        resumo.setTextColor(NAVY);

        finalizar.setEnabled(!carrinho.isEmpty() && total >= 0);
        finalizar.setAlpha(finalizar.isEnabled() ? 1f : 0.5f);
    }

    private LinearLayout.LayoutParams lpPeso(float peso) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), peso);
        p.setMargins(dp(2), 0, dp(2), 0);
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
        if (!item.produto.ehServico() && nova > item.produto.estoque + 0.000001) {
            Toast.makeText(this, "Limite do estoque: " + fmtQtd(item.produto.estoque), Toast.LENGTH_SHORT).show();
            return;
        }
        item.quantidade = nova;
        atualizarCarrinho();
    }

    private boolean fracionada(GestaoDbHelper.Produto p) {
        String u = unidade(p).toUpperCase(new Locale("pt","BR"));
        return u.equals("KG") || u.equals("G") || u.equals("M") || u.equals("CM") ||
                u.equals("L") || u.equals("ML") || u.equals("OUTRA");
    }

    private void editarQuantidade(GestaoDbHelper.VendaItem item) {
        EditText campo = inputNumero("Quantidade");
        campo.setText(fmtQtd(item.quantidade).replace(".", ","));
        campo.setSelectAllOnFocus(true);
        campo.setInputType(InputType.TYPE_CLASS_NUMBER |
                (fracionada(item.produto) ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alterar quantidade")
                .setMessage(item.produto.nome + (item.produto.ehServico() ? "" :
                        "\nDisponível: " + fmtQtd(item.produto.estoque) + " " + unidade(item.produto)))
                .setView(campo)
                .setPositiveButton("Aplicar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double q = numero(campo.getText().toString());
            if (Double.isNaN(q) || q <= 0) {
                campo.setError("Quantidade inválida");
                return;
            }
            if (!fracionada(item.produto) && Math.abs(q - Math.rint(q)) > 0.000001) {
                campo.setError("Use quantidade inteira para " + unidade(item.produto));
                return;
            }
            if (!item.produto.ehServico() && q > item.produto.estoque + 0.000001) {
                campo.setError("Máximo disponível: " + fmtQtd(item.produto.estoque));
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
        campo.setText(String.format(new Locale("pt","BR"), "%.2f", item.precoUnitario));
        campo.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alterar preço do item")
                .setMessage(item.produto.nome +
                        "\nPreço cadastrado: " + moeda.format(item.produto.precoVenda))
                .setView(campo)
                .setPositiveButton("Aplicar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
                .setTitle("Remover item?")
                .setMessage(item.produto.nome + "\n" + fmtQtd(item.quantidade) + " " + unidade(item.produto))
                .setPositiveButton("Remover", (d,w) -> {
                    carrinho.remove(item);
                    atualizarCarrinho();
                })
                .setNegativeButton("Manter", null)
                .show();
    }

    private void cancelarVenda() {
        if (carrinho.isEmpty()) {
            Toast.makeText(this, "Não há venda em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cancelar venda atual?")
                .setMessage("Todos os itens e o desconto serão removidos. O estoque não será alterado.")
                .setPositiveButton("Cancelar venda", (d,w) -> {
                    carrinho.clear();
                    descontoEntrada = 0;
                    descontoPercentual = false;
                    busca.setText("");
                    atualizarCarrinho();
                    Toast.makeText(this, "Venda cancelada.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Continuar venda", null)
                .show();
    }

    private void abrirDesconto() {
        if (carrinho.isEmpty()) {
            Toast.makeText(this, "Adicione um produto antes de aplicar desconto.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(6));

        Spinner tipo = new Spinner(this);
        String[] tipos = {"Desconto em R$", "Desconto em %"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tipo.setAdapter(ad);
        tipo.setSelection(descontoPercentual ? 1 : 0);
        box.addView(tipo);

        EditText valor = inputNumero("Valor do desconto");
        if (descontoEntrada > 0) {
            valor.setText(String.format(new Locale("pt","BR"), "%.2f", descontoEntrada));
        }
        box.addView(valor);

        TextView limite = txt("Subtotal atual: " + moeda.format(subtotal()), 13, false);
        limite.setTextColor(MUTED);
        limite.setPadding(0, dp(8), 0, 0);
        box.addView(limite);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Desconto da venda")
                .setView(box)
                .setPositiveButton("Aplicar desconto", null)
                .setNeutralButton("Remover desconto", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                double d = numero(valor.getText().toString());
                if (Double.isNaN(d) || d < 0) {
                    valor.setError("Informe um desconto válido");
                    return;
                }

                boolean percentual = tipo.getSelectedItemPosition() == 1;
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

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
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
        box.setPadding(dp(20), dp(6), dp(20), dp(6));

        TextView totalTxt = txt("TOTAL A RECEBER\n" + moeda.format(total), 22, true);
        totalTxt.setTextColor(GREEN);
        totalTxt.setGravity(Gravity.CENTER);
        totalTxt.setPadding(0, 0, 0, dp(12));
        box.addView(totalTxt);

        if (desconto > 0) {
            TextView desc = txt("Subtotal " + moeda.format(subtotal) + "  •  desconto " + moeda.format(desconto), 12, false);
            desc.setTextColor(MUTED);
            desc.setGravity(Gravity.CENTER);
            box.addView(desc);
        }

        Spinner forma = new Spinner(this);
        String[] formas = {"DINHEIRO", "PIX", "CARTÃO DÉBITO", "CARTÃO CRÉDITO", "MISTO"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formas);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        forma.setAdapter(ad);
        box.addView(forma);

        EditText recebido = inputNumero("Valor recebido em dinheiro");
        EditText mistoDinheiro = inputNumero("Dinheiro");
        EditText mistoPix = inputNumero("PIX");
        EditText mistoCartao = inputNumero("Cartão");
        TextView troco = txt("", 15, true);
        troco.setTextColor(GREEN);

        box.addView(recebido);
        box.addView(mistoDinheiro);
        box.addView(mistoPix);
        box.addView(mistoCartao);
        box.addView(troco);

        Runnable ajustarVisibilidade = () -> {
            String f = forma.getSelectedItem().toString();
            recebido.setVisibility(f.equals("DINHEIRO") ? View.VISIBLE : View.GONE);
            boolean misto = f.equals("MISTO");
            mistoDinheiro.setVisibility(misto ? View.VISIBLE : View.GONE);
            mistoPix.setVisibility(misto ? View.VISIBLE : View.GONE);
            mistoCartao.setVisibility(misto ? View.VISIBLE : View.GONE);
            troco.setVisibility(f.equals("DINHEIRO") ? View.VISIBLE : View.GONE);
        };

        Runnable atualizarTroco = () -> {
            if (!forma.getSelectedItem().toString().equals("DINHEIRO")) return;
            double r = numero(recebido.getText().toString());
            if (Double.isNaN(r)) r = 0;
            double t = r - total;
            troco.setText(t >= 0 ? "Troco: " + moeda.format(t) : "Falta: " + moeda.format(-t));
            troco.setTextColor(t >= 0 ? GREEN : RED);
        };

        forma.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ajustarVisibilidade.run();
                atualizarTroco.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        recebido.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { atualizarTroco.run(); }
            public void afterTextChanged(Editable e) {}
        });

        ajustarVisibilidade.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Finalizar venda")
                .setView(box)
                .setPositiveButton("CONFIRMAR VENDA", null)
                .setNegativeButton("Voltar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String f = forma.getSelectedItem().toString();
            GestaoDbHelper.Pagamento pg = new GestaoDbHelper.Pagamento();
            pg.forma = f;

            if (f.equals("DINHEIRO")) {
                double r = numero(recebido.getText().toString());
                if (Double.isNaN(r) || r + 0.001 < total) {
                    recebido.setError("Valor recebido deve ser igual ou maior que o total");
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
                if (Double.isNaN(d) || Double.isNaN(p) || Double.isNaN(c)) {
                    Toast.makeText(this, "Confira os valores do pagamento misto.", Toast.LENGTH_SHORT).show();
                    return;
                }
                double soma = d + p + c;
                if (Math.abs(soma - total) > 0.011) {
                    Toast.makeText(this,
                            "Pagamento misto soma " + moeda.format(soma) +
                                    " e precisa fechar " + moeda.format(total),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                pg.dinheiro = d;
                pg.pix = p;
                pg.cartao = c;
                pg.recebido = total;
            }

            try {
                String tipoDesc = desconto > 0 ? (descontoPercentual ? "PERCENTUAL" : "VALOR") : "";
                long idVenda = db.finalizarVenda(carrinho, pg, desconto, tipoDesc, descontoEntrada);
                double trocoFinal = pg.troco;
                double totalFinal = total;

                carrinho.clear();
                descontoEntrada = 0;
                descontoPercentual = false;
                dialog.dismiss();
                busca.setText("");
                atualizarResultados();
                atualizarCarrinho();

                String msg = "Venda #" + idVenda +
                        "\nTotal: " + moeda.format(totalFinal);
                if (trocoFinal > 0.001) msg += "\nTroco: " + moeda.format(trocoFinal);

                new AlertDialog.Builder(this)
                        .setTitle("Venda concluída ✓")
                        .setMessage(msg + "\n\nEstoque atualizado automaticamente.")
                        .setPositiveButton("Nova venda", null)
                        .show();
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                atualizarResultados();
            }
        }));

        dialog.show();
    }

    private EditText inputNumero(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private double zeroSeVazio(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        return numero(s);
    }

    private double subtotal() {
        double total = 0;
        for (GestaoDbHelper.VendaItem i : carrinho) total += i.total();
        return total;
    }

    private double custoTotal() {
        double total = 0;
        for (GestaoDbHelper.VendaItem i : carrinho) total += i.custoTotal();
        return total;
    }

    private double valorDesconto() {
        if (descontoEntrada <= 0) return 0;
        double sub = subtotal();
        if (descontoPercentual) return Math.min(sub, sub * (descontoEntrada / 100.0));
        return Math.min(sub, descontoEntrada);
    }

    private String unidade(GestaoDbHelper.Produto p) {
        return p.unidade == null || p.unidade.trim().isEmpty() ? "UN" : p.unidade.trim();
    }

    private String fmtQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }

    private String fmtPct(double v) {
        return String.format(new Locale("pt","BR"), "%.2f%%", v);
    }

    private double numero(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        if (s.isEmpty()) return Double.NaN;
        int c = s.lastIndexOf(',');
        int d = s.lastIndexOf('.');
        try {
            if (c >= 0 && d >= 0) {
                if (c > d) s = s.replace(".", "").replace(",", ".");
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
