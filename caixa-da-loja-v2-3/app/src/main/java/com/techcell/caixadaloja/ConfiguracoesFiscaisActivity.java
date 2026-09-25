package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
    private String ultimoCnpjConsultado = "";
    private String ultimoCepConsultado = "";

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

        TextView sub = txt("Dados da empresa emitente • Alpha 20", 15, false);
        sub.setTextColor(Color.parseColor("#667085"));
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        status = txt("", 13, true);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setVisibility(TextView.GONE);
        root.addView(status);

        razao = addCampo(root, "Razão social *", "Preenchida automaticamente pelo CNPJ", false);
        fantasia = addCampo(root, "Nome fantasia", "Preenchido automaticamente quando disponível", false);
        cnpj = addCampo(root, "CNPJ *", "00.000.000/0000-00", true);
        ie = addCampo(root, "Inscrição Estadual *", "Inscrição Estadual", false);
        regime = addCampo(root, "Regime tributário", "Ex.: Simples Nacional", false);

        TextView end = txt("ENDEREÇO DA EMPRESA", 14, true);
        end.setTextColor(Color.parseColor("#175CD3"));
        end.setPadding(0, dp(18), 0, dp(2));
        root.addView(end);

        cep = addCampo(root, "CEP", "00000-000", true);
        logradouro = addCampo(root, "Logradouro *", "Preenchido pelo CEP", false);
        numero = addCampo(root, "Número *", "Número", false);
        complemento = addCampo(root, "Complemento", "Opcional", false);
        bairro = addCampo(root, "Bairro *", "Preenchido pelo CEP", false);
        municipio = addCampo(root, "Município *", "Preenchido pelo CEP", false);
        uf = addCampo(root, "UF *", "Preenchida pelo CEP", false);
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
        configurarAutomacoes();
        setContentView(scroll);
    }

    private void carregar() {
        GestaoDbHelper.EmpresaConfig e = db.getEmpresaConfig();
        razao.setText(e.razao);
        fantasia.setText(e.fantasia);
        cnpj.setText(CadastroBrasilUtils.formatarCnpj(e.cnpj));
        ie.setText(e.ie);
        regime.setText(e.regime);
        cep.setText(CadastroBrasilUtils.formatarCep(e.cep));
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
        ultimoCnpjConsultado = CadastroBrasilUtils.apenasDigitos(e.cnpj);
        ultimoCepConsultado = CadastroBrasilUtils.apenasDigitos(e.cep);
    }

    private void configurarAutomacoes() {
        CadastroBrasilUtils.aplicarMascaraCnpj(cnpj);
        CadastroBrasilUtils.aplicarMascaraCep(cep);

        cnpj.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String doc = CadastroBrasilUtils.apenasDigitos(e.toString());
                if (doc.length() < 14) {
                    cnpj.setError(null);
                    return;
                }
                if (!CadastroBrasilUtils.cnpjValido(doc)) {
                    cnpj.setError("CNPJ inválido");
                    return;
                }
                cnpj.setError(null);
                if (doc.equals(ultimoCnpjConsultado)) return;
                ultimoCnpjConsultado = doc;
                consultarCnpj(doc);
            }
        });

        cep.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String d = CadastroBrasilUtils.apenasDigitos(e.toString());
                if (d.length() != 8 || d.equals(ultimoCepConsultado)) return;
                ultimoCepConsultado = d;
                consultarCep(d);
            }
        });
    }

    private void consultarCnpj(String doc) {
        mostrarStatus("Consultando CNPJ...", "#175CD3", "#EFF8FF");
        CadastroBrasilUtils.buscarCnpj(this, doc, new CadastroBrasilUtils.CnpjCallback() {
            @Override public void onSuccess(CadastroBrasilUtils.CnpjData d) {
                razao.setText(d.razaoSocial);
                if (!d.nomeFantasia.isEmpty()) fantasia.setText(d.nomeFantasia);

                ultimoCepConsultado = d.cep;
                if (!d.cep.isEmpty()) cep.setText(CadastroBrasilUtils.formatarCep(d.cep));
                if (!d.logradouro.isEmpty()) logradouro.setText(d.logradouro);
                if (!d.numero.isEmpty()) numero.setText(d.numero);
                if (!d.complemento.isEmpty()) complemento.setText(d.complemento);
                if (!d.bairro.isEmpty()) bairro.setText(d.bairro);
                if (!d.municipio.isEmpty()) municipio.setText(d.municipio);
                if (!d.uf.isEmpty()) uf.setText(d.uf);
                if (!d.telefone.isEmpty()) telefone.setText(d.telefone);
                if (!d.email.isEmpty()) email.setText(d.email);

                String situacao = d.situacao.isEmpty() ? "" : " • Situação: " + d.situacao;
                mostrarStatus("✓ Empresa localizada" + situacao, "#176240", "#ECFDF3");
            }

            @Override public void onError(String mensagem) {
                mostrarStatus(mensagem + " Você pode preencher manualmente.", "#B54708", "#FFF6ED");
            }
        });
    }

    private void consultarCep(String d) {
        mostrarStatus("Consultando CEP...", "#175CD3", "#EFF8FF");
        CadastroBrasilUtils.buscarCep(this, d, new CadastroBrasilUtils.CepCallback() {
            @Override public void onSuccess(CadastroBrasilUtils.CepData x) {
                if (!x.logradouro.isEmpty()) logradouro.setText(x.logradouro);
                if (!x.bairro.isEmpty()) bairro.setText(x.bairro);
                if (!x.municipio.isEmpty()) municipio.setText(x.municipio);
                if (!x.uf.isEmpty()) uf.setText(x.uf);
                mostrarStatus("✓ Endereço localizado pelo CEP", "#176240", "#ECFDF3");
                numero.requestFocus();
            }
            @Override public void onError(String mensagem) {
                mostrarStatus(mensagem + " Preencha o endereço manualmente.", "#B54708", "#FFF6ED");
            }
        });
    }

    private void mostrarStatus(String mensagem, String texto, String fundo) {
        status.setText(mensagem);
        status.setTextColor(Color.parseColor(texto));
        status.setBackgroundColor(Color.parseColor(fundo));
        status.setVisibility(TextView.VISIBLE);
    }

    private void salvar() {
        String doc = CadastroBrasilUtils.apenasDigitos(cnpj.getText().toString());
        if (razao.getText().toString().trim().isEmpty()) {
            razao.setError("Informe a razão social");
            return;
        }
        if (!CadastroBrasilUtils.cnpjValido(doc)) {
            cnpj.setError("CNPJ inválido");
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
        e.cep = CadastroBrasilUtils.apenasDigitos(cep.getText().toString());
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
        mostrarStatus("✓ Dados da empresa salvos no banco.", "#176240", "#ECFDF3");
        Toast.makeText(this, "Dados fiscais salvos.", Toast.LENGTH_SHORT).show();
    }

    public static boolean estaMinimamenteConfigurada(Context context) {
        GestaoDbHelper.EmpresaConfig e = new GestaoDbHelper(context).getEmpresaConfig();
        return !e.razao.trim().isEmpty() &&
                CadastroBrasilUtils.cnpjValido(e.cnpj) &&
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
}
