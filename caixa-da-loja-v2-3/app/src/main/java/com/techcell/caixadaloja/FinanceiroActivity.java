package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

public class FinanceiroActivity extends Activity {
    private static final int REQ_XML_NFE = 4210;

    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));

    private GestaoDbHelper db;
    private LinearLayout resumoBox;
    private LinearLayout lista;
    private Button hojeBtn, mesBtn;
    private boolean periodoMes = false;
    private long inicioAtual;
    private long fimAtual;

    private AlertDialog despesaDialog;
    private EditText descricaoForm;
    private EditText favorecidoForm;
    private EditText favorecidoDocForm;
    private EditText valorForm;
    private EditText docNumeroForm;
    private EditText docSerieForm;
    private EditText docChaveForm;
    private EditText docEmitenteForm;
    private EditText docEmitenteCnpjForm;
    private Spinner docTipoForm;
    private LinearLayout docDetalhesBox;
    private Button importarXmlBtn;

    private String xmlPendente = "";
    private String xmlUriPendente = "";
    private long xmlEmissaoPendente;
    private double xmlValorPendente;

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

        TextView sub = txt("Vendas, custos, despesas e documentos • Alpha 24", 14, false);
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
        if (periodoMes) c.set(Calendar.DAY_OF_MONTH, 1);
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

            String doc = rotuloDocumento(d.documentoTipo);
            TextView meta = txt(data.format(new Date(d.dataMillis)) + " • " + tipo +
                    (d.categoria == null || d.categoria.isEmpty() ? "" : " • " + d.categoria) +
                    (doc.isEmpty() ? "" : "\nDocumento: " + doc),
                    12, false);
            meta.setTextColor(Color.parseColor("#667085"));
            card.addView(meta);

            if (d.favorecidoNome != null && !d.favorecidoNome.trim().isEmpty()) {
                TextView fav = txt("Favorecido: " + d.favorecidoNome.trim(), 12, false);
                fav.setTextColor(Color.parseColor("#475467"));
                card.addView(fav);
            }

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
        xmlPendente = "";
        xmlUriPendente = "";
        xmlEmissaoPendente = 0;
        xmlValorPendente = 0;

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), dp(12));
        sv.addView(box);

        TextView aviso = txt(
                "Compra para estoque não é descontada novamente do lucro líquido. " +
                "O documento pode ser um recibo ou uma nota recebida.",
                12, true);
        aviso.setTextColor(Color.parseColor("#B54708"));
        aviso.setPadding(0,0,0,dp(8));
        box.addView(aviso);

        descricaoForm = campo("Descrição *", InputType.TYPE_CLASS_TEXT);
        box.addView(descricaoForm);

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

        valorForm = campo("Valor R$ *",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(valorForm);

        favorecidoForm = campo("Favorecido / fornecedor", InputType.TYPE_CLASS_TEXT);
        box.addView(favorecidoForm);

        favorecidoDocForm = campo("CPF/CNPJ do favorecido", InputType.TYPE_CLASS_NUMBER);
        CadastroBrasilUtils.aplicarMascaraDocumento(
                favorecidoDocForm,
                () -> CadastroBrasilUtils.apenasDigitos(
                        favorecidoDocForm.getText().toString()).length() > 11);
        box.addView(favorecidoDocForm);

        TextView docTitulo = txt("DOCUMENTO DA DESPESA", 13, true);
        docTitulo.setTextColor(Color.parseColor("#475467"));
        docTitulo.setPadding(0, dp(14), 0, dp(4));
        box.addView(docTitulo);

        docTipoForm = new Spinner(this);
        String[] docs = {
                "Recibo",
                "NF-e recebida",
                "NFC-e / Cupom",
                "Boleto",
                "Outro",
                "Sem documento"
        };
        ArrayAdapter<String> da = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, docs);
        da.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        docTipoForm.setAdapter(da);
        box.addView(docTipoForm);

        docDetalhesBox = new LinearLayout(this);
        docDetalhesBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(docDetalhesBox);

        importarXmlBtn = action("Importar XML da NF-e");
        importarXmlBtn.setVisibility(View.GONE);
        importarXmlBtn.setOnClickListener(v -> escolherXmlNfe());
        docDetalhesBox.addView(importarXmlBtn);

        docNumeroForm = campo("Número do documento / NF-e", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docNumeroForm);

        docSerieForm = campo("Série (quando houver)", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docSerieForm);

        docChaveForm = campo("Chave de acesso da NF-e (44 dígitos)", InputType.TYPE_CLASS_NUMBER);
        docDetalhesBox.addView(docChaveForm);

        docEmitenteForm = campo("Emitente da nota", InputType.TYPE_CLASS_TEXT);
        docDetalhesBox.addView(docEmitenteForm);

        docEmitenteCnpjForm = campo("CNPJ do emitente", InputType.TYPE_CLASS_NUMBER);
        CadastroBrasilUtils.aplicarMascaraDocumento(docEmitenteCnpjForm, () -> true);
        docDetalhesBox.addView(docEmitenteCnpjForm);

        EditText obs = campo("Observação", InputType.TYPE_CLASS_TEXT);
        box.addView(obs);

        docTipoForm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean sem = pos == 5;
                boolean nfe = pos == 1;
                docDetalhesBox.setVisibility(sem ? View.GONE : View.VISIBLE);
                importarXmlBtn.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docChaveForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docSerieForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docEmitenteForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
                docEmitenteCnpjForm.setVisibility(nfe ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        despesaDialog = new AlertDialog.Builder(this)
                .setTitle("Registrar saída")
                .setView(sv)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        despesaDialog.setOnDismissListener(x -> limparFormularioDocumento());
        despesaDialog.setOnShowListener(x ->
                despesaDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String desc = descricaoForm.getText().toString().trim();
                            if (desc.isEmpty()) {
                                descricaoForm.setError("Informe a descrição");
                                return;
                            }

                            double vl = numero(valorForm.getText().toString());
                            if (Double.isNaN(vl) || vl <= 0) {
                                valorForm.setError("Informe um valor válido");
                                return;
                            }

                            String favDoc = CadastroBrasilUtils.apenasDigitos(
                                    favorecidoDocForm.getText().toString());
                            if (!favDoc.isEmpty()) {
                                boolean ok = favDoc.length() == 11
                                        ? CadastroBrasilUtils.cpfValido(favDoc)
                                        : favDoc.length() == 14 && CadastroBrasilUtils.cnpjValido(favDoc);
                                if (!ok) {
                                    favorecidoDocForm.setError("CPF/CNPJ inválido");
                                    return;
                                }
                            }

                            String docTipo = codigoDocumento(docTipoForm.getSelectedItemPosition());
                            String chave = CadastroBrasilUtils.apenasDigitos(
                                    docChaveForm.getText().toString());
                            if ("NFE_RECEBIDA".equals(docTipo) && !chave.isEmpty() && chave.length() != 44) {
                                docChaveForm.setError("A chave da NF-e deve ter 44 dígitos");
                                return;
                            }

                            String emitCnpj = CadastroBrasilUtils.apenasDigitos(
                                    docEmitenteCnpjForm.getText().toString());
                            if ("NFE_RECEBIDA".equals(docTipo) && !emitCnpj.isEmpty()
                                    && !CadastroBrasilUtils.cnpjValido(emitCnpj)) {
                                docEmitenteCnpjForm.setError("CNPJ do emitente inválido");
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
                            d.favorecidoNome = favorecidoForm.getText().toString().trim();
                            d.favorecidoDocumento = favDoc;
                            d.documentoTipo = docTipo;
                            d.documentoNumero = docNumeroForm.getText().toString().trim();
                            d.documentoSerie = docSerieForm.getText().toString().trim();
                            d.documentoChave = chave;
                            d.documentoEmissaoMillis = xmlEmissaoPendente;
                            d.documentoEmitenteNome = docEmitenteForm.getText().toString().trim();
                            d.documentoEmitenteCnpj = emitCnpj;
                            d.documentoValor = xmlValorPendente > 0 ? xmlValorPendente : vl;
                            d.documentoXml = xmlPendente;
                            d.documentoUri = xmlUriPendente;

                            db.saveDespesa(d);
                            despesaDialog.dismiss();
                            carregar();
                            Toast.makeText(this, "Saída registrada.", Toast.LENGTH_SHORT).show();
                        }));
        despesaDialog.show();
    }

    private void escolherXmlNfe() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/xml", "text/xml", "text/plain"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_XML_NFE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode != REQ_XML_NFE || resultCode != RESULT_OK || intent == null) return;
        Uri uri = intent.getData();
        if (uri == null) return;

        try {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}

            byte[] bytes = lerTudo(uri);
            DocumentoNfe x = lerNfe(bytes);

            if (!formularioDocumentoDisponivel()) {
                Toast.makeText(this,
                        "A tela foi recarregada pelo Android. Abra novamente 'Registrar saída' e importe o XML.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            xmlPendente = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            xmlUriPendente = uri.toString();
            xmlEmissaoPendente = x.emissaoMillis;
            xmlValorPendente = x.valor;

            docTipoForm.setSelection(1);
            docNumeroForm.setText(x.numero);
            docSerieForm.setText(x.serie);
            docChaveForm.setText(x.chave);
            docEmitenteForm.setText(x.emitenteNome);
            docEmitenteCnpjForm.setText(x.emitenteCnpj);

            if (favorecidoForm.getText().toString().trim().isEmpty()) {
                favorecidoForm.setText(x.emitenteNome);
            }
            if (CadastroBrasilUtils.apenasDigitos(
                    favorecidoDocForm.getText().toString()).isEmpty()) {
                favorecidoDocForm.setText(x.emitenteCnpj);
            }
            if (x.valor > 0) {
                valorForm.setText(String.format(Locale.US, "%.2f", x.valor).replace(".", ","));
            }
            if (descricaoForm.getText().toString().trim().isEmpty()) {
                descricaoForm.setText("NF-e " + (x.numero.isEmpty() ? "recebida" : "nº " + x.numero));
            }

            Toast.makeText(this,
                    "XML importado. Confira os dados antes de salvar.",
                    Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this,
                    e.getMessage() == null || e.getMessage().trim().isEmpty()
                            ? "Este arquivo não é uma NF-e válida."
                            : e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Não foi possível ler este XML. Se for uma NF-e real, tente novamente ou envie o arquivo para conferência.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean formularioDocumentoDisponivel() {
        return docTipoForm != null &&
                docNumeroForm != null &&
                docSerieForm != null &&
                docChaveForm != null &&
                docEmitenteForm != null &&
                docEmitenteCnpjForm != null &&
                favorecidoForm != null &&
                favorecidoDocForm != null &&
                valorForm != null &&
                descricaoForm != null;
    }

    private byte[] lerTudo(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Arquivo indisponível.");
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("O XML é muito grande para importação.");
                }
                out.write(buffer, 0, n);
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("O arquivo XML está vazio.");
            }
            return bytes;
        }
    }

    private DocumentoNfe lerNfe(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("O arquivo XML está vazio.");
        }

        String inicio = new String(
                bytes, 0, Math.min(bytes.length, 4096),
                java.nio.charset.StandardCharsets.UTF_8);
        if (inicio.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("XML não aceito por segurança.");
        }

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        try { f.setXIncludeAware(false); } catch (Exception ignored) {}
        try { f.setExpandEntityReferences(false); } catch (Exception ignored) {}

        Document doc;
        try {
            doc = f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("O arquivo não contém um XML válido.");
        }

        if (doc == null || doc.getDocumentElement() == null) {
            throw new IllegalArgumentException("O arquivo não contém uma NF-e.");
        }
        doc.getDocumentElement().normalize();

        Element inf = primeiro(doc, "infNFe");
        Element ide = primeiro(doc, "ide");
        Element emit = primeiro(doc, "emit");
        Element total = primeiro(doc, "ICMSTot");

        if (inf == null || ide == null || emit == null || total == null) {
            throw new IllegalArgumentException("Este XML não possui a estrutura de uma NF-e.");
        }

        String modelo = texto(ide, "mod");
        if ("65".equals(modelo)) {
            throw new IllegalArgumentException("Este XML é de NFC-e (modelo 65). Selecione NFC-e / Cupom.");
        }
        if (!"55".equals(modelo)) {
            throw new IllegalArgumentException("Este XML não é uma NF-e modelo 55.");
        }

        DocumentoNfe x = new DocumentoNfe();

        String id = inf.getAttribute("Id");
        if (id != null && !id.trim().isEmpty()) {
            x.chave = CadastroBrasilUtils.apenasDigitos(id);
        }
        if (x.chave.length() != 44) {
            Element prot = primeiro(doc, "infProt");
            if (prot != null) {
                x.chave = CadastroBrasilUtils.apenasDigitos(texto(prot, "chNFe"));
            }
        }
        if (x.chave.length() != 44) {
            throw new IllegalArgumentException("A NF-e não possui uma chave de acesso válida com 44 dígitos.");
        }

        x.numero = texto(ide, "nNF");
        x.serie = texto(ide, "serie");
        if (x.numero.isEmpty()) {
            throw new IllegalArgumentException("A NF-e não possui número identificável.");
        }

        String dh = texto(ide, "dhEmi");
        if (dh.isEmpty()) dh = texto(ide, "dEmi");
        x.emissaoMillis = parseDataXml(dh);

        x.emitenteCnpj = CadastroBrasilUtils.apenasDigitos(texto(emit, "CNPJ"));
        x.emitenteNome = texto(emit, "xNome");
        if (x.emitenteNome.isEmpty()) x.emitenteNome = texto(emit, "xFant");

        if (x.emitenteCnpj.length() != 14 || !CadastroBrasilUtils.cnpjValido(x.emitenteCnpj)) {
            throw new IllegalArgumentException("O CNPJ do emitente da NF-e é inválido.");
        }
        if (x.emitenteNome.isEmpty()) {
            throw new IllegalArgumentException("A NF-e não informa o nome do emitente.");
        }

        String vNf = texto(total, "vNF");
        try {
            x.valor = Double.parseDouble(vNf.replace(",", "."));
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível identificar o valor total da NF-e.");
        }
        if (x.valor < 0) {
            throw new IllegalArgumentException("O valor total da NF-e é inválido.");
        }

        return x;
    }

    private Element primeiro(Document doc, String localName) {
        NodeList n = doc.getElementsByTagNameNS("*", localName);
        if (n.getLength() == 0) n = doc.getElementsByTagName(localName);
        return n.getLength() > 0 && n.item(0) instanceof Element ? (Element) n.item(0) : null;
    }

    private String texto(Element parent, String localName) {
        NodeList n = parent.getElementsByTagNameNS("*", localName);
        if (n.getLength() == 0) n = parent.getElementsByTagName(localName);
        if (n.getLength() == 0 || n.item(0) == null) return "";
        String s = n.item(0).getTextContent();
        return s == null ? "" : s.trim();
    }

    private long parseDataXml(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String s = raw.trim();
        try {
            if (s.length() >= 19) {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        .parse(s.substring(0, 19)).getTime();
            }
            if (s.length() >= 10) {
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .parse(s.substring(0, 10)).getTime();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static class DocumentoNfe {
        String numero = "";
        String serie = "";
        String chave = "";
        String emitenteNome = "";
        String emitenteCnpj = "";
        long emissaoMillis;
        double valor;
    }

    private void abrirDespesa(GestaoDbHelper.Despesa d) {
        StringBuilder msg = new StringBuilder();
        msg.append(data.format(new Date(d.dataMillis)));
        msg.append("\nCategoria: ")
                .append(d.categoria == null || d.categoria.isEmpty() ? "—" : d.categoria);
        msg.append("\nPagamento: ").append(d.formaPagamento);
        msg.append("\nValor: ").append(moeda.format(d.valor));

        if (d.favorecidoNome != null && !d.favorecidoNome.trim().isEmpty()) {
            msg.append("\nFavorecido: ").append(d.favorecidoNome.trim());
        }
        String favDoc = CadastroBrasilUtils.apenasDigitos(d.favorecidoDocumento);
        if (!favDoc.isEmpty()) {
            msg.append("\nCPF/CNPJ: ")
                    .append(CadastroBrasilUtils.formatarDocumento(favDoc, favDoc.length() > 11));
        }

        String rotulo = rotuloDocumento(d.documentoTipo);
        if (!rotulo.isEmpty()) msg.append("\nDocumento: ").append(rotulo);
        if (d.documentoNumero != null && !d.documentoNumero.trim().isEmpty()) {
            msg.append("\nNúmero: ").append(d.documentoNumero.trim());
        }
        if (d.documentoSerie != null && !d.documentoSerie.trim().isEmpty()) {
            msg.append("  Série: ").append(d.documentoSerie.trim());
        }
        if (d.documentoChave != null && !d.documentoChave.trim().isEmpty()) {
            msg.append("\nChave NF-e: ").append(d.documentoChave.trim());
        }
        if (d.documentoEmissaoMillis > 0) {
            msg.append("\nEmissão do documento: ")
                    .append(new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"))
                            .format(new Date(d.documentoEmissaoMillis)));
        }
        if (d.documentoXml != null && !d.documentoXml.isEmpty()) {
            msg.append("\nXML da NF-e: armazenado no lançamento");
        }
        if (d.observacao != null && !d.observacao.isEmpty()) {
            msg.append("\nObservação: ").append(d.observacao);
        }

        new AlertDialog.Builder(this)
                .setTitle(d.descricao)
                .setMessage(msg.toString())
                .setPositiveButton("Recibo / comprovante", (x,w) -> {
                    Intent i = new Intent(this, ComprovanteDespesaActivity.class);
                    i.putExtra("despesa_id", d.id);
                    startActivity(i);
                })
                .setNeutralButton("Fechar", null)
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

    private String codigoDocumento(int pos) {
        switch (pos) {
            case 0: return "RECIBO";
            case 1: return "NFE_RECEBIDA";
            case 2: return "NFCE_CUPOM";
            case 3: return "BOLETO";
            case 4: return "OUTRO";
            default: return "SEM_DOCUMENTO";
        }
    }

    private String rotuloDocumento(String tipo) {
        if (tipo == null || tipo.trim().isEmpty() || "SEM_DOCUMENTO".equalsIgnoreCase(tipo)) return "";
        if ("RECIBO".equalsIgnoreCase(tipo)) return "Recibo";
        if ("NFE_RECEBIDA".equalsIgnoreCase(tipo)) return "NF-e recebida";
        if ("NFCE_CUPOM".equalsIgnoreCase(tipo)) return "NFC-e / Cupom";
        if ("BOLETO".equalsIgnoreCase(tipo)) return "Boleto";
        return "Outro";
    }

    private void limparFormularioDocumento() {
        despesaDialog = null;
        descricaoForm = null;
        favorecidoForm = null;
        favorecidoDocForm = null;
        valorForm = null;
        docNumeroForm = null;
        docSerieForm = null;
        docChaveForm = null;
        docEmitenteForm = null;
        docEmitenteCnpjForm = null;
        docTipoForm = null;
        docDetalhesBox = null;
        importarXmlBtn = null;
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
