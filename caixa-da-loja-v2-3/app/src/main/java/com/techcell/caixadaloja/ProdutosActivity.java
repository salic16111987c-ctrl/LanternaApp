package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProdutosActivity extends Activity {
    private GestaoDbHelper db;
    private LinearLayout lista;
    private TextView contador;
    private EditText busca;
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.parseColor("#101828"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
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

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F3F5F9"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(30));
        scroll.addView(root);

        Button voltar = new Button(this);
        voltar.setText("← Voltar"); voltar.setAllCaps(false);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView title = txt("Produtos", 28, true);
        title.setPadding(0, dp(16), 0, dp(4)); root.addView(title);
        TextView info = txt("Cadastro separado da Gestão Tech Cell. Ainda não importa o SMB.", 13, false);
        info.setTextColor(Color.parseColor("#667085")); root.addView(info);

        Button novo = new Button(this);
        novo.setText("+ Novo produto"); novo.setAllCaps(false); novo.setTextSize(17);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, dp(16), 0, dp(10)); novo.setLayoutParams(np);
        novo.setOnClickListener(v -> abrirFormulario(null));
        root.addView(novo);

        busca = field("Buscar por nome, código ou código de barras", InputType.TYPE_CLASS_TEXT);
        root.addView(busca);

        contador = txt("", 13, true);
        contador.setTextColor(Color.parseColor("#475467"));
        contador.setPadding(0, dp(8), 0, dp(8));
        root.addView(contador);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        busca.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { carregar(); }
            public void afterTextChanged(Editable e) {}
        });

        setContentView(scroll);
        carregar();
    }

    private void carregar() {
        List<GestaoDbHelper.Produto> produtos = db.list(busca == null ? "" : busca.getText().toString());
        lista.removeAllViews();
        contador.setText(produtos.size() + (produtos.size()==1 ? " produto" : " produtos"));

        if (produtos.isEmpty()) {
            TextView vazio = txt("Nenhum produto cadastrado.", 15, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(8), dp(30), dp(8), dp(30));
            lista.addView(vazio);
            return;
        }

        for (GestaoDbHelper.Produto p : produtos) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(5), 0, dp(5)); card.setLayoutParams(cp);

            String cab = (p.codigo == null || p.codigo.isEmpty() ? "" : p.codigo + " • ") + p.nome;
            card.addView(txt(cab, 17, true));
            TextView linha = txt("Custo: " + moeda.format(p.custo) +
                    "   Venda: " + moeda.format(p.precoVenda) +
                    "\nLucro/un.: " + moeda.format(p.lucroUnitario()) +
                    "   Margem s/venda: " + String.format(Locale.US,"%.1f%%", p.margemSobreVenda()) +
                    "\nEstoque: " + fmtQtd(p.estoque), 14, false);
            linha.setTextColor(Color.parseColor("#344054"));
            linha.setPadding(0, dp(5), 0, 0);
            card.addView(linha);

            if (p.estoque <= p.estoqueMinimo && p.estoqueMinimo > 0) {
                TextView baixo = txt("⚠ Estoque baixo — mínimo: " + fmtQtd(p.estoqueMinimo), 13, true);
                baixo.setTextColor(Color.parseColor("#B42318"));
                baixo.setPadding(0, dp(5), 0, 0);
                card.addView(baixo);
            }

            card.setOnClickListener(v -> abrirFormulario(p));
            lista.addView(card);
        }
    }

    private String fmtQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }

    private double num(String s) {
        if (s == null) return 0;
        s = s.trim().replace(".", "").replace(",", ".");
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch(Exception e) { return 0; }
    }

    private void abrirFormulario(GestaoDbHelper.Produto original) {
        GestaoDbHelper.Produto p = original == null ? new GestaoDbHelper.Produto() : original;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        sv.addView(box);

        EditText codigo = field("Código", InputType.TYPE_CLASS_TEXT);
        EditText nome = field("Nome do produto *", InputType.TYPE_CLASS_TEXT);
        EditText barras = field("Código de barras", InputType.TYPE_CLASS_TEXT);
        EditText grupo = field("Grupo", InputType.TYPE_CLASS_TEXT);
        EditText fornecedor = field("Fornecedor", InputType.TYPE_CLASS_TEXT);
        EditText unidade = field("Unidade (UN, PC, KG...)", InputType.TYPE_CLASS_TEXT);
        EditText fabricante = field("Fabricante", InputType.TYPE_CLASS_TEXT);
        int dec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText custo = field("Valor de compra / custo", dec);
        EditText venda = field("Valor de venda", dec);
        EditText prazo = field("Valor a prazo", dec);
        EditText estoque = field("Estoque atual", dec);
        EditText minimo = field("Estoque mínimo", dec);

        codigo.setText(p.codigo); nome.setText(p.nome); barras.setText(p.codigoBarras);
        grupo.setText(p.grupo); fornecedor.setText(p.fornecedor); unidade.setText(p.unidade);
        fabricante.setText(p.fabricante);
        if (p.custo != 0) custo.setText(String.valueOf(p.custo).replace(".", ","));
        if (p.precoVenda != 0) venda.setText(String.valueOf(p.precoVenda).replace(".", ","));
        if (p.precoPrazo != 0) prazo.setText(String.valueOf(p.precoPrazo).replace(".", ","));
        if (p.estoque != 0) estoque.setText(String.valueOf(p.estoque).replace(".", ","));
        if (p.estoqueMinimo != 0) minimo.setText(String.valueOf(p.estoqueMinimo).replace(".", ","));

        box.addView(codigo); box.addView(nome); box.addView(barras); box.addView(grupo);
        box.addView(fornecedor); box.addView(unidade); box.addView(fabricante);
        box.addView(custo); box.addView(venda); box.addView(prazo); box.addView(estoque); box.addView(minimo);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(original == null ? "Novo produto" : "Editar produto")
                .setView(sv)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null);

        if (original != null) {
            b.setNeutralButton("Excluir", (d,w) -> new AlertDialog.Builder(this)
                    .setTitle("Excluir produto?")
                    .setMessage(original.nome)
                    .setPositiveButton("Excluir", (dd,ww) -> { db.delete(original.id); carregar(); })
                    .setNegativeButton("Cancelar", null)
                    .show());
        }

        AlertDialog dialog = b.create();
        dialog.setOnShowListener(x -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = nome.getText().toString().trim();
            if (n.isEmpty()) {
                nome.setError("Informe o nome do produto");
                return;
            }
            p.codigo = codigo.getText().toString().trim();
            p.nome = n;
            p.codigoBarras = barras.getText().toString().trim();
            p.grupo = grupo.getText().toString().trim();
            p.fornecedor = fornecedor.getText().toString().trim();
            p.unidade = unidade.getText().toString().trim();
            p.fabricante = fabricante.getText().toString().trim();
            p.custo = num(custo.getText().toString());
            p.precoVenda = num(venda.getText().toString());
            p.precoPrazo = num(prazo.getText().toString());
            p.estoque = num(estoque.getText().toString());
            p.estoqueMinimo = num(minimo.getText().toString());
            db.save(p);
            dialog.dismiss();
            carregar();
            Toast.makeText(this, "Produto salvo.", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }
}
