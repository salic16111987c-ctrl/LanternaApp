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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FornecedoresActivity extends Activity {
    private GestaoDbHelper db;
    private LinearLayout lista;
    private EditText busca;
    private CheckBox mostrarInativos;

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#172033"));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private EditText addCampo(LinearLayout root, String hint, boolean num) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(num ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        root.addView(e);
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

        Button voltar = new Button(this);
        voltar.setText("←  Voltar");
        voltar.setAllCaps(false);
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar);

        TextView title = txt("Fornecedores", 27, true);
        title.setPadding(0, dp(18), 0, dp(2));
        root.addView(title);

        TextView sub = txt("Cadastro de fornecedores • Alpha 23", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        Button novo = new Button(this);
        novo.setText("+  NOVO FORNECEDOR");
        novo.setAllCaps(false);
        novo.setTextColor(Color.WHITE);
        novo.setBackgroundColor(Color.parseColor("#175CD3"));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        np.setMargins(0, dp(16), 0, dp(10));
        novo.setLayoutParams(np);
        novo.setOnClickListener(v -> editar(null));
        root.addView(novo);

        busca = new EditText(this);
        busca.setHint("Buscar por nome, fantasia, CPF/CNPJ ou contato");
        busca.setSingleLine(true);
        busca.setTextSize(16);
        root.addView(busca, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        mostrarInativos = new CheckBox(this);
        mostrarInativos.setText("Mostrar fornecedores inativos");
        mostrarInativos.setTextSize(14);
        mostrarInativos.setOnCheckedChangeListener((b, checked) -> carregar());
        root.addView(mostrarInativos);

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
        lista.removeAllViews();
        String termo = busca == null ? "" : busca.getText().toString().trim();
        String digitos = CadastroBrasilUtils.apenasDigitos(termo);
        if (digitos.length() >= 3) termo = digitos;

        List<GestaoDbHelper.Fornecedor> fornecedores =
                db.listFornecedores(termo, mostrarInativos != null && mostrarInativos.isChecked());

        if (fornecedores.isEmpty()) {
            TextView vazio = txt("Nenhum fornecedor cadastrado.", 14, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(24), 0, dp(24));
            lista.addView(vazio);
            return;
        }

        for (GestaoDbHelper.Fornecedor f : fornecedores) {
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
            TextView nome = txt(f.nome, 16, true);
            top.addView(nome, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if ("INATIVO".equalsIgnoreCase(f.status)) {
                TextView st = txt("INATIVO", 11, true);
                st.setTextColor(Color.parseColor("#B42318"));
                top.addView(st);
            }
            card.addView(top);

            if (f.fantasia != null && !f.fantasia.trim().isEmpty() &&
                    !f.fantasia.trim().equalsIgnoreCase(f.nome == null ? "" : f.nome.trim())) {
                TextView fantasia = txt(f.fantasia.trim(), 13, false);
                fantasia.setTextColor(Color.parseColor("#475467"));
                card.addView(fantasia);
            }

            String doc = f.documento == null ? "" : f.documento;
            if (!doc.isEmpty()) {
                doc = CadastroBrasilUtils.formatarDocumento(doc, "PJ".equalsIgnoreCase(f.tipo));
            }
            TextView info = txt((f.tipo == null ? "PJ" : f.tipo) +
                    (doc.isEmpty() ? "" : " • " + doc) +
                    (f.municipio == null || f.municipio.isEmpty() ? "" : " • " + f.municipio + "/" + f.uf),
                    12, false);
            info.setTextColor(Color.parseColor("#667085"));
            card.addView(info);

            if (f.telefone != null && !f.telefone.trim().isEmpty()) {
                TextView contato = txt("Tel.: " + f.telefone.trim() +
                        (f.contato == null || f.contato.trim().isEmpty() ? "" : " • " + f.contato.trim()),
                        12, false);
                contato.setTextColor(Color.parseColor("#667085"));
                card.addView(contato);
            }

            LinearLayout botoes = new LinearLayout(this);
            botoes.setOrientation(LinearLayout.HORIZONTAL);

            Button editar = new Button(this);
            editar.setText("Editar");
            editar.setAllCaps(false);
            editar.setOnClickListener(v -> editar(f));
            botoes.addView(editar, new LinearLayout.LayoutParams(0, dp(46), 1));

            Button status = new Button(this);
            boolean ativo = !"INATIVO".equalsIgnoreCase(f.status);
            status.setText(ativo ? "Inativar" : "Reativar");
            status.setAllCaps(false);
            status.setTextColor(Color.parseColor(ativo ? "#B42318" : "#176240"));
            status.setOnClickListener(v -> alterarStatus(f, !ativo));
            botoes.addView(status, new LinearLayout.LayoutParams(0, dp(46), 1));

            card.addView(botoes);
            lista.addView(card);
        }
    }

    private void editar(GestaoDbHelper.Fornecedor fornecedor) {
        GestaoDbHelper.Fornecedor f = fornecedor == null
                ? new GestaoDbHelper.Fornecedor() : fornecedor;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), dp(10));
        sv.addView(box);

        Spinner tipo = new Spinner(this);
        String[] tipos = {"PJ - Pessoa Jurídica", "PF - Pessoa Física"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tipo.setAdapter(ad);
        tipo.setSelection("PF".equalsIgnoreCase(f.tipo) ? 1 : 0);
        box.addView(tipo);

        TextView consultaStatus = txt("", 12, true);
        consultaStatus.setVisibility(View.GONE);
        consultaStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.addView(consultaStatus);

        EditText nome = addCampo(box, "Nome / Razão social *", false);
        EditText fantasia = addCampo(box, "Nome fantasia", false);
        EditText documento = addCampo(box, "CNPJ / CPF *", true);
        EditText ie = addCampo(box, "Inscrição Estadual (se houver)", false);
        EditText contato = addCampo(box, "Pessoa de contato", false);
        EditText cep = addCampo(box, "CEP", true);
        EditText logradouro = addCampo(box, "Logradouro", false);
        EditText numero = addCampo(box, "Número", false);
        EditText complemento = addCampo(box, "Complemento", false);
        EditText bairro = addCampo(box, "Bairro", false);
        EditText municipio = addCampo(box, "Município", false);
        EditText uf = addCampo(box, "UF", false);
        EditText telefone = addCampo(box, "Telefone", false);
        EditText email = addCampo(box, "E-mail", false);
        EditText observacao = addCampo(box, "Observação", false);

        nome.setText(f.nome);
        fantasia.setText(f.fantasia);
        documento.setText(CadastroBrasilUtils.formatarDocumento(
                f.documento, !"PF".equalsIgnoreCase(f.tipo)));
        ie.setText(f.ie);
        contato.setText(f.contato);
        cep.setText(CadastroBrasilUtils.formatarCep(f.cep));
        logradouro.setText(f.logradouro);
        numero.setText(f.numero);
        complemento.setText(f.complemento);
        bairro.setText(f.bairro);
        municipio.setText(f.municipio);
        uf.setText(f.uf);
        telefone.setText(f.telefone);
        email.setText(f.email);
        observacao.setText(f.observacao);

        final String[] ultimoCnpj = {CadastroBrasilUtils.apenasDigitos(f.documento)};
        final String[] ultimoCep = {CadastroBrasilUtils.apenasDigitos(f.cep)};

        CadastroBrasilUtils.aplicarMascaraDocumento(documento,
                () -> tipo.getSelectedItemPosition() == 0);
        CadastroBrasilUtils.aplicarMascaraCep(cep);

        Runnable atualizarDocumento = () -> {
            boolean pj = tipo.getSelectedItemPosition() == 0;
            CadastroBrasilUtils.reformatarDocumento(documento, pj);
            documento.setHint(pj ? "00.000.000/0000-00" : "000.000.000-00");
        };

        tipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                atualizarDocumento.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        documento.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c1, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c1) {}
            public void afterTextChanged(Editable e) {
                String doc = CadastroBrasilUtils.apenasDigitos(e.toString());
                boolean pj = tipo.getSelectedItemPosition() == 0;

                if (!pj) {
                    if (doc.length() == 11 && !CadastroBrasilUtils.cpfValido(doc))
                        documento.setError("CPF inválido");
                    else documento.setError(null);
                    return;
                }

                if (doc.length() < 14) {
                    documento.setError(null);
                    return;
                }
                if (!CadastroBrasilUtils.cnpjValido(doc)) {
                    documento.setError("CNPJ inválido");
                    return;
                }
                documento.setError(null);

                if (doc.equals(ultimoCnpj[0])) return;
                ultimoCnpj[0] = doc;
                mostrarStatus(consultaStatus, "Consultando CNPJ...", "#175CD3", "#EFF8FF");

                CadastroBrasilUtils.buscarCnpj(FornecedoresActivity.this, doc,
                        new CadastroBrasilUtils.CnpjCallback() {
                            @Override public void onSuccess(CadastroBrasilUtils.CnpjData d) {
                                nome.setText(d.razaoSocial);
                                if (!d.nomeFantasia.isEmpty()) fantasia.setText(d.nomeFantasia);
                                ultimoCep[0] = d.cep;
                                if (!d.cep.isEmpty()) cep.setText(CadastroBrasilUtils.formatarCep(d.cep));
                                if (!d.logradouro.isEmpty()) logradouro.setText(d.logradouro);
                                if (!d.numero.isEmpty()) numero.setText(d.numero);
                                if (!d.complemento.isEmpty()) complemento.setText(d.complemento);
                                if (!d.bairro.isEmpty()) bairro.setText(d.bairro);
                                if (!d.municipio.isEmpty()) municipio.setText(d.municipio);
                                if (!d.uf.isEmpty()) uf.setText(d.uf);
                                if (!d.telefone.isEmpty()) telefone.setText(d.telefone);
                                if (!d.email.isEmpty()) email.setText(d.email);
                                mostrarStatus(consultaStatus,
                                        "✓ Empresa localizada" +
                                                (d.situacao.isEmpty() ? "" : " • " + d.situacao),
                                        "#176240", "#ECFDF3");
                            }
                            @Override public void onError(String mensagem) {
                                mostrarStatus(consultaStatus, mensagem, "#B54708", "#FFF6ED");
                            }
                        });
            }
        });

        cep.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c1, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c1) {}
            public void afterTextChanged(Editable e) {
                String d = CadastroBrasilUtils.apenasDigitos(e.toString());
                if (d.length() != 8 || d.equals(ultimoCep[0])) return;
                ultimoCep[0] = d;
                mostrarStatus(consultaStatus, "Consultando CEP...", "#175CD3", "#EFF8FF");
                CadastroBrasilUtils.buscarCep(FornecedoresActivity.this, d,
                        new CadastroBrasilUtils.CepCallback() {
                            @Override public void onSuccess(CadastroBrasilUtils.CepData x) {
                                if (!x.logradouro.isEmpty()) logradouro.setText(x.logradouro);
                                if (!x.bairro.isEmpty()) bairro.setText(x.bairro);
                                if (!x.municipio.isEmpty()) municipio.setText(x.municipio);
                                if (!x.uf.isEmpty()) uf.setText(x.uf);
                                mostrarStatus(consultaStatus, "✓ Endereço localizado pelo CEP",
                                        "#176240", "#ECFDF3");
                                numero.requestFocus();
                            }
                            @Override public void onError(String mensagem) {
                                mostrarStatus(consultaStatus, mensagem, "#B54708", "#FFF6ED");
                            }
                        });
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(fornecedor == null ? "Novo fornecedor" : "Editar fornecedor")
                .setView(sv)
                .setPositiveButton("SALVAR", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    boolean pj = tipo.getSelectedItemPosition() == 0;
                    String doc = CadastroBrasilUtils.apenasDigitos(documento.getText().toString());
                    boolean valido = pj ? CadastroBrasilUtils.cnpjValido(doc)
                            : CadastroBrasilUtils.cpfValido(doc);

                    if (nome.getText().toString().trim().isEmpty()) {
                        nome.setError("Informe o nome / razão social");
                        return;
                    }
                    if (!valido) {
                        documento.setError(pj ? "CNPJ inválido" : "CPF inválido");
                        return;
                    }

                    f.tipo = pj ? "PJ" : "PF";
                    f.nome = nome.getText().toString().trim();
                    f.fantasia = fantasia.getText().toString().trim();
                    f.documento = doc;
                    f.ie = ie.getText().toString().trim();
                    f.contato = contato.getText().toString().trim();
                    f.logradouro = logradouro.getText().toString().trim();
                    f.numero = numero.getText().toString().trim();
                    f.complemento = complemento.getText().toString().trim();
                    f.bairro = bairro.getText().toString().trim();
                    f.cep = CadastroBrasilUtils.apenasDigitos(cep.getText().toString());
                    f.municipio = municipio.getText().toString().trim();
                    f.uf = uf.getText().toString().trim().toUpperCase();
                    f.telefone = telefone.getText().toString().trim();
                    f.email = email.getText().toString().trim();
                    f.observacao = observacao.getText().toString().trim();
                    if (f.status == null || f.status.trim().isEmpty()) f.status = "ATIVO";

                    try {
                        db.saveFornecedor(f);
                        dialog.dismiss();
                        carregar();
                        Toast.makeText(this, "Fornecedor salvo.", Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        Toast.makeText(this,
                                "Não foi possível salvar. Verifique se CPF/CNPJ já está cadastrado.",
                                Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }

    private void alterarStatus(GestaoDbHelper.Fornecedor f, boolean ativar) {
        new AlertDialog.Builder(this)
                .setTitle(ativar ? "Reativar fornecedor?" : "Inativar fornecedor?")
                .setMessage(f.nome + (ativar ? "\nEle voltará a aparecer na lista principal."
                        : "\nO cadastro será preservado e poderá ser reativado depois."))
                .setPositiveButton(ativar ? "Reativar" : "Inativar", (d,w) -> {
                    db.setFornecedorAtivo(f.id, ativar);
                    carregar();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarStatus(TextView v, String msg, String texto, String fundo) {
        v.setText(msg);
        v.setTextColor(Color.parseColor(texto));
        v.setBackgroundColor(Color.parseColor(fundo));
        v.setVisibility(View.VISIBLE);
    }
}
