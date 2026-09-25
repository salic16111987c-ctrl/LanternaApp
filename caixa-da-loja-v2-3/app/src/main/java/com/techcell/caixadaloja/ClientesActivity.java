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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class ClientesActivity extends Activity {
    private GestaoDbHelper db;
    private LinearLayout lista;
    private EditText busca;

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

        TextView title = txt("Clientes", 27, true);
        title.setPadding(0, dp(18), 0, dp(2));
        root.addView(title);

        TextView sub = txt("Pessoa Física e Pessoa Jurídica • Alpha 14", 14, false);
        sub.setTextColor(Color.parseColor("#667085"));
        root.addView(sub);

        Button novo = new Button(this);
        novo.setText("+  NOVO CLIENTE");
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
        busca.setHint("Buscar por nome, CPF ou CNPJ");
        busca.setSingleLine(true);
        busca.setTextSize(16);
        root.addView(busca, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

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

        List<GestaoDbHelper.Cliente> clientes = db.listClientes(termo);

        if (clientes.isEmpty()) {
            TextView vazio = txt("Nenhum cliente cadastrado.", 14, false);
            vazio.setTextColor(Color.parseColor("#667085"));
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(24), 0, dp(24));
            lista.addView(vazio);
            return;
        }

        for (GestaoDbHelper.Cliente c : clientes) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(8), 0, 0);
            card.setLayoutParams(cp);

            TextView nome = txt(c.nome, 16, true);
            card.addView(nome);

            String doc = c.documento == null ? "" : c.documento;
            if (!doc.isEmpty()) doc = CadastroBrasilUtils.formatarDocumento(doc, "PJ".equalsIgnoreCase(c.tipo));
            TextView info = txt((c.tipo == null ? "PF" : c.tipo) +
                    (doc.isEmpty() ? "" : " • " + doc) +
                    (c.municipio == null || c.municipio.isEmpty() ? "" : " • " + c.municipio + "/" + c.uf),
                    12, false);
            info.setTextColor(Color.parseColor("#667085"));
            card.addView(info);

            LinearLayout botoes = new LinearLayout(this);
            botoes.setOrientation(LinearLayout.HORIZONTAL);

            Button editar = new Button(this);
            editar.setText("Editar");
            editar.setAllCaps(false);
            editar.setOnClickListener(v -> editar(c));
            botoes.addView(editar, new LinearLayout.LayoutParams(0, dp(46), 1));

            Button excluir = new Button(this);
            excluir.setText("Excluir");
            excluir.setAllCaps(false);
            excluir.setTextColor(Color.parseColor("#B42318"));
            excluir.setOnClickListener(v -> confirmarExcluir(c));
            botoes.addView(excluir, new LinearLayout.LayoutParams(0, dp(46), 1));

            card.addView(botoes);
            lista.addView(card);
        }
    }

    private EditText addCampo(LinearLayout root, String hint, boolean num) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(num ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        root.addView(e);
        return e;
    }

    private void editar(GestaoDbHelper.Cliente cliente) {
        GestaoDbHelper.Cliente c = cliente == null ? new GestaoDbHelper.Cliente() : cliente;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), dp(10));
        sv.addView(box);

        Spinner tipo = new Spinner(this);
        String[] tipos = {"PF - Pessoa Física", "PJ - Pessoa Jurídica"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tipo.setAdapter(ad);
        tipo.setSelection("PJ".equalsIgnoreCase(c.tipo) ? 1 : 0);
        box.addView(tipo);

        TextView consultaStatus = txt("", 12, true);
        consultaStatus.setVisibility(View.GONE);
        consultaStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.addView(consultaStatus);

        EditText nome = addCampo(box, "Nome / Razão social *", false);
        EditText documento = addCampo(box, "CPF / CNPJ *", true);
        EditText ie = addCampo(box, "Inscrição Estadual (se houver)", false);
        EditText cep = addCampo(box, "CEP", true);
        EditText logradouro = addCampo(box, "Logradouro", false);
        EditText numero = addCampo(box, "Número", false);
        EditText complemento = addCampo(box, "Complemento", false);
        EditText bairro = addCampo(box, "Bairro", false);
        EditText municipio = addCampo(box, "Município", false);
        EditText uf = addCampo(box, "UF", false);
        EditText telefone = addCampo(box, "Telefone", false);
        EditText email = addCampo(box, "E-mail", false);

        nome.setText(c.nome);
        documento.setText(CadastroBrasilUtils.formatarDocumento(c.documento, "PJ".equalsIgnoreCase(c.tipo)));
        ie.setText(c.ie);
        cep.setText(CadastroBrasilUtils.formatarCep(c.cep));
        logradouro.setText(c.logradouro);
        numero.setText(c.numero);
        complemento.setText(c.complemento);
        bairro.setText(c.bairro);
        municipio.setText(c.municipio);
        uf.setText(c.uf);
        telefone.setText(c.telefone);
        email.setText(c.email);

        final String[] ultimoCnpj = {CadastroBrasilUtils.apenasDigitos(c.documento)};
        final String[] ultimoCep = {CadastroBrasilUtils.apenasDigitos(c.cep)};

        CadastroBrasilUtils.aplicarMascaraDocumento(documento,
                () -> tipo.getSelectedItemPosition() == 1);
        CadastroBrasilUtils.aplicarMascaraCep(cep);

        Runnable atualizarDocumento = () -> {
            boolean pj = tipo.getSelectedItemPosition() == 1;
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
                boolean pj = tipo.getSelectedItemPosition() == 1;

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

                CadastroBrasilUtils.buscarCnpj(ClientesActivity.this, doc,
                        new CadastroBrasilUtils.CnpjCallback() {
                            @Override public void onSuccess(CadastroBrasilUtils.CnpjData d) {
                                nome.setText(d.razaoSocial);
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
                                        "✓ Empresa localizada" + (d.situacao.isEmpty() ? "" : " • " + d.situacao),
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
                CadastroBrasilUtils.buscarCep(ClientesActivity.this, d,
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
                .setTitle(cliente == null ? "Novo cliente" : "Editar cliente")
                .setView(sv)
                .setPositiveButton("SALVAR", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String tipoSel = tipo.getSelectedItemPosition() == 1 ? "PJ" : "PF";
            String doc = CadastroBrasilUtils.apenasDigitos(documento.getText().toString());
            boolean valido = "PJ".equals(tipoSel)
                    ? CadastroBrasilUtils.cnpjValido(doc)
                    : CadastroBrasilUtils.cpfValido(doc);

            if (nome.getText().toString().trim().isEmpty()) {
                nome.setError("Informe o nome / razão social");
                return;
            }
            if (!valido) {
                documento.setError("PJ".equals(tipoSel) ? "CNPJ inválido" : "CPF inválido");
                return;
            }

            c.tipo = tipoSel;
            c.nome = nome.getText().toString().trim();
            c.documento = doc;
            c.ie = ie.getText().toString().trim();
            c.logradouro = logradouro.getText().toString().trim();
            c.numero = numero.getText().toString().trim();
            c.complemento = complemento.getText().toString().trim();
            c.bairro = bairro.getText().toString().trim();
            c.cep = CadastroBrasilUtils.apenasDigitos(cep.getText().toString());
            c.municipio = municipio.getText().toString().trim();
            c.uf = uf.getText().toString().trim().toUpperCase();
            c.telefone = telefone.getText().toString().trim();
            c.email = email.getText().toString().trim();

            try {
                db.saveCliente(c);
                dialog.dismiss();
                carregar();
                Toast.makeText(this, "Cliente salvo.", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this,
                        "Não foi possível salvar. Verifique se CPF/CNPJ já está cadastrado.",
                        Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private void mostrarStatus(TextView v, String msg, String texto, String fundo) {
        v.setText(msg);
        v.setTextColor(Color.parseColor(texto));
        v.setBackgroundColor(Color.parseColor(fundo));
        v.setVisibility(View.VISIBLE);
    }

    private void confirmarExcluir(GestaoDbHelper.Cliente c) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir cliente?")
                .setMessage(c.nome)
                .setPositiveButton("Excluir", (d,w) -> {
                    db.deleteCliente(c.id);
                    carregar();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
