package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PreparacaoFiscalActivity extends Activity {
    private GestaoDbHelper db;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView status(String titulo, String detalhe, boolean ok) {
        TextView t = txt((ok ? "✓ " : "• ") + titulo + "\n" + detalhe, 14, ok);
        t.setTextColor(Color.parseColor(ok ? "#176240" : "#B54708"));
        t.setBackgroundColor(Color.parseColor(ok ? "#ECFDF3" : "#FFF6ED"));
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(7), 0, dp(7));
        t.setLayoutParams(p);
        return t;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(52));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        p.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(p);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        Button voltar = action("← Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView titulo = txt("Preparação fiscal", 27, true);
        titulo.setPadding(0, dp(16), 0, 0);
        root.addView(titulo);

        TextView sub = txt("NF-e / NFC-e • Alpha 20 • preparação para homologação", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        sub.setPadding(0, dp(2), 0, dp(12));
        root.addView(sub);

        GestaoDbHelper.EmpresaConfig empresa = db.getEmpresaConfig();
        boolean empresaOk = ConfiguracoesFiscaisActivity.estaMinimamenteConfigurada(this);
        root.addView(status(
                "Dados da empresa",
                empresaOk
                        ? "CNPJ, IE, município e UF estão preenchidos."
                        : "Complete os dados fiscais da empresa antes de gerar XML.",
                empresaOk));

        int totalProdutos = db.count();
        int pendentes = db.countProdutosFiscalPendente();
        boolean produtosOk = totalProdutos > 0 && pendentes == 0;
        root.addView(status(
                "Dados fiscais dos produtos",
                totalProdutos == 0
                        ? "Nenhum produto cadastrado."
                        : pendentes == 0
                            ? totalProdutos + " produto(s) com dados mínimos preenchidos."
                            : pendentes + " de " + totalProdutos + " produto(s) ainda precisam de NCM/CFOP/tributação.",
                produtosOk));

        boolean homologacao = !empresa.producao;
        root.addView(status(
                "Ambiente",
                homologacao
                        ? "HOMOLOGAÇÃO selecionada. É o ambiente correto para os próximos testes."
                        : "PRODUÇÃO está selecionada. A transmissão real continua bloqueada nesta Alpha.",
                homologacao));

        root.addView(status(
                "Certificado digital ICP-Brasil",
                "Ainda não configurado no aplicativo. Nenhuma senha ou certificado deve ser gravado em código-fonte.",
                false));

        root.addView(status(
                "CSC / ID CSC da NFC-e",
                "Ainda não configurados. Serão necessários antes do teste real de NFC-e.",
                false));

        root.addView(status(
                "Transmissão SEFAZ",
                "Ainda desativada. Esta versão apenas prepara os dados e valida pendências.",
                false));

        TextView aviso = txt(
                "Os códigos tributários não são preenchidos automaticamente porque dependem " +
                "do produto, regime e operação. Confirme-os com a contabilidade antes da homologação.",
                13, true);
        aviso.setTextColor(Color.parseColor("#344054"));
        aviso.setBackgroundColor(Color.WHITE);
        aviso.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, dp(12), 0, dp(12));
        aviso.setLayoutParams(ap);
        root.addView(aviso);

        Button empresaBtn = action("Abrir dados da empresa");
        empresaBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ConfiguracoesFiscaisActivity.class)));
        root.addView(empresaBtn);

        Button produtosBtn = action("Abrir produtos / dados fiscais");
        produtosBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ProdutosActivity.class)));
        root.addView(produtosBtn);

        TextView rodape = txt(
                "Nenhuma emissão fiscal real é feita nesta tela.",
                12, true);
        rodape.setTextColor(Color.parseColor("#667085"));
        rodape.setGravity(Gravity.CENTER);
        rodape.setPadding(0, dp(18), 0, 0);
        root.addView(rodape);

        setContentView(scroll);
    }
}
