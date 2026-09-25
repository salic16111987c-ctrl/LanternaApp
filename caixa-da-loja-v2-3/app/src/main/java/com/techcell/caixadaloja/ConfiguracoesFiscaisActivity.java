package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
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
    private EditText razao, fantasia, cnpj, ie, regime, cep, logradouro, numero,
            complemento, bairro, municipio, uf, telefone, email, serieNfce, serieNfe;
    private Spinner ambiente;
    private TextView status;
    private GestaoDbHelper db;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
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
        e.setInputType(numeroApenas ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return e;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GestaoDbHelper(this);

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

        status = txt("", 13, true);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setVisibility(TextView.GONE);
        root.addView(status);

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
        GestaoDbHelper.EmpresaConfig e = db.getEmpresaConfig();
        razao.setText(e.razao);
        fantasia.setText(e.fantasia);
        cnpj.setText(e.cnpj);
        ie.setText(e.ie);
        regime.setText(e.regime);
        cep.setText(e.cep);
        logradouro.setText(e.logradouro);
        numero.setText(e.numero);
        complemento.setText(e.complemento);
        bairro.setText(e.bairro);
        municipio.setText(e.municipio);
        uf.setText(e.uf);
        telefone.setText(e.telefone);
        email.setText(e.email);
        serieNfce.setText(e.serieNfce == null || e.serieNfce.isEmpty() ? "1" : e.serieNfce);
        serieNfe.setText(e.serieNfe == null || e.serieNfe.isEmpty() ? "1" : e.serieNfe);
        ambiente.setSelection(e.producao ? 1 : 0);
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
        String ufTxt = uf.getText().toString().trim().toUpperCase();
        if (logradouro.getText().toString().trim().isEmpty() ||
                numero.getText().toString().trim().isEmpty() ||
                bairro.getText().toString().trim().isEmpty() ||
                municipio.getText().toString().trim().isEmpty() ||
                ufTxt.length() != 2) {
            Toast.makeText(this, "Confira o endereço da empresa e a UF.", Toast.LENGTH_LONG).show();
            return;
        }

        GestaoDbHelper.EmpresaConfig e = new GestaoDbHelper.EmpresaConfig();
        e.razao = razao.getText().toString().trim();
        e.fantasia = fantasia.getText().toString().trim();
        e.cnpj = doc;
        e.ie = ie.getText().toString().trim();
        e.regime = regime.getText().toString().trim();
        e.cep = soNumeros(cep.getText().toString());
        e.logradouro = logradouro.getText().toString().trim();
        e.numero = numero.getText().toString().trim();
        e.complemento = complemento.getText().toString().trim();
        e.bairro = bairro.getText().toString().trim();
        e.municipio = municipio.getText().toString().trim();
        e.uf = ufTxt;
        e.telefone = telefone.getText().toString().trim();
        e.email = email.getText().toString().trim();
        e.serieNfce = serieNfce.getText().toString().trim().isEmpty() ? "1" : serieNfce.getText().toString().trim();
        e.serieNfe = serieNfe.getText().toString().trim().isEmpty() ? "1" : serieNfe.getText().toString().trim();
        e.producao = ambiente.getSelectedItemPosition() == 1;

        db.saveEmpresaConfig(e);

        status.setText("✓ Dados da empresa salvos no banco.");
        status.setTextColor(Color.parseColor("#176240"));
        status.setBackgroundColor(Color.parseColor("#ECFDF3"));
        status.setVisibility(TextView.VISIBLE);
        Toast.makeText(this, "Dados fiscais salvos.", Toast.LENGTH_SHORT).show();
    }

    public static boolean estaMinimamenteConfigurada(Context context) {
        GestaoDbHelper.EmpresaConfig e = new GestaoDbHelper(context).getEmpresaConfig();
        return !e.razao.trim().isEmpty() &&
                soNumeros(e.cnpj).length() == 14 &&
                !e.ie.trim().isEmpty() &&
                !e.municipio.trim().isEmpty() &&
                e.uf.trim().length() == 2;
    }

    public static String nomeEmpresa(Context context) {
        GestaoDbHelper.EmpresaConfig e = new GestaoDbHelper(context).getEmpresaConfig();
        if (e.fantasia != null && !e.fantasia.trim().isEmpty()) return e.fantasia.trim();
        if (e.razao != null && !e.razao.trim().isEmpty()) return e.razao.trim();
        return "TECH CELL";
    }

    private static String soNumeros(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }
}
