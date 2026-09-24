package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;

public class PdvActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final List<GestaoDbHelper.VendaItem> carrinho = new ArrayList<>();
    private GestaoDbHelper db;
    private EditText busca;
    private LinearLayout resultados;
    private LinearLayout itensCarrinho;
    private TextView totais;
    private Button finalizar;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#101828"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
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
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(30));
        scroll.addView(root);

        TextView title = txt("PDV / Frente de Caixa", 26, true);
        root.addView(title);

        TextView aviso = txt("O PDV encontrou um erro ao iniciar, mas o aplicativo permaneceu aberto.", 16, true);
        aviso.setTextColor(Color.parseColor("#B42318"));
        aviso.setPadding(0, dp(16), 0, dp(10));
        root.addView(aviso);

        String detalhe = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            detalhe += "\n" + e.getMessage();
        }

        TextView erro = txt(detalhe, 13, false);
        erro.setTextColor(Color.parseColor("#475467"));
        erro.setBackgroundColor(Color.WHITE);
        erro.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(erro);

        Button voltar = new Button(this);
        voltar.setText("Voltar");
        voltar.setAllCaps(false);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        setContentView(scroll);
    }

    private void montarTela() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(28));
        scroll.addView(root);

        Button voltar = new Button(this);
        voltar.setText("← Voltar");
        voltar.setAllCaps(false);
        voltar.setOnClickListener(v -> {
            if (carrinho.isEmpty()) finish();
            else new AlertDialog.Builder(this)
                    .setTitle("Cancelar venda?")
                    .setMessage("Os itens do carrinho serão descartados.")
                    .setPositiveButton("Cancelar venda", (d,w) -> finish())
                    .setNegativeButton("Continuar", null)
                    .show();
        });
        root.addView(voltar);

        TextView title = txt("PDV / Frente de Caixa", 27, true);
        title.setPadding(0, dp(14), 0, dp(2));
        root.addView(title);

        TextView sub = txt("Busque pelo nome, código interno ou código de barras.", 13, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        busca = new EditText(this);
        busca.setHint("Buscar produto...");
        busca.setTextSize(18);
        busca.setSingleLine(true);
        busca.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(12), 0, dp(4));
        busca.setLayoutParams(bp);
        root.addView(busca);

        resultados = new LinearLayout(this);
        resultados.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultados);

        TextView carrinhoTitle = txt("Carrinho", 20, true);
        carrinhoTitle.setPadding(0, dp(18), 0, dp(5));
        root.addView(carrinhoTitle);

        itensCarrinho = new LinearLayout(this);
        itensCarrinho.setOrientation(LinearLayout.VERTICAL);
        root.addView(itensCarrinho);

        totais = txt("", 17, true);
        totais.setBackgroundColor(Color.WHITE);
        totais.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, dp(10), 0, dp(8));
        totais.setLayoutParams(tp);
        root.addView(totais);

        finalizar = new Button(this);
        finalizar.setText("FINALIZAR VENDA");
        finalizar.setTextSize(18);
        finalizar.setOnClickListener(v -> abrirPagamento());
        root.addView(finalizar);

        busca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { atualizarResultados(); }
            public void afterTextChanged(Editable e) {}
        });

        setContentView(scroll);
        atualizarResultados();
        atualizarCarrinho();
    }

    private void atualizarResultados() {
        resultados.removeAllViews();
        String q = busca.getText().toString().trim();

        if (q.isEmpty()) {
            TextView dica = txt("Digite para localizar um produto.", 13, false);
            dica.setTextColor(Color.parseColor("#667085"));
            dica.setPadding(0, dp(5), 0, dp(8));
            resultados.addView(dica);
            return;
        }

        List<GestaoDbHelper.Produto> ps = db.list(q);
        if (ps.isEmpty()) {
            TextView vazio = txt("Nenhum produto encontrado.", 14, false);
            vazio.setTextColor(Color.parseColor("#B42318"));
            resultados.addView(vazio);
            return;
        }

        int limite = Math.min(ps.size(), 8);
        for (int i=0; i<limite; i++) {
            GestaoDbHelper.Produto p = ps.get(i);

            Button b = new Button(this);
            String un = unidade(p);
            String estoque = p.ehServico() ? "Serviço" : "Estoque: " + fmtQtd(p.estoque) + " " + un;
            b.setText(p.nome + "\n" + moeda.format(p.precoVenda) + "  •  " + estoque);
            b.setAllCaps(false);
            b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> adicionarProduto(p));

            boolean disponivel = p.ehServico() || p.estoque > 0.000001;
            b.setEnabled(disponivel);
            resultados.addView(b);
        }
    }

    private void adicionarProduto(GestaoDbHelper.Produto p) {
        GestaoDbHelper.VendaItem existente = localizarNoCarrinho(p.id);
        double passo = 1.0;

        if (existente != null) {
            double nova = existente.quantidade + passo;
            if (!p.ehServico() && nova > p.estoque + 0.000001) {
                Toast.makeText(this, "Quantidade maior que o estoque disponível.", Toast.LENGTH_SHORT).show();
                return;
            }
            existente.quantidade = nova;
        } else {
            if (!p.ehServico() && p.estoque < passo - 0.000001) {
                Toast.makeText(this, "Produto sem estoque suficiente.", Toast.LENGTH_SHORT).show();
                return;
            }
            GestaoDbHelper.VendaItem item = new GestaoDbHelper.VendaItem();
            item.produto = p;
            item.quantidade = passo;
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

        if (carrinho.isEmpty()) {
            TextView vazio = txt("Carrinho vazio.", 15, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(18), 0, dp(18));
            itensCarrinho.addView(vazio);
        } else {
            for (GestaoDbHelper.VendaItem item : new ArrayList<>(carrinho)) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Color.WHITE);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.setMargins(0, dp(4), 0, dp(4));
                card.setLayoutParams(cp);

                card.addView(txt(item.produto.nome, 16, true));

                TextView info = txt(
                        fmtQtd(item.quantidade) + " " + unidade(item.produto) +
                        " × " + moeda.format(item.precoUnitario) +
                        " = " + moeda.format(item.total()),
                        14, false);
                info.setTextColor(Color.parseColor("#344054"));
                card.addView(info);

                LinearLayout botoes = new LinearLayout(this);
                botoes.setOrientation(LinearLayout.HORIZONTAL);

                Button menos = new Button(this);
                menos.setText("−");
                menos.setOnClickListener(v -> diminuir(item));
                botoes.addView(menos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button qtd = new Button(this);
                qtd.setText("Qtd.");
                qtd.setAllCaps(false);
                qtd.setOnClickListener(v -> editarQuantidade(item));
                botoes.addView(qtd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button mais = new Button(this);
                mais.setText("+");
                mais.setOnClickListener(v -> aumentar(item));
                botoes.addView(mais, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button remover = new Button(this);
                remover.setText("Remover");
                remover.setAllCaps(false);
                remover.setOnClickListener(v -> {
                    carrinho.remove(item);
                    atualizarCarrinho();
                });
                botoes.addView(remover, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));

                card.addView(botoes);
                itensCarrinho.addView(card);
            }
        }

        double total=0, custo=0;
        for (GestaoDbHelper.VendaItem i : carrinho) {
            total += i.total();
            custo += i.custoTotal();
        }
        double lucro = total - custo;

        totais.setText(
                "TOTAL: " + moeda.format(total) +
                "\nCusto das mercadorias: " + moeda.format(custo) +
                "\nLucro bruto desta venda: " + moeda.format(lucro));

        finalizar.setEnabled(!carrinho.isEmpty());
    }

    private void diminuir(GestaoDbHelper.VendaItem item) {
        double passo = 1.0;
        if (item.quantidade - passo <= 0.000001) carrinho.remove(item);
        else item.quantidade -= passo;
        atualizarCarrinho();
    }

    private void aumentar(GestaoDbHelper.VendaItem item) {
        double nova = item.quantidade + 1.0;
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
        EditText campo = new EditText(this);
        campo.setText(fmtQtd(item.quantidade).replace(".", ","));
        campo.setSelectAllOnFocus(true);
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_NUMBER |
                (fracionada(item.produto) ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Quantidade — " + item.produto.nome)
                .setMessage(item.produto.ehServico() ? "Informe a quantidade." :
                        "Disponível: " + fmtQtd(item.produto.estoque) + " " + unidade(item.produto))
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

    private void abrirPagamento() {
        if (carrinho.isEmpty()) return;

        double total = totalVenda();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(6));

        TextView totalTxt = txt("Total a receber: " + moeda.format(total), 20, true);
        totalTxt.setPadding(0, 0, 0, dp(10));
        box.addView(totalTxt);

        Spinner forma = new Spinner(this);
        String[] formas = {"DINHEIRO", "PIX", "CARTÃO DÉBITO", "CARTÃO CRÉDITO", "MISTO"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formas);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        forma.setAdapter(ad);
        box.addView(forma);

        EditText recebido = dinheiroCampo("Valor recebido em dinheiro");
        EditText mistoDinheiro = dinheiroCampo("Dinheiro");
        EditText mistoPix = dinheiroCampo("PIX");
        EditText mistoCartao = dinheiroCampo("Cartão");
        TextView troco = txt("", 15, true);
        troco.setTextColor(Color.parseColor("#176240"));

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
            troco.setTextColor(Color.parseColor(t >= 0 ? "#176240" : "#B42318"));
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
                .setTitle("Pagamento")
                .setView(box)
                .setPositiveButton("Confirmar venda", null)
                .setNegativeButton("Cancelar", null)
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
                long idVenda = db.finalizarVenda(carrinho, pg);
                double trocoFinal = pg.troco;
                carrinho.clear();
                dialog.dismiss();
                busca.setText("");
                atualizarResultados();
                atualizarCarrinho();

                String msg = "Venda #" + idVenda + " finalizada.";
                if (trocoFinal > 0.001) msg += "\nTroco: " + moeda.format(trocoFinal);

                new AlertDialog.Builder(this)
                        .setTitle("Venda concluída")
                        .setMessage(msg + "\nEstoque atualizado automaticamente.")
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                atualizarResultados();
            }
        }));

        dialog.show();
    }

    private EditText dinheiroCampo(String hint) {
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

    private double totalVenda() {
        double total = 0;
        for (GestaoDbHelper.VendaItem i : carrinho) total += i.total();
        return total;
    }

    private String unidade(GestaoDbHelper.Produto p) {
        return p.unidade == null || p.unidade.trim().isEmpty() ? "UN" : p.unidade.trim();
    }

    private String fmtQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
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
