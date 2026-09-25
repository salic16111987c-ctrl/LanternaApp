package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoricoVendasActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));
    private GestaoDbHelper db;
    private LinearLayout lista;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
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

        Button voltar = new Button(this);
        voltar.setText("←  Voltar");
        voltar.setAllCaps(false);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView titulo = txt("Histórico de vendas", 27, true);
        titulo.setPadding(0, dp(18), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Últimas vendas realizadas • Alpha 15", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        root.addView(lista);

        setContentView(scroll);
        carregar();
    }

    private void carregar() {
        lista.removeAllViews();
        List<GestaoDbHelper.VendaResumo> vendas = db.listVendas(200);

        if (vendas.isEmpty()) {
            TextView vazio = txt("Nenhuma venda registrada.", 15, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(30), 0, dp(30));
            lista.addView(vazio);
            return;
        }

        for (GestaoDbHelper.VendaResumo v : vendas) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(8), 0, 0);
            card.setLayoutParams(cp);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView id = txt("Venda #" + v.id, 16, true);
            top.addView(id, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView total = txt(moeda.format(v.total), 18, true);
            total.setTextColor(Color.parseColor("#07884B"));
            top.addView(total);
            card.addView(top);

            TextView dt = txt(data.format(new Date(v.dataMillis)) + " • " + v.formaPagamento, 12, false);
            dt.setTextColor(Color.parseColor("#667085"));
            card.addView(dt);

            String fiscal = formatarSituacaoFiscal(v);
            TextView nf = txt(fiscal, 12, true);
            nf.setTextColor(corFiscal(v.notaStatus));
            nf.setPadding(0, dp(5), 0, 0);
            card.addView(nf);

            String cliente = v.destNome == null ? "" : v.destNome.trim();
            if (!cliente.isEmpty()) {
                TextView cli = txt("Cliente: " + cliente, 12, false);
                cli.setTextColor(Color.parseColor("#475467"));
                cli.setPadding(0, dp(3), 0, 0);
                card.addView(cli);
            }

            card.setOnClickListener(x -> abrirVenda(v.id));
            lista.addView(card);
        }
    }

    private int corFiscal(String status) {
        if ("AUTORIZADA".equalsIgnoreCase(status)) return Color.parseColor("#176240");
        if ("PENDENTE_CONFIGURACAO".equalsIgnoreCase(status)) return Color.parseColor("#B54708");
        if ("CANCELADA".equalsIgnoreCase(status)) return Color.parseColor("#B42318");
        return Color.parseColor("#667085");
    }

    private String formatarSituacaoFiscal(GestaoDbHelper.VendaResumo v) {
        String tipo = v.notaTipo == null || v.notaTipo.trim().isEmpty() ? "Sem nota" : v.notaTipo;
        String status = v.notaStatus == null ? "NAO_EMITIDA" : v.notaStatus;
        if ("NAO_EMITIDA".equals(status)) return "Fiscal: sem documento emitido";
        if ("PENDENTE_CONFIGURACAO".equals(status)) return "Fiscal: " + tipo + " preparada / pendente";
        return "Fiscal: " + tipo + " • " + status.replace('_',' ');
    }

    private void abrirVenda(long vendaId) {
        GestaoDbHelper.VendaDetalhe v = db.getVendaDetalhe(vendaId);
        if (v == null) {
            Toast.makeText(this, "Venda não encontrada.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder detalhes = new StringBuilder();
        detalhes.append("Data: ").append(data.format(new Date(v.dataMillis))).append("\n");
        detalhes.append("Pagamento: ").append(v.formaPagamento).append("\n\n");

        for (GestaoDbHelper.VendaItemRegistro item : v.itens) {
            detalhes.append(item.nome).append("\n")
                    .append(formatarQtd(item.quantidade)).append(" ")
                    .append(item.unidade == null || item.unidade.isEmpty() ? "UN" : item.unidade)
                    .append(" × ").append(moeda.format(item.precoUnitario))
                    .append(" = ").append(moeda.format(item.total)).append("\n\n");
        }

        detalhes.append("Subtotal: ").append(moeda.format(v.subtotal)).append("\n");
        detalhes.append("Desconto: - ").append(moeda.format(v.desconto)).append("\n");
        detalhes.append("TOTAL: ").append(moeda.format(v.total)).append("\n");

        if (v.troco > 0.001) detalhes.append("Troco: ").append(moeda.format(v.troco)).append("\n");

        if (v.destNome != null && !v.destNome.trim().isEmpty()) {
            detalhes.append("\nCliente: ").append(v.destNome);
            if (v.destDocumento != null && !v.destDocumento.isEmpty()) {
                boolean cnpj = v.destDocumento.length() > 11;
                detalhes.append("\nDocumento: ")
                        .append(CadastroBrasilUtils.formatarDocumento(v.destDocumento, cnpj));
            }
        }

        detalhes.append("\n\n").append("Situação fiscal: ")
                .append(v.notaStatus == null ? "NAO_EMITIDA" : v.notaStatus.replace('_',' '));
        if (v.notaTipo != null && !v.notaTipo.isEmpty())
            detalhes.append("\nDocumento: ").append(v.notaTipo);
        if (v.notaNumero != null && !v.notaNumero.isEmpty())
            detalhes.append("\nNúmero: ").append(v.notaNumero);
        if (v.notaChave != null && !v.notaChave.isEmpty())
            detalhes.append("\nChave: ").append(v.notaChave);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Venda #" + vendaId)
                .setMessage(detalhes.toString())
                .setPositiveButton("Comprovante", null)
                .setNeutralButton("Nota fiscal", null)
                .setNegativeButton("Fechar", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(y -> compartilharComprovante(vendaId));

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(y -> {
                        Intent i = new Intent(this, PdvActivity.class);
                        i.putExtra("emitir_nota_venda_id", vendaId);
                        startActivity(i);
                    });
        });
        dialog.show();
    }

    private void compartilharComprovante(long vendaId) {
        GestaoDbHelper.VendaDetalhe v = db.getVendaDetalhe(vendaId);
        if (v == null) return;

        String comprovante = montarComprovante(v);

        AlertDialog preview = new AlertDialog.Builder(this)
                .setTitle("Comprovante da venda")
                .setMessage(comprovante)
                .setPositiveButton("Compartilhar", null)
                .setNegativeButton("Fechar", null)
                .create();

        preview.setOnShowListener(x -> preview.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(y -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_SUBJECT, "Comprovante Tech Cell - Venda #" + v.id);
                    share.putExtra(Intent.EXTRA_TEXT, comprovante);
                    startActivity(Intent.createChooser(share, "Compartilhar comprovante"));
                }));
        preview.show();
    }

    private String montarComprovante(GestaoDbHelper.VendaDetalhe v) {
        StringBuilder s = new StringBuilder();
        s.append(ConfiguracoesFiscaisActivity.nomeEmpresa(this)).append("\n");
        s.append("COMPROVANTE DE VENDA - NÃO FISCAL\n");
        s.append("--------------------------------\n");
        s.append("Venda #").append(v.id).append("\n");
        s.append("Data: ").append(data.format(new Date(v.dataMillis))).append("\n");
        s.append("--------------------------------\n");

        for (GestaoDbHelper.VendaItemRegistro item : v.itens) {
            s.append(item.nome).append("\n");
            s.append(formatarQtd(item.quantidade)).append(" ")
                    .append(item.unidade == null || item.unidade.isEmpty() ? "UN" : item.unidade)
                    .append(" x ").append(moeda.format(item.precoUnitario))
                    .append(" = ").append(moeda.format(item.total)).append("\n");
        }

        s.append("--------------------------------\n");
        s.append("Subtotal: ").append(moeda.format(v.subtotal)).append("\n");
        s.append("Desconto: - ").append(moeda.format(v.desconto)).append("\n");
        s.append("TOTAL: ").append(moeda.format(v.total)).append("\n");
        s.append("Pagamento: ").append(v.formaPagamento).append("\n");
        if (v.troco > 0.001) s.append("Troco: ").append(moeda.format(v.troco)).append("\n");
        s.append("--------------------------------\n");
        s.append("Obrigado pela preferência!\n");
        return s.toString();
    }

    private String formatarQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.US, "%.3f", v).replaceAll("0+$","").replaceAll("\\.$","");
    }
}
