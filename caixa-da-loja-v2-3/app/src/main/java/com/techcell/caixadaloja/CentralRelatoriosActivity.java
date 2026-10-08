package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/** Centraliza os relatórios respeitando as permissões do usuário. */
public class CentralRelatoriosActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

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
        b.setTextSize(14);
        b.setAllCaps(false);
        TechCellUi.styleSecondary(this, b);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        montar();
    }

    @Override protected void onResume() {
        super.onResume();
        TechCellBackgroundSync.garantir(this);
        if (!TechCellAccess.podeResumoDia(this) && !TechCellAccess.podeRelatorios(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Acesso restrito")
                    .setMessage("O Master não liberou acesso aos relatórios para esta conta.")
                    .setPositiveButton("Voltar", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }
        montar();
    }

    private void montar() {
        boolean resumoDia = TechCellAccess.podeResumoDia(this);
        boolean relatorios = TechCellAccess.podeRelatorios(this);
        boolean historico = TechCellAccess.podeHistorico(this);

        TechCellUi.applyWindowChrome(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root);

        Button voltar = botao("←  Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView titulo = txt("Relatórios", 27, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);

        TextView sub = txt(relatorios
                ? "Acompanhe as vendas e os resultados da empresa."
                : "Seu acesso está limitado ao relatório das vendas de hoje.", 13, false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        if (resumoDia) {
            GestaoDbHelper db = new GestaoDbHelper(this);
            GestaoDbHelper.ResumoVendas hoje;
            try { hoje = db.resumoHoje(); }
            finally { db.close(); }

            LinearLayout card = TechCellUi.card(this);
            card.setLayoutParams(TechCellUi.fullCardParams(this, 14));
            TextView h = txt("VENDAS DE HOJE", 12, true);
            h.setTextColor(TechCellUi.MUTED);
            card.addView(h);

            TextView valor = txt(moeda.format(hoje.total), 26, true);
            valor.setTextColor(TechCellUi.GREEN);
            valor.setPadding(0, dp(5), 0, 0);
            card.addView(valor);

            TextView qtd = txt(hoje.quantidadeVendas + (hoje.quantidadeVendas == 1 ? " venda" : " vendas"), 13, true);
            qtd.setTextColor(TechCellUi.NAVY);
            qtd.setPadding(0, dp(2), 0, 0);
            card.addView(qtd);
            root.addView(card);

            Button dia = botao("📅  Relatório detalhado das vendas de hoje");
            TechCellUi.stylePrimary(this, dia, TechCellUi.BLUE);
            dia.setOnClickListener(v -> startActivity(new Intent(this, RelatorioDiaActivity.class)));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, this.dp(56));
            dp.setMargins(0, this.dp(8), 0, 0);
            root.addView(dia, dp);
        }

        if (relatorios) {
            TextView completos = txt("Relatórios completos", 17, true);
            completos.setPadding(0, dp(18), 0, dp(2));
            root.addView(completos);

            Button geral = botao("📊  Resumo • Produtos • Despesas • Estoque");
            TechCellUi.stylePrimary(this, geral, TechCellUi.NAVY);
            geral.setOnClickListener(v -> startActivity(new Intent(this, RelatoriosActivity.class)));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            gp.setMargins(0, dp(8), 0, 0);
            root.addView(geral, gp);
        }

        if (historico) {
            Button vendas = botao("🧾  Histórico de vendas");
            vendas.setOnClickListener(v -> startActivity(new Intent(this, HistoricoVendasActivity.class)));
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            vp.setMargins(0, dp(8), 0, 0);
            root.addView(vendas, vp);
        }

        setContentView(scroll);
    }
}
