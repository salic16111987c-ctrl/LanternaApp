package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FinanceiroActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));
    private GestaoDbHelper db;
    private LinearLayout resumoBox;
    private LinearLayout lista;
    private Button hojeBtn, mesBtn;
    private boolean periodoMes = false;
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
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        Button voltar = action("← Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView titulo = txt("Financeiro", 28, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Vendas, custos, despesas e resultado • Alpha 20", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        LinearLayout filtros = new LinearLayout(this);
        filtros.setOrientation(LinearLayout.HORIZONTAL);
        filtros.setPadding(0, dp(14), 0, dp(6));

        hojeBtn = action("Hoje");
        hojeBtn.setOnClickListener(v -> { periodoMes = false; carregar(); });
        filtros.addView(hojeBtn, new LinearLayout.LayoutParams(0, dp(50), 1));

        mesBtn = action("Este mês");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(50), 1);
        mp.setMargins(dp(8),0,0,0);
        mesBtn.setLayoutParams(mp);
        mesBtn.setOnClickListener(v -> { periodoMes = true; carregar(); });
        filtros.addView(mesBtn);
        root.addView(filtros);

        resumoBox = new LinearLayout(this);
        resumoBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resumoBox);

        Button nova = action("+ Registrar saída / despesa");
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        np.setMargins(0, dp(14), 0, dp(8));
        nova.setLayoutParams(np);
        nova.setOnClickListener(v -> novaDespesa());
        root.addView(nova);

        TextView mov = txt("Saídas registradas", 19, true);
        mov.setPadding(0, dp(12), 0, dp(4));
        root.addView(mov);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        setContentView(scroll);
        carregar();
    }

    private void definirPeriodo() {
        Calendar c = Calendar.getInstance();
        if (periodoMes) {
            c.set(Calendar.DAY_OF_MONTH, 1);
        }
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
        inicioAtual = c.getTimeInMillis();

        if (periodoMes) c.add(Calendar.MONTH,1);
        else c.add(Calendar.DAY_OF_MONTH,1);
        fimAtual = c.getTimeInMillis();
    }

    private void carregar() {
        definirPeriodo();
        hojeBtn.setEnabled(periodoMes);
        mesBtn.setEnabled(!periodoMes);

        GestaoDbHelper.ResumoFinanceiro r = db.resumoFinanceiro(inicioAtual, fimAtual);

        resumoBox.removeAllViews();
        resumoBox.addView(cardResumo("Vendas realizadas",
                r.vendas.quantidadeVendas + " venda(s)", "#175CD3"));
        resumoBox.addView(cardResumo("Total vendido",
                moeda.format(r.vendas.total), "#07884B"));
        resumoBox.addView(cardResumo("Custo das mercadorias vendidas",
                moeda.format(r.vendas.custo), "#475467"));
        resumoBox.addView(cardResumo("Lucro bruto",
                moeda.format(r.vendas.lucro), "#176240"));
        resumoBox.addView(cardResumo("Despesas operacionais",
                moeda.format(r.despesasOperacionais), "#B42318"));
        if (r.outrasSaidas > 0.001) {
            resumoBox.addView(cardResumo("Outras saídas que afetam resultado",
                    moeda.format(r.outrasSaidas), "#B42318"));
        }
        resumoBox.addView(cardResumo("Lucro líquido",
                moeda.format(r.lucroLiquido),
                r.lucroLiquido >= 0 ? "#176240" : "#B42318"));

        if (r.comprasEstoque > 0.001) {
            resumoBox.addView(cardResumo("Compras para estoque",
                    moeda.format(r.comprasEstoque), "#B54708"));
            TextView regra = txt(
                    "Compras de estoque não são descontadas novamente do lucro líquido. " +
                    "O custo entra no resultado quando a mercadoria é vendida.",
                    12, false);
            regra.setTextColor(Color.parseColor("#667085"));
            regra.setPadding(dp(10), dp(4), dp(10), dp(8));
            resumoBox.addView(regra);
        }

        TextView formas = txt(
                "Recebimentos — Dinheiro: " + moeda.format(r.vendas.dinheiro) +
                "   PIX: " + moeda.format(r.vendas.pix) +
                "   Cartão: " + moeda.format(r.vendas.cartao),
                13, true);
        formas.setTextColor(Color.parseColor("#344054"));
        formas.setPadding(dp(12), dp(10), dp(12), dp(10));
        formas.setBackgroundColor(Color.WHITE);
        resumoBox.addView(formas);

        lista.removeAllViews();
        List<GestaoDbHelper.Despesa> despesas = db.listDespesas(inicioAtual, fimAtual, 200);
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
            card.setBackgroundColor(Color.WHITE);
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
            TextView meta = txt(data.format(new Date(d.dataMillis)) + " • " + tipo +
                    (d.categoria == null || d.categoria.isEmpty() ? "" : " • " + d.categoria),
                    12, false);
            meta.setTextColor(Color.parseColor("#667085"));
            card.addView(meta);

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

    private TextView cardResumo(String titulo, String valor, String cor) {
        TextView t = txt(titulo + "\n" + valor, 15, true);
        t.setTextColor(Color.parseColor(cor));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        t.setLayoutParams(p);
        return t;
    }

    private void novaDespesa() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(12));
        sv.addView(box);

        TextView aviso = txt(
                "Escolha corretamente o tipo. Compra para estoque não é descontada novamente " +
                "do lucro líquido, evitando contar o custo da mercadoria duas vezes.",
                12, true);
        aviso.setTextColor(Color.parseColor("#B54708"));
        aviso.setPadding(0,0,0,dp(8));
        box.addView(aviso);

        EditText descricao = campo("Descrição *", InputType.TYPE_CLASS_TEXT);
        box.addView(descricao);

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

        EditText valor = campo("Valor R$ *",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(valor);

        EditText obs = campo("Observação", InputType.TYPE_CLASS_TEXT);
        box.addView(obs);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Registrar saída")
                .setView(sv)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String desc = descricao.getText().toString().trim();
                    if (desc.isEmpty()) { descricao.setError("Informe a descrição"); return; }

                    double vl = numero(valor.getText().toString());
                    if (Double.isNaN(vl) || vl <= 0) {
                        valor.setError("Informe um valor válido");
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
                    db.saveDespesa(d);
                    dialog.dismiss();
                    carregar();
                    Toast.makeText(this, "Saída registrada.", Toast.LENGTH_SHORT).show();
                }));
        dialog.show();
    }

    private void abrirDespesa(GestaoDbHelper.Despesa d) {
        String msg = data.format(new Date(d.dataMillis)) +
                "\nCategoria: " + (d.categoria == null || d.categoria.isEmpty() ? "—" : d.categoria) +
                "\nPagamento: " + d.formaPagamento +
                "\nValor: " + moeda.format(d.valor) +
                (d.observacao == null || d.observacao.isEmpty() ? "" : "\nObservação: " + d.observacao);

        new AlertDialog.Builder(this)
                .setTitle(d.descricao)
                .setMessage(msg)
                .setPositiveButton("Fechar", null)
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
