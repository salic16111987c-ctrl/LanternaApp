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

public class ComprovanteVendaActivity extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    private GestaoDbHelper db;
    private GestaoDbHelper.VendaDetalhe venda;
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

        long vendaId = getIntent().getLongExtra("venda_id", -1);
        db = new GestaoDbHelper(this);
        venda = db.getVendaDetalhe(vendaId);

        if (venda == null) {
            Toast.makeText(this, "Venda não encontrada.", Toast.LENGTH_LONG).show();
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

        TextView titulo = txt("Comprovante da venda", 20, true);
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

        adicionarCentralizado(recibo, "COMPROVANTE DE VENDA - NÃO FISCAL", 13, true, MUTED);

        String cnpj = CadastroBrasilUtils.apenasDigitos(empresa.cnpj);
        if (!cnpj.isEmpty()) {
            adicionarCentralizado(recibo,
                    "CNPJ: " + CadastroBrasilUtils.formatarCnpj(cnpj),
                    12, false, MUTED);
        }
        if (empresa.ie != null && !empresa.ie.trim().isEmpty()) {
            adicionarCentralizado(recibo, "IE: " + empresa.ie.trim(), 12, false, MUTED);
        }

        String enderecoEmpresa = enderecoEmpresa();
        if (!enderecoEmpresa.isEmpty()) {
            adicionarCentralizado(recibo, enderecoEmpresa, 12, false, MUTED);
        }
        if (empresa.telefone != null && !empresa.telefone.trim().isEmpty()) {
            adicionarCentralizado(recibo, "Tel.: " + empresa.telefone.trim(), 12, false, MUTED);
        }

        recibo.addView(divider());

        LinearLayout vendaTop = new LinearLayout(this);
        vendaTop.setOrientation(LinearLayout.HORIZONTAL);
        TextView numero = txt("Venda #" + venda.id, 16, true);
        vendaTop.addView(numero, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView dt = txt(data.format(new Date(venda.dataMillis)), 13, false);
        dt.setTextColor(MUTED);
        vendaTop.addView(dt);
        recibo.addView(vendaTop);

        if ("ESTORNADA".equalsIgnoreCase(venda.statusVenda)) {
            TextView est = txt("VENDA ESTORNADA", 15, true);
            est.setTextColor(RED);
            est.setGravity(Gravity.CENTER);
            est.setPadding(dp(8), dp(10), dp(8), dp(6));
            recibo.addView(est);
            if (venda.estornoEm > 0) {
                adicionarCentralizado(recibo,
                        "Estornada em: " + data.format(new Date(venda.estornoEm)),
                        12, false, RED);
            }
            if (venda.estornoMotivo != null && !venda.estornoMotivo.trim().isEmpty()) {
                adicionarCentralizado(recibo,
                        "Motivo: " + venda.estornoMotivo.trim(),
                        12, false, RED);
            }
        }

        recibo.addView(divider());

        TextView itensTitulo = txt("ITENS", 13, true);
        itensTitulo.setTextColor(MUTED);
        recibo.addView(itensTitulo);

        for (GestaoDbHelper.VendaItemRegistro item : venda.itens) {
            TextView nome = txt(item.nome, 15, true);
            nome.setPadding(0, dp(8), 0, 0);
            recibo.addView(nome);

            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);

            String unidade = item.unidade == null || item.unidade.trim().isEmpty()
                    ? "UN" : item.unidade.trim();
            TextView qtd = txt(formatarQtd(item.quantidade) + " " + unidade +
                    " x " + moeda.format(item.precoUnitario), 13, false);
            qtd.setTextColor(MUTED);
            linha.addView(qtd, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView totalItem = txt(moeda.format(item.total), 14, true);
            linha.addView(totalItem);
            recibo.addView(linha);
        }

        recibo.addView(divider());
        adicionarTotal(recibo, "Subtotal", venda.subtotal, false);
        if (venda.desconto > 0.001) {
            adicionarTotal(recibo, "Desconto", -venda.desconto, false);
        }
        adicionarTotal(recibo, "TOTAL", venda.total, true);

        recibo.addView(divider());

        TextView pgTitulo = txt("PAGAMENTO", 13, true);
        pgTitulo.setTextColor(MUTED);
        recibo.addView(pgTitulo);
        adicionarLinha(recibo, "Forma", venda.formaPagamento, false);
        if (venda.dinheiro > 0.001) adicionarLinha(recibo, "Dinheiro", moeda.format(venda.dinheiro), false);
        if (venda.pix > 0.001) adicionarLinha(recibo, "PIX", moeda.format(venda.pix), false);
        if (venda.cartao > 0.001) adicionarLinha(recibo, "Cartão", moeda.format(venda.cartao), false);
        if (venda.recebido > venda.total + 0.001) {
            adicionarLinha(recibo, "Recebido", moeda.format(venda.recebido), false);
        }
        if (venda.troco > 0.001) adicionarLinha(recibo, "Troco", moeda.format(venda.troco), true);

        String clienteNome = venda.destNome == null ? "" : venda.destNome.trim();
        String clienteDoc = venda.destDocumento == null ? "" :
                CadastroBrasilUtils.apenasDigitos(venda.destDocumento);
        if (clienteDoc.isEmpty() && venda.consumidorDocumento != null) {
            clienteDoc = CadastroBrasilUtils.apenasDigitos(venda.consumidorDocumento);
        }
        if (!clienteNome.isEmpty() || !clienteDoc.isEmpty()) {
            recibo.addView(divider());
            TextView cliTitulo = txt("CLIENTE / CONSUMIDOR", 13, true);
            cliTitulo.setTextColor(MUTED);
            recibo.addView(cliTitulo);
            if (!clienteNome.isEmpty()) adicionarLinha(recibo, "Nome", clienteNome, false);
            if (!clienteDoc.isEmpty()) {
                adicionarLinha(recibo, "Documento",
                        CadastroBrasilUtils.formatarDocumento(clienteDoc, clienteDoc.length() > 11),
                        false);
            }
        }

        if (venda.notaStatus != null && !"NAO_EMITIDA".equalsIgnoreCase(venda.notaStatus)) {
            recibo.addView(divider());
            String tipo = venda.notaTipo == null || venda.notaTipo.trim().isEmpty()
                    ? "Documento fiscal" : venda.notaTipo.trim();
            adicionarLinha(recibo, tipo,
                    venda.notaStatus.replace('_', ' '), true);
            if (venda.notaNumero != null && !venda.notaNumero.trim().isEmpty()) {
                adicionarLinha(recibo, "Número", venda.notaNumero.trim(), false);
            }
        }

        recibo.addView(divider());
        adicionarCentralizado(recibo, "Obrigado pela preferência!", 14, true, NAVY);
        adicionarCentralizado(recibo,
                "Este comprovante não substitui documento fiscal.",
                11, false, MUTED);

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

    private void adicionarTotal(LinearLayout root, String rotulo, double valor, boolean destaque) {
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(0, dp(destaque ? 6 : 3), 0, dp(destaque ? 6 : 3));

        TextView l = txt(rotulo, destaque ? 18 : 14, destaque);
        linha.addView(l, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView v = txt(moeda.format(valor), destaque ? 21 : 15, true);
        if (destaque) v.setTextColor(GREEN);
        linha.addView(v);
        root.addView(linha);
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

    private String formatarQtd(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.3f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private String montarTexto() {
        StringBuilder s = new StringBuilder();
        s.append(nomeEmpresa()).append("\n");
        String cnpj = CadastroBrasilUtils.apenasDigitos(empresa.cnpj);
        if (!cnpj.isEmpty()) s.append("CNPJ: ").append(CadastroBrasilUtils.formatarCnpj(cnpj)).append("\n");
        if (empresa.ie != null && !empresa.ie.trim().isEmpty()) s.append("IE: ").append(empresa.ie.trim()).append("\n");
        if (!enderecoEmpresa().isEmpty()) s.append(enderecoEmpresa()).append("\n");
        s.append("\nCOMPROVANTE DE VENDA - NÃO FISCAL\n");
        s.append("--------------------------------\n");
        s.append("Venda #").append(venda.id).append("\n");
        s.append("Data: ").append(data.format(new Date(venda.dataMillis))).append("\n");

        if ("ESTORNADA".equalsIgnoreCase(venda.statusVenda)) {
            s.append("*** VENDA ESTORNADA ***\n");
            if (venda.estornoEm > 0) {
                s.append("Estornada em: ").append(data.format(new Date(venda.estornoEm))).append("\n");
            }
            if (venda.estornoMotivo != null && !venda.estornoMotivo.trim().isEmpty()) {
                s.append("Motivo: ").append(venda.estornoMotivo.trim()).append("\n");
            }
        }

        s.append("--------------------------------\n");
        for (GestaoDbHelper.VendaItemRegistro item : venda.itens) {
            String unidade = item.unidade == null || item.unidade.trim().isEmpty()
                    ? "UN" : item.unidade.trim();
            s.append(item.nome).append("\n");
            s.append(formatarQtd(item.quantidade)).append(" ").append(unidade)
                    .append(" x ").append(moeda.format(item.precoUnitario))
                    .append(" = ").append(moeda.format(item.total)).append("\n");
        }

        s.append("--------------------------------\n");
        s.append("Subtotal: ").append(moeda.format(venda.subtotal)).append("\n");
        if (venda.desconto > 0.001) s.append("Desconto: - ").append(moeda.format(venda.desconto)).append("\n");
        s.append("TOTAL: ").append(moeda.format(venda.total)).append("\n");
        s.append("Pagamento: ").append(venda.formaPagamento).append("\n");
        if (venda.dinheiro > 0.001) s.append("Dinheiro: ").append(moeda.format(venda.dinheiro)).append("\n");
        if (venda.pix > 0.001) s.append("PIX: ").append(moeda.format(venda.pix)).append("\n");
        if (venda.cartao > 0.001) s.append("Cartão: ").append(moeda.format(venda.cartao)).append("\n");
        if (venda.troco > 0.001) s.append("Troco: ").append(moeda.format(venda.troco)).append("\n");

        String clienteNome = venda.destNome == null ? "" : venda.destNome.trim();
        String clienteDoc = venda.destDocumento == null ? "" :
                CadastroBrasilUtils.apenasDigitos(venda.destDocumento);
        if (clienteDoc.isEmpty() && venda.consumidorDocumento != null) {
            clienteDoc = CadastroBrasilUtils.apenasDigitos(venda.consumidorDocumento);
        }
        if (!clienteNome.isEmpty() || !clienteDoc.isEmpty()) {
            s.append("--------------------------------\n");
            if (!clienteNome.isEmpty()) s.append("Cliente: ").append(clienteNome).append("\n");
            if (!clienteDoc.isEmpty()) {
                s.append("Documento: ")
                        .append(CadastroBrasilUtils.formatarDocumento(clienteDoc, clienteDoc.length() > 11))
                        .append("\n");
            }
        }

        s.append("--------------------------------\n");
        s.append("Obrigado pela preferência!\n");
        s.append("Este comprovante não substitui documento fiscal.\n");
        return s.toString();
    }

    private void compartilharTexto() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Comprovante Tech Cell - Venda #" + venda.id);
        share.putExtra(Intent.EXTRA_TEXT, montarTexto());
        startActivity(Intent.createChooser(share, "Compartilhar comprovante"));
    }

    private void imprimirOuSalvarPdf() {
        final WebView web = new WebView(this);
        web.getSettings().setDefaultTextEncodingName("utf-8");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm == null) {
                    Toast.makeText(ComprovanteVendaActivity.this,
                            "Serviço de impressão indisponível.", Toast.LENGTH_LONG).show();
                    return;
                }
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(
                        "Comprovante-venda-" + venda.id);
                pm.print("Comprovante Tech Cell - Venda #" + venda.id,
                        adapter, new PrintAttributes.Builder().build());
            }
        });
        web.loadDataWithBaseURL(null, montarHtml(), "text/html", "UTF-8", null);
    }

    private String montarHtml() {
        String texto = html(montarTexto()).replace("\n", "<br>");
        return "<!doctype html><html><head><meta charset='utf-8'>" +
                "<style>@page{margin:10mm}body{font-family:monospace;font-size:12px;color:#111;" +
                "max-width:78mm;margin:0 auto;line-height:1.45}h1{font-family:sans-serif;" +
                "font-size:20px;text-align:center;margin:0 0 6px}.box{border:1px solid #bbb;" +
                "padding:14px;white-space:normal}.nf{text-align:center;font-weight:bold;" +
                "margin-bottom:12px}</style></head><body><h1>" + html(nomeEmpresa()) +
                "</h1><div class='nf'>COMPROVANTE DE VENDA - NÃO FISCAL</div>" +
                "<div class='box'>" + texto + "</div></body></html>";
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
