package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import java.util.Locale;

/** Relatório operacional do dia liberado pela permissão RESUMO_DIA. */
public class RelatorioDiaActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));
    private final SimpleDateFormat hora = new SimpleDateFormat("HH:mm", new Locale("pt", "BR"));
    private GestaoDbHelper db;

    private int dp(int v) { return TechCellUi.dp(this, v); }
    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TechCellUi.TEXT);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!TechCellAccess.podeResumoDia(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Acesso restrito")
                    .setMessage("O Master não liberou o Resumo do dia para esta conta.")
                    .setPositiveButton("Voltar", (d, w) -> finish())
                    .setCancelable(false).show();
            return;
        }
        db = new GestaoDbHelper(this);
        montar();
    }

    @Override protected void onResume() {
        super.onResume();
        TechCellBackgroundSync.garantir(this);
        if (db != null && TechCellAccess.podeResumoDia(this)) montar();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private long[] periodoHoje() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long inicio = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{inicio, c.getTimeInMillis()};
    }

    private void montar() {
        TechCellUi.applyWindowChrome(this);
        long[] p = periodoHoje();
        long inicio = p[0], fim = p[1];
        GestaoDbHelper.ResumoVendas r = db.resumoHoje();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root);

        Button voltar = botao("←  Voltar");
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView titulo = txt("Relatório do dia", 27, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);
        TextView sub = txt(data.format(Calendar.getInstance().getTime()) + " • atualizado com as vendas disponíveis neste aparelho", 12, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout resumo = TechCellUi.card(this);
        resumo.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        resumo.addView(txt("RESUMO", 11, true));
        TextView total = txt(moeda.format(r.total), 27, true);
        total.setTextColor(TechCellUi.GREEN);
        total.setPadding(0, dp(5), 0, dp(3));
        resumo.addView(total);
        TextView vendas = txt(r.quantidadeVendas + (r.quantidadeVendas == 1 ? " venda concluída" : " vendas concluídas"), 13, true);
        vendas.setTextColor(TechCellUi.NAVY);
        resumo.addView(vendas);
        if (TechCellAccess.podeResumoFinanceiro(this)) {
            TextView fin = txt("Custo " + moeda.format(r.custo) + "   •   Lucro bruto " + moeda.format(r.lucro), 12, true);
            fin.setTextColor(TechCellUi.MUTED);
            fin.setPadding(0, dp(5), 0, 0);
            resumo.addView(fin);
        }
        root.addView(resumo);

        TextView sec = txt("O que foi vendido hoje", 18, true);
        sec.setPadding(0, dp(12), 0, dp(2));
        root.addView(sec);
        carregarProdutos(root, inicio, fim);

        if (TechCellAccess.podeResumoFinanceiro(this)) {
            LinearLayout rec = TechCellUi.card(this);
            rec.setLayoutParams(TechCellUi.fullCardParams(this, 10));
            TextView h = txt("RECEBIMENTOS", 11, true);
            h.setTextColor(TechCellUi.MUTED);
            rec.addView(h);
            TextView v = txt("Dinheiro " + moeda.format(r.dinheiro) + "   •   PIX " + moeda.format(r.pix) + "   •   Cartão " + moeda.format(r.cartao), 13, true);
            v.setPadding(0, dp(5), 0, 0);
            rec.addView(v);
            root.addView(rec);
        }

        if (TechCellAccess.podeHistorico(this)) {
            TextView sh = txt("Vendas de hoje", 18, true);
            sh.setPadding(0, dp(14), 0, dp(2));
            root.addView(sh);
            carregarVendas(root, inicio, fim);
            Button historico = botao("🧾  Abrir histórico completo");
            TechCellUi.styleSecondary(this, historico);
            historico.setOnClickListener(v -> startActivity(new Intent(this, HistoricoVendasActivity.class)));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            hp.setMargins(0, dp(8), 0, 0);
            root.addView(historico, hp);
        }

        setContentView(scroll);
    }

    private void carregarProdutos(LinearLayout root, long inicio, long fim) {
        SQLiteDatabase sql = db.getReadableDatabase();
        Cursor c = sql.rawQuery(
                "SELECT COALESCE(vi.codigo,''),vi.nome,COALESCE(vi.unidade,'')," +
                        "COALESCE(SUM(vi.quantidade),0)," +
                        "COALESCE(SUM(CASE WHEN vi.total_liquido<>0 THEN vi.total_liquido ELSE vi.total END),0) " +
                        "FROM venda_itens vi JOIN vendas v ON v.id=vi.venda_id " +
                        "WHERE v.data_millis>=? AND v.data_millis<? " +
                        "AND UPPER(COALESCE(v.status_venda,'CONCLUIDA'))<>'ESTORNADA' " +
                        "GROUP BY COALESCE(vi.codigo,''),vi.nome,COALESCE(vi.unidade,'') " +
                        "ORDER BY 5 DESC, vi.nome COLLATE NOCASE",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        boolean algum = false;
        try {
            while (c.moveToNext()) {
                algum = true;
                String codigo = c.getString(0) == null ? "" : c.getString(0).trim();
                String nome = c.getString(1) == null ? "Produto" : c.getString(1);
                String unidade = c.getString(2) == null ? "" : c.getString(2).trim();
                double qtd = c.getDouble(3);
                double valor = c.getDouble(4);

                LinearLayout card = TechCellUi.card(this);
                card.setLayoutParams(TechCellUi.fullCardParams(this, 7));
                card.addView(txt((codigo.isEmpty() ? "" : codigo + " • ") + nome, 15, true));
                String quantidade = formatarQuantidade(qtd) + (unidade.isEmpty() ? " un." : " " + unidade);
                TextView d = txt(quantidade + "   •   Vendido " + moeda.format(valor), 13, true);
                d.setTextColor(TechCellUi.NAVY);
                d.setPadding(0, dp(5), 0, 0);
                card.addView(d);
                root.addView(card);
            }
        } finally { c.close(); }

        if (!algum) {
            LinearLayout vazio = TechCellUi.card(this);
            vazio.setLayoutParams(TechCellUi.fullCardParams(this, 7));
            TextView t = txt("Nenhum produto vendido hoje.", 13, false);
            t.setTextColor(TechCellUi.MUTED);
            t.setGravity(Gravity.CENTER);
            vazio.addView(t);
            root.addView(vazio);
        }
    }

    private void carregarVendas(LinearLayout root, long inicio, long fim) {
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,master_sale_id,data_millis,total,forma_pagamento,status_venda " +
                        "FROM vendas WHERE data_millis>=? AND data_millis<? ORDER BY data_millis DESC",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        boolean algum = false;
        try {
            while (c.moveToNext()) {
                algum = true;
                long id = c.getLong(0);
                long masterId = c.getLong(1);
                long numero = masterId > 0 ? masterId : id;
                long dataMillis = c.getLong(2);
                double total = c.getDouble(3);
                String forma = c.getString(4) == null ? "" : c.getString(4);
                String status = c.getString(5) == null ? "CONCLUIDA" : c.getString(5);

                LinearLayout card = TechCellUi.card(this);
                card.setLayoutParams(TechCellUi.fullCardParams(this, 7));
                card.addView(txt("Venda #" + numero + " • " + hora.format(dataMillis), 14, true));
                TextView d = txt(moeda.format(total) + (forma.isEmpty() ? "" : " • " + forma) +
                        ("ESTORNADA".equalsIgnoreCase(status) ? " • ESTORNADA" : ""), 12, true);
                d.setTextColor("ESTORNADA".equalsIgnoreCase(status) ? TechCellUi.RED : TechCellUi.NAVY);
                d.setPadding(0, dp(4), 0, 0);
                card.addView(d);
                root.addView(card);
            }
        } finally { c.close(); }
        if (!algum) {
            TextView t = txt("Nenhuma venda registrada hoje.", 12, false);
            t.setTextColor(TechCellUi.MUTED);
            t.setPadding(dp(4), dp(6), dp(4), 0);
            root.addView(t);
        }
    }

    private String formatarQuantidade(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(new Locale("pt", "BR"), "%.3f", v).replaceAll("0+$", "").replaceAll(",+$", "");
    }
}
