package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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

public class ConfiguracoesFiscaisActivity extends Activity {
    private static final String PREFS = "fiscal_config";

    private EditText razao, fantasia, cnpj, ie, regime, cep, logradouro, numero,
            complemento, bairro, municipio, uf, telefone, email, serieNfce, serieNfe;
    private Spinner ambiente;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private EditText campo(String label, String key) {
        LinearLayout box = new LinearLayout(this);
        return null;
    }

    private EditText addCampo(LinearLayout root, String label, String hint, boolean numeroApenas) {
        TextView l = txt(label, 13, true);
        l.setTextColor(Color.parseColor("#475467"));
        l.setPadding(0, dp(10), 0, dp(3));
        root.addView(l);

        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        if (numeroApenas) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return e;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        TextView titulo = txt("Configurações fiscais", 27, true);
        titulo.setPadding(0, dp(18), 0, 0);
        root.addView(titulo);

        TextView sub = txt("Dados da empresa emitente", 15, false);
        sub.setTextColor(Color.parseColor("#667085"));
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        TextView aviso = txt(
                "Estes dados serão usados no cupom fiscal (NFC-e) e na nota fiscal (NF-e). " +
                "Certificado digital e CSC serão configurados na etapa de integração real com a SEFAZ.",
                13, false);
        aviso.setTextColor(Color.parseColor("#344054"));
        aviso.setBackgroundColor(Color.parseColor("#EAF3FF"));
        aviso.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(aviso);

        razao = addCampo(root, "Razão social *", "Razão social da empresa", false);
        fantasia = addCampo(root, "Nome fantasia", "Tech Cell", false);
        cnpj = addCampo(root, "CNPJ *", "Somente números", true);
        ie = addCampo(root, "Inscrição Estadual *", "Inscrição Estadual", false);
        regime = addCampo(root, "Regime tributário", "Ex.: Simples Nacional", false);

        TextView end = txt("ENDEREÇO DA EMPRESA", 14, true);
        end.setTextColor(Color.parseColor("#175CD3"));
        end.setPadding(0, dp(18), 0, dp(2));
        root.addView(end);

        cep = addCampo(root, "CEP", "Somente números", true);
        logradouro = addCampo(root, "Logradouro *", "Rua / Avenida", false);
        numero = addCampo(root, "Número *", "Número", false);
        complemento = addCampo(root, "Complemento", "Opcional", false);
        bairro = addCampo(root, "Bairro *", "Bairro", false);
        municipio = addCampo(root, "Município *", "Cidade", false);
        uf = addCampo(root, "UF *", "MG", false);
        telefone = addCampo(root, "Telefone", "Telefone da empresa", false);
        email = addCampo(root, "E-mail", "E-mail da empresa", false);

        TextView fiscal = txt("NUMERAÇÃO FISCAL", 14, true);
        fiscal.setTextColor(Color.parseColor("#175CD3"));
        fiscal.setPadding(0, dp(18), 0, dp(2));
        root.addView(fiscal);

        serieNfce = addCampo(root, "Série NFC-e", "Ex.: 1", true);
        serieNfe = addCampo(root, "Série NF-e", "Ex.: 1", true);

        TextView ambLabel = txt("Ambiente", 13, true);
        ambLabel.setTextColor(Color.parseColor("#475467"));
        ambLabel.setPadding(0, dp(10), 0, dp(3));
        root.addView(ambLabel);

        ambiente = new Spinner(this);
        String[] ambientes = {"HOMOLOGAÇÃO (testes)", "PRODUÇÃO"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, ambientes);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ambiente.setAdapter(ad);
        root.addView(ambiente);

        Button salvar = new Button(this);
        salvar.setText("SALVAR DADOS DA EMPRESA");
        salvar.setAllCaps(false);
        salvar.setTextSize(16);
        salvar.setTextColor(Color.WHITE);
        salvar.setBackgroundColor(Color.parseColor("#07884B"));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        sp.setMargins(0, dp(22), 0, 0);
        salvar.setLayoutParams(sp);
        salvar.setOnClickListener(v -> salvar());
        root.addView(salvar);

        carregar();
        setContentView(scroll);
    }

