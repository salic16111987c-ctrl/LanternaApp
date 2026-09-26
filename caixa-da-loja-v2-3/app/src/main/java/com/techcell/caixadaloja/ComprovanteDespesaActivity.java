package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ComprovanteDespesaActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt","BR"));

    private GestaoDbHelper db;
    private GestaoDbHelper.Despesa despesa;
    private GestaoDbHelper.EmpresaConfig empresa;

    private final int NAVY = Color.parseColor("#0B1F3A");
    private final int GREEN = Color.parseColor("#07884B");
    private final int RED = Color.parseColor("#B42318");
    private final int TEXT = Color.parseColor("#182230");
    private final int MUTED = Color.parseColor("#667085");
    private final int BG = Color.parseColor("#F4F6FA");
    private final int BORDER = Color.parseColor("#DCE2EA");

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView txt(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(TEXT);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        p.setMargins(0, dp(10), 0, dp(10));
        v.setLayoutParams(p);
        return v;
    }

    private Button botao(String texto, int cor) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackgroundColor(cor);
        b.setMinHeight(dp(52));
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long id = getIntent().getLongExtra("despesa_id", -1);
        db = new GestaoDbHelper(this);
        despesa = db.getDespesa(id);

        if (despesa == null) {
            Toast.makeText(this, "Lançamento não encontrado.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        empresa = db.getEmpresaConfig();
        montarTela();
    }

    private void montarTela() {
        getWindow().setStatusBarColor(NAVY);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackgroundColor(NAVY);

        Button voltar = new Button(this);
        voltar.setText("←");
        voltar.setTextSize(22);
        voltar.setTextColor(Color.WHITE);
        voltar.setBackgroundColor(Color.TRANSPARENT);
        voltar.setOnClickListener(v -> finish());
        top.addView(voltar, new LinearLayout.LayoutParams(dp(58), dp(48)));

        TextView titulo = txt("Recibo / comprovante", 20, true);
        titulo.setTextColor(Color.WHITE);
        titulo.setPadding(dp(8), 0, 0, 0);
        top.addView(titulo, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        screen.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        LinearLayout recibo = new LinearLayout(this);
        recibo.setOrientation(LinearLayout.VERTICAL);
        recibo.setPadding(dp(18), dp(20), dp(18), dp(20));
        recibo.setBackgroundColor(Color.WHITE);
        root.addView(recibo);

        TextView empresaNome = txt(nomeEmpresa(), 23, true);
        empresaNome.setGravity(Gravity.CENTER);
        empresaNome.setTextColor(NAVY);
        recibo.addView(empresaNome);

        adicionarCentralizado(recibo, "RECIBO DE PAGAMENTO / COMPROVANTE DE SAÍDA", 13, true, MUTED);
        adicionarCentralizado(recibo, "NÃO FISCAL", 12, true, MUTED);

        String cnpj = CadastroBrasilUtils.apenasDigitos(empresa.cnpj);
        if (!cnpj.isEmpty()) {
            adicionarCentralizado(recibo,
                    "CNPJ: " + CadastroBrasilUtils.formatarCnpj(cnpj),
                    12, false, MUTED);
        }
        if (!enderecoEmpresa().isEmpty()) {
            adicionarCentralizado(recibo, enderecoEmpresa(), 12, false, MUTED);
        }

        recibo.addView(divider());

        adicionarLinha(recibo, "Lançamento", "#" + despesa.id, true);
        adicionarLinha(recibo, "Data", data.format(new Date(despesa.dataMillis)), false);

        if ("CANCELADA".equalsIgnoreCase(despesa.status)) {
            TextView cancelada = txt("LANÇAMENTO CANCELADO", 15, true);
            cancelada.setTextColor(RED);
            cancelada.setGravity(Gravity.CENTER);
            cancelada.setPadding(0, dp(8), 0, dp(8));
            recibo.addView(cancelada);
            if (despesa.canceladaEm > 0) {
                adicionarLinha(recibo, "Cancelado em",
                        data.format(new Date(despesa.canceladaEm)), false);
            }
            if (despesa.cancelamentoMotivo != null && !despesa.cancelamentoMotivo.trim().isEmpty()) {
                adicionarLinha(recibo, "Motivo", despesa.cancelamentoMotivo.trim(), false);
            }
        }

        recibo.addView(divider());

        String fav = despesa.favorecidoNome == null ? "" : despesa.favorecidoNome.trim();
        String favDoc = CadastroBrasilUtils.apenasDigitos(despesa.favorecidoDocumento);
        if (!fav.isEmpty() || !favDoc.isEmpty()) {
            TextView ft = txt("FAVORECIDO", 13, true);
            ft.setTextColor(MUTED);
            recibo.addView(ft);
            if (!fav.isEmpty()) adicionarLinha(recibo, "Nome", fav, true);
            if (!favDoc.isEmpty()) {
                adicionarLinha(recibo, "CPF/CNPJ",
                        CadastroBrasilUtils.formatarDocumento(favDoc, favDoc.length() > 11),
                        false);
            }
            recibo.addView(divider());
        }

        TextView dados = txt("DADOS DO PAGAMENTO", 13, true);
        dados.setTextColor(MUTED);
        recibo.addView(dados);

        adicionarLinha(recibo, "Descrição", despesa.descricao, true);
        if (despesa.categoria != null && !despesa.categoria.trim().isEmpty()) {
            adicionarLinha(recibo, "Categoria", despesa.categoria.trim(), false);
        }
        adicionarLinha(recibo, "Forma de pagamento", despesa.formaPagamento, false);

        LinearLayout total = new LinearLayout(this);
        total.setOrientation(LinearLayout.HORIZONTAL);
        total.setPadding(0, dp(12), 0, dp(8));
        TextView tl = txt("VALOR PAGO", 18, true);
        total.addView(tl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView tv = txt(moeda.format(despesa.valor), 22, true);
        tv.setTextColor(GREEN);
        total.addView(tv);
        recibo.addView(total);

        if (despesa.observacao != null && !despesa.observacao.trim().isEmpty()) {
            adicionarLinha(recibo, "Observação", despesa.observacao.trim(), false);
        }

        String docTipo = rotuloDocumento(despesa.documentoTipo);
        if (!docTipo.isEmpty()) {
            recibo.addView(divider());
            TextView dt = txt("DOCUMENTO VINCULADO", 13, true);
            dt.setTextColor(MUTED);
            recibo.addView(dt);

            adicionarLinha(recibo, "Tipo", docTipo, true);
            if (despesa.documentoNumero != null && !despesa.documentoNumero.trim().isEmpty()) {
                adicionarLinha(recibo, "Número", despesa.documentoNumero.trim(), false);
            }
            if (despesa.documentoSerie != null && !despesa.documentoSerie.trim().isEmpty()) {
                adicionarLinha(recibo, "Série", despesa.documentoSerie.trim(), false);
            }
            if (despesa.documentoEmitenteNome != null &&
                    !despesa.documentoEmitenteNome.trim().isEmpty()) {
                adicionarLinha(recibo, "Emitente", despesa.documentoEmitenteNome.trim(), false);
            }
            String emitCnpj = CadastroBrasilUtils.apenasDigitos(despesa.documentoEmitenteCnpj);
            if (!emitCnpj.isEmpty()) {
                adicionarLinha(recibo, "CNPJ emitente",
                        CadastroBrasilUtils.formatarCnpj(emitCnpj), false);
            }
            if (despesa.documentoEmissaoMillis > 0) {
                adicionarLinha(recibo, "Emissão",
                        data.format(new Date(despesa.documentoEmissaoMillis)), false);
            }
            if (despesa.documentoChave != null && !despesa.documentoChave.trim().isEmpty()) {
                TextView chave = txt("Chave NF-e:\n" + despesa.documentoChave.trim(), 11, false);
                chave.setTextColor(MUTED);
                chave.setPadding(0, dp(5), 0, dp(2));
                recibo.addView(chave);
            }
        }

        recibo.addView(divider());

        TextView declaracao = txt(
                "Declaro o recebimento do valor acima referente ao pagamento descrito neste recibo.",
                13, false);
        declaracao.setGravity(Gravity.CENTER);
        declaracao.setPadding(dp(4), dp(5), dp(4), dp(26));
        recibo.addView(declaracao);

        TextView assinatura = txt(
                "____________________________________\nAssinatura do favorecido",
                13, false);
        assinatura.setGravity(Gravity.CENTER);
        assinatura.setPadding(0, dp(12), 0, dp(18));
        recibo.addView(assinatura);

        adicionarCentralizado(recibo,
                "Este recibo/comprovante não substitui documento fiscal.",
                11, false, MUTED);

        if ("NFE_RECEBIDA".equalsIgnoreCase(despesa.documentoTipo)) {
            adicionarCentralizado(recibo,
                    "Há uma NF-e recebida vinculada a este lançamento.",
                    11, true, NAVY);
        }

        LinearLayout acoes = new LinearLayout(this);
        acoes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        ap.setMargins(0, dp(14), 0, 0);
        acoes.setLayoutParams(ap);

        Button compartilhar = botao("Compartilhar", GREEN);
        compartilhar.setOnClickListener(v -> compartilharTexto());
        acoes.addView(compartilhar, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Button imprimir = botao("Imprimir / Salvar PDF", NAVY);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        ip.setMargins(dp(8), 0, 0, 0);
        imprimir.setLayoutParams(ip);
        imprimir.setOnClickListener(v -> imprimirOuSalvarPdf());
        acoes.addView(imprimir);

        root.addView(acoes);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(screen);
    }

    private void adicionarCentralizado(LinearLayout root, String texto, int tamanho,
                                       boolean bold, int cor) {
        TextView t = txt(texto, tamanho, bold);
        t.setTextColor(cor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(2), 0, dp(2));
        root.addView(t);
    }

    private void adicionarLinha(LinearLayout root, String rotulo, String valor, boolean bold) {
        if (valor == null || valor.trim().isEmpty()) return;
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setPadding(0, dp(3), 0, dp(3));

        TextView l = txt(rotulo, 13, false);
        l.setTextColor(MUTED);
        linha.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));

        TextView v = txt(valor, 13, bold);
        v.setGravity(Gravity.END);
        linha.addView(v, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        root.addView(linha);
    }

    private String nomeEmpresa() {
        if (empresa.fantasia != null && !empresa.fantasia.trim().isEmpty()) return empresa.fantasia.trim();
        if (empresa.razao != null && !empresa.razao.trim().isEmpty()) return empresa.razao.trim();
        return "Tech Cell";
    }

    private String enderecoEmpresa() {
        StringBuilder s = new StringBuilder();
        if (empresa.logradouro != null && !empresa.logradouro.trim().isEmpty()) {
            s.append(empresa.logradouro.trim());
            if (empresa.numero != null && !empresa.numero.trim().isEmpty()) {
                s.append(", ").append(empresa.numero.trim());
            }
        }
        if (empresa.bairro != null && !empresa.bairro.trim().isEmpty()) {
            if (s.length() > 0) s.append(" - ");
            s.append(empresa.bairro.trim());
        }
        if (empresa.municipio != null && !empresa.municipio.trim().isEmpty()) {
            if (s.length() > 0) s.append(" • ");
            s.append(empresa.municipio.trim());
            if (empresa.uf != null && !empresa.uf.trim().isEmpty()) {
                s.append("/").append(empresa.uf.trim());
            }
        }
        return s.toString();
    }

    private String rotuloDocumento(String tipo) {
        if (tipo == null || tipo.trim().isEmpty() || "SEM_DOCUMENTO".equalsIgnoreCase(tipo)) return "";
        if ("RECIBO".equalsIgnoreCase(tipo)) return "Recibo";
        if ("NFE_RECEBIDA".equalsIgnoreCase(tipo)) return "NF-e recebida";
        if ("NFCE_CUPOM".equalsIgnoreCase(tipo)) return "NFC-e / Cupom";
        if ("BOLETO".equalsIgnoreCase(tipo)) return "Boleto";
        return "Outro";
    }

    private String montarTexto() {
        StringBuilder s = new StringBuilder();
        s.append(nomeEmpresa()).append("\n");
        String cnpj = CadastroBrasilUtils.apenasDigitos(empresa.cnpj);
        if (!cnpj.isEmpty()) {
            s.append("CNPJ: ").append(CadastroBrasilUtils.formatarCnpj(cnpj)).append("\n");
        }
        if (!enderecoEmpresa().isEmpty()) s.append(enderecoEmpresa()).append("\n");

        s.append("\nRECIBO DE PAGAMENTO / COMPROVANTE DE SAÍDA\n");
        s.append("NÃO FISCAL\n");
        s.append("--------------------------------\n");
        s.append("Lançamento #").append(despesa.id).append("\n");
        s.append("Data: ").append(data.format(new Date(despesa.dataMillis))).append("\n");

        if ("CANCELADA".equalsIgnoreCase(despesa.status)) {
            s.append("*** LANÇAMENTO CANCELADO ***\n");
            if (despesa.canceladaEm > 0) {
                s.append("Cancelado em: ")
                        .append(data.format(new Date(despesa.canceladaEm))).append("\n");
            }
            if (despesa.cancelamentoMotivo != null && !despesa.cancelamentoMotivo.trim().isEmpty()) {
                s.append("Motivo: ").append(despesa.cancelamentoMotivo.trim()).append("\n");
            }
        }

        s.append("--------------------------------\n");
        if (despesa.favorecidoNome != null && !despesa.favorecidoNome.trim().isEmpty()) {
            s.append("Favorecido: ").append(despesa.favorecidoNome.trim()).append("\n");
        }
        String favDoc = CadastroBrasilUtils.apenasDigitos(despesa.favorecidoDocumento);
        if (!favDoc.isEmpty()) {
            s.append("CPF/CNPJ: ")
                    .append(CadastroBrasilUtils.formatarDocumento(favDoc, favDoc.length() > 11))
                    .append("\n");
        }
        s.append("Descrição: ").append(despesa.descricao).append("\n");
        if (despesa.categoria != null && !despesa.categoria.trim().isEmpty()) {
            s.append("Categoria: ").append(despesa.categoria.trim()).append("\n");
        }
        s.append("Pagamento: ").append(despesa.formaPagamento).append("\n");
        s.append("VALOR PAGO: ").append(moeda.format(despesa.valor)).append("\n");

        if (despesa.observacao != null && !despesa.observacao.trim().isEmpty()) {
            s.append("Observação: ").append(despesa.observacao.trim()).append("\n");
        }

        String doc = rotuloDocumento(despesa.documentoTipo);
        if (!doc.isEmpty()) {
            s.append("--------------------------------\n");
            s.append("Documento vinculado: ").append(doc).append("\n");
            if (despesa.documentoNumero != null && !despesa.documentoNumero.trim().isEmpty()) {
                s.append("Número: ").append(despesa.documentoNumero.trim()).append("\n");
            }
            if (despesa.documentoSerie != null && !despesa.documentoSerie.trim().isEmpty()) {
                s.append("Série: ").append(despesa.documentoSerie.trim()).append("\n");
            }
            if (despesa.documentoChave != null && !despesa.documentoChave.trim().isEmpty()) {
                s.append("Chave NF-e: ").append(despesa.documentoChave.trim()).append("\n");
            }
        }

        s.append("--------------------------------\n");
        s.append("Declaro o recebimento do valor acima.\n\n");
        s.append("____________________________________\n");
        s.append("Assinatura do favorecido\n\n");
        s.append("Este recibo/comprovante não substitui documento fiscal.\n");
        return s.toString();
    }

    private void compartilharTexto() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT,
                "Recibo Tech Cell - Lançamento #" + despesa.id);
        share.putExtra(Intent.EXTRA_TEXT, montarTexto());
        startActivity(Intent.createChooser(share, "Compartilhar recibo"));
    }

    private void imprimirOuSalvarPdf() {
        final WebView web = new WebView(this);
        web.getSettings().setDefaultTextEncodingName("utf-8");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm == null) {
                    Toast.makeText(ComprovanteDespesaActivity.this,
                            "Serviço de impressão indisponível.", Toast.LENGTH_LONG).show();
                    return;
                }
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(
                        "Recibo-despesa-" + despesa.id);
                pm.print("Recibo Tech Cell - Lançamento #" + despesa.id,
                        adapter, new PrintAttributes.Builder().build());
            }
        });
        web.loadDataWithBaseURL(null, montarHtml(), "text/html", "UTF-8", null);
    }

    private String montarHtml() {
        String texto = html(montarTexto()).replace("\n", "<br>");
        return "<!doctype html><html><head><meta charset='utf-8'>" +
                "<style>@page{margin:10mm}body{font-family:monospace;font-size:12px;color:#111;" +
                "max-width:90mm;margin:0 auto;line-height:1.45}.box{border:1px solid #bbb;" +
                "padding:14px;white-space:normal}</style></head><body><div class='box'>" +
                texto + "</div></body></html>";
    }

    private String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