    private void carregar() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        razao.setText(p.getString("razao", ""));
        fantasia.setText(p.getString("fantasia", ""));
        cnpj.setText(p.getString("cnpj", ""));
        ie.setText(p.getString("ie", ""));
        regime.setText(p.getString("regime", ""));
        cep.setText(p.getString("cep", ""));
        logradouro.setText(p.getString("logradouro", ""));
        numero.setText(p.getString("numero", ""));
        complemento.setText(p.getString("complemento", ""));
        bairro.setText(p.getString("bairro", ""));
        municipio.setText(p.getString("municipio", ""));
        uf.setText(p.getString("uf", ""));
        telefone.setText(p.getString("telefone", ""));
        email.setText(p.getString("email", ""));
        serieNfce.setText(p.getString("serie_nfce", "1"));
        serieNfe.setText(p.getString("serie_nfe", "1"));
        ambiente.setSelection(p.getBoolean("producao", false) ? 1 : 0);
    }

    private void salvar() {
        String doc = soNumeros(cnpj.getText().toString());
        if (razao.getText().toString().trim().isEmpty()) {
            razao.setError("Informe a razão social");
            return;
        }
        if (doc.length() != 14) {
            cnpj.setError("Informe um CNPJ com 14 dígitos");
            return;
        }
        if (ie.getText().toString().trim().isEmpty()) {
            ie.setError("Informe a Inscrição Estadual");
            return;
        }
        if (logradouro.getText().toString().trim().isEmpty() ||
                numero.getText().toString().trim().isEmpty() ||
                bairro.getText().toString().trim().isEmpty() ||
                municipio.getText().toString().trim().isEmpty() ||
                uf.getText().toString().trim().length() != 2) {
            Toast.makeText(this,
                    "Confira o endereço da empresa e a UF.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putString("razao", razao.getText().toString().trim());
        e.putString("fantasia", fantasia.getText().toString().trim());
        e.putString("cnpj", doc);
        e.putString("ie", ie.getText().toString().trim());
        e.putString("regime", regime.getText().toString().trim());
        e.putString("cep", soNumeros(cep.getText().toString()));
        e.putString("logradouro", logradouro.getText().toString().trim());
        e.putString("numero", numero.getText().toString().trim());
        e.putString("complemento", complemento.getText().toString().trim());
        e.putString("bairro", bairro.getText().toString().trim());
        e.putString("municipio", municipio.getText().toString().trim());
        e.putString("uf", uf.getText().toString().trim().toUpperCase());
        e.putString("telefone", telefone.getText().toString().trim());
        e.putString("email", email.getText().toString().trim());
        e.putString("serie_nfce", serieNfce.getText().toString().trim());
        e.putString("serie_nfe", serieNfe.getText().toString().trim());
        e.putBoolean("producao", ambiente.getSelectedItemPosition() == 1);
        e.apply();

        Toast.makeText(this, "Dados fiscais salvos.", Toast.LENGTH_SHORT).show();
        finish();
    }

    public static boolean estaMinimamenteConfigurada(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !p.getString("razao", "").trim().isEmpty() &&
                soNumeros(p.getString("cnpj", "")).length() == 14 &&
                !p.getString("ie", "").trim().isEmpty() &&
                !p.getString("municipio", "").trim().isEmpty() &&
                p.getString("uf", "").trim().length() == 2;
    }

    public static String nomeEmpresa(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fantasia = p.getString("fantasia", "").trim();
        if (!fantasia.isEmpty()) return fantasia;
        String razao = p.getString("razao", "").trim();
        return razao.isEmpty() ? "TECH CELL" : razao;
    }

    private static String soNumeros(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }
}
