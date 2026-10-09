package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Caixa nativo multiempresa.
 *
 * Motivo desta versao: o Caixa Web antigo dependia de baixar o SDK web do Firebase
 * pelo WebView e alguns aparelhos ficaram presos em "Conectando ao Firebase...".
 * Esta Activity usa diretamente o Firebase Android que ja faz parte do Tech Cell.
 *
 * Dados:
 * techcell_empresas/{empresa_uuid}/caixa_movimentos/{yyyy-MM-dd}
 * techcell_empresas/{empresa_uuid}/caixa_fechamentos/{yyyy-MM}
 */
public class MainActivityV27 extends Activity {
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final SimpleDateFormat isoDia = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat brDia = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));

    private TechCellAccess.Sessao sessao;
    private FirebaseFirestore firestore;
    private DocumentReference empresaRef;
    private ListenerRegistration movimentosListener;
    private ListenerRegistration fechamentosListener;

    private final List<Movimento> movimentos = new ArrayList<>();
    private final List<Fechamento> fechamentos = new ArrayList<>();

    private boolean master;
    private boolean carregouMovimentos;
    private boolean carregouFechamentos;
    private String aba = "lancamentos";
    private String periodo = "hoje";
    private String dataMaster;
    private String mesFechamento;

    private int dp(int v) { return TechCellUi.dp(this, v); }

    private static final class Movimento {
        String id = "";
        String data = "";
        double dinheiro;
        double cartao;
        double total;
        String atualizadoPor = "";
    }

    private static final class Fechamento {
        String id = "";
        String mes = "";
        double lucro;
        double despesasTotal;
        double resultadoLiquido;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TechCellUi.applyWindowChrome(this);

        sessao = TechCellAccess.sessao(this);
        if (sessao == null || !sessao.valida || sessao.empresaUuid == null || sessao.empresaUuid.trim().isEmpty()) {
            Toast.makeText(this, "Este aparelho ainda nao possui uma empresa valida.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FirebaseUser user = null;
        try { user = TechCellCloudSync.auth(this).getCurrentUser(); } catch (Throwable ignored) {}
        if (user == null) {
            Toast.makeText(this, "A sessao do Firebase expirou. Entre novamente no Tech Cell.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        master = sessao.perfil == TechCellAccess.Perfil.MASTER;
        dataMaster = hoje();
        mesFechamento = dataMaster.substring(0, 7);

        firestore = TechCellCloudSync.firestore(this);
        empresaRef = firestore.collection("techcell_empresas").document(sessao.empresaUuid.trim());

        // Copia o historico antigo para a empresa atual sem apagar os documentos antigos.
        TechCellCaixaMigration.iniciar(this);

        renderCarregando();
        observarMovimentos();
        if (master) observarFechamentos();
        else carregouFechamentos = true;
    }

    private void observarMovimentos() {
        movimentosListener = empresaRef.collection("caixa_movimentos").addSnapshotListener((snap, erro) -> {
            if (erro != null) {
                carregouMovimentos = true;
                renderErro("Sem acesso aos lancamentos desta empresa.", erro);
                return;
            }
            movimentos.clear();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Movimento m = new Movimento();
                    m.id = d.getId();
                    m.data = texto(d.get("data"));
                    if (m.data.isEmpty()) m.data = d.getId();
                    m.dinheiro = numero(d.get("dinheiro"));
                    m.cartao = numero(d.get("cartao"));
                    m.total = d.get("total") == null ? m.dinheiro + m.cartao : numero(d.get("total"));
                    m.atualizadoPor = texto(d.get("atualizadoPor"));
                    if (m.atualizadoPor.isEmpty()) m.atualizadoPor = texto(d.get("criadoPor"));
                    movimentos.add(m);
                }
            }
            Collections.sort(movimentos, (a, b) -> b.data.compareTo(a.data));
            carregouMovimentos = true;
            if (carregouFechamentos) render();
        });
    }

    private void observarFechamentos() {
        fechamentosListener = empresaRef.collection("caixa_fechamentos").addSnapshotListener((snap, erro) -> {
            if (erro != null) {
                carregouFechamentos = true;
                if (carregouMovimentos) render();
                return;
            }
            fechamentos.clear();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Fechamento f = new Fechamento();
                    f.id = d.getId();
                    f.mes = texto(d.get("mes"));
                    if (f.mes.isEmpty()) f.mes = d.getId();
                    f.lucro = numero(d.get("lucro"));
                    f.despesasTotal = numero(d.get("despesasTotal"));
                    f.resultadoLiquido = d.get("resultadoLiquido") == null
                            ? f.lucro - f.despesasTotal
                            : numero(d.get("resultadoLiquido"));
                    fechamentos.add(f);
                }
            }
            Collections.sort(fechamentos, (a, b) -> b.mes.compareTo(a.mes));
            carregouFechamentos = true;
            if (carregouMovimentos) render();
        });
    }

    private void renderCarregando() {
        LinearLayout root = baseRoot();
        TextView t = textoGrande("Caixa da Loja");
        t.setGravity(Gravity.CENTER);
        root.addView(t, TechCellUi.fullCardParams(this, 120));
        TextView s = texto("Carregando dados da empresa...", 15, false);
        s.setTextColor(TechCellUi.MUTED);
        s.setGravity(Gravity.CENTER);
        root.addView(s, TechCellUi.fullCardParams(this, 10));
        setContentView(envolver(root));
    }

    private void renderErro(String mensagem, Throwable erro) {
        LinearLayout root = baseRoot();
        TextView t = textoGrande("Caixa da Loja");
        t.setGravity(Gravity.CENTER);
        root.addView(t, TechCellUi.fullCardParams(this, 70));
        TextView e = texto(mensagem, 16, true);
        e.setTextColor(TechCellUi.RED);
        e.setGravity(Gravity.CENTER);
        root.addView(e, TechCellUi.fullCardParams(this, 14));
        TextView d = texto(TechCellCloudSync.mensagemCloud(erro), 12, false);
        d.setTextColor(TechCellUi.MUTED);
        d.setGravity(Gravity.CENTER);
        root.addView(d, TechCellUi.fullCardParams(this, 8));
        Button voltar = botaoSecundario("Voltar");
        voltar.setOnClickListener(v -> finish());
        root.addView(voltar, botaoParams(20));
        setContentView(envolver(root));
    }

    private void render() {
        LinearLayout root = baseRoot();
        cabecalho(root);

        if (TechCellDeveloperTestMode.ativo(this)) {
            TextView aviso = texto("MODO DE TESTE DO DESENVOLVEDOR - gravacoes bloqueadas", 12, true);
            aviso.setTextColor(TechCellUi.ORANGE);
            aviso.setGravity(Gravity.CENTER);
            aviso.setBackground(TechCellUi.solid(this, Color.parseColor("#FFF4E8"), 12));
            aviso.setPadding(dp(10), dp(10), dp(10), dp(10));
            root.addView(aviso, TechCellUi.fullCardParams(this, 8));
        }

        if (!master) {
            renderCaixa(root);
        } else {
            navegacaoMaster(root);
            if ("lancamentos".equals(aba)) renderLancamentosMaster(root);
            else if ("resumo".equals(aba)) renderResumo(root);
            else if ("fechamento".equals(aba)) renderFechamento(root);
            else renderExportar(root);
        }
        setContentView(envolver(root));
    }

    private void cabecalho(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button voltar = new Button(this);
        voltar.setText("←");
        TechCellUi.styleSecondary(this, voltar);
        voltar.setOnClickListener(v -> finish());
        row.addView(voltar, new LinearLayout.LayoutParams(dp(52), dp(46)));

        LinearLayout centro = new LinearLayout(this);
        centro.setOrientation(LinearLayout.VERTICAL);
        centro.setGravity(Gravity.CENTER);
        TextView titulo = texto("Caixa da Loja", 25, true);
        titulo.setGravity(Gravity.CENTER);
        centro.addView(titulo);
        TextView sub = texto((master ? "MASTER" : "CAIXA") + " • empresa isolada", 11, true);
        sub.setTextColor(TechCellUi.GREEN);
        sub.setGravity(Gravity.CENTER);
        centro.addView(sub);
        row.addView(centro, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView logo = texto("R\n$", 20, true);
        logo.setGravity(Gravity.CENTER);
        logo.setTextColor(Color.WHITE);
        logo.setBackground(TechCellUi.solid(this, TechCellUi.NAVY, 14));
        row.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(row);

        TextView info = texto(sessao.email == null ? "" : sessao.email, 11, false);
        info.setTextColor(TechCellUi.MUTED);
        info.setGravity(Gravity.CENTER);
        root.addView(info, TechCellUi.fullCardParams(this, 4));
    }

    private void navegacaoMaster(LinearLayout root) {
        LinearLayout l1 = new LinearLayout(this);
        l1.setOrientation(LinearLayout.HORIZONTAL);
        l1.setWeightSum(2);
        Button a = nav("Lancamentos", "lancamentos");
        Button b = nav("Resumo", "resumo");
        l1.addView(a, navParams(true));
        l1.addView(b, navParams(false));
        root.addView(l1, TechCellUi.fullCardParams(this, 14));

        LinearLayout l2 = new LinearLayout(this);
        l2.setOrientation(LinearLayout.HORIZONTAL);
        l2.setWeightSum(2);
        Button c = nav("Fechamento", "fechamento");
        Button d = nav("Exportar", "exportar");
        l2.addView(c, navParams(true));
        l2.addView(d, navParams(false));
        root.addView(l2, TechCellUi.fullCardParams(this, 8));
    }

    private Button nav(String rotulo, String destino) {
        Button b = new Button(this);
        b.setText(rotulo);
        if (destino.equals(aba)) TechCellUi.stylePrimary(this, b, TechCellUi.NAVY);
        else TechCellUi.styleSecondary(this, b);
        b.setOnClickListener(v -> { aba = destino; render(); });
        return b;
    }

    private LinearLayout.LayoutParams navParams(boolean esquerda) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (esquerda) p.setMargins(0, 0, dp(4), 0); else p.setMargins(dp(4), 0, 0, 0);
        return p;
    }

    private void renderCaixa(LinearLayout root) {
        String d = hoje();
        Movimento existente = movimento(d);
        LinearLayout card = TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this, 18));

        TextView data = texto("Hoje • " + formatarDia(d), 14, true);
        data.setTextColor(TechCellUi.MUTED);
        card.addView(data);

        double total = existente == null ? 0 : existente.total;
        TextView totalView = texto(moeda.format(total), 36, true);
        totalView.setTextColor(TechCellUi.NAVY);
        card.addView(totalView, TechCellUi.fullCardParams(this, 8));

        if (existente != null) {
            TextView lock = texto("Lancamento ja realizado. Somente o Master pode alterar ou excluir.", 13, true);
            lock.setTextColor(TechCellUi.ORANGE);
            lock.setBackground(TechCellUi.solid(this, Color.parseColor("#FFF4E8"), 10));
            lock.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.addView(lock, TechCellUi.fullCardParams(this, 12));
        }

        EditText din = campoMoeda("Entrada em dinheiro", existente == null ? 0 : existente.dinheiro);
        EditText car = campoMoeda("Entrada em cartao + PIX", existente == null ? 0 : existente.cartao);
        din.setEnabled(existente == null);
        car.setEnabled(existente == null);
        card.addView(rotuloCampo("Entrada em dinheiro"), TechCellUi.fullCardParams(this, 16));
        card.addView(din, campoParams());
        card.addView(rotuloCampo("Entrada em cartao + PIX"), TechCellUi.fullCardParams(this, 12));
        card.addView(car, campoParams());

        Button salvar = botaoPrimario(existente == null ? "Criar lancamento" : "Lancamento bloqueado");
        salvar.setEnabled(existente == null && !TechCellDeveloperTestMode.ativo(this));
        salvar.setOnClickListener(v -> salvarCaixa(d, din, car));
        card.addView(salvar, botaoParams(16));
        root.addView(card);
    }

    private void renderLancamentosMaster(LinearLayout root) {
        LinearLayout card = TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this, 16));
        card.addView(texto("Lancamento", 22, true));

        EditText data = campoTexto(dataMaster, "AAAA-MM-DD");
        data.setFocusable(false);
        data.setOnClickListener(v -> abrirCalendario());
        card.addView(rotuloCampo("Data"), TechCellUi.fullCardParams(this, 12));
        card.addView(data, campoParams());

        Movimento atual = movimento(dataMaster);
        EditText din = campoMoeda("Dinheiro", atual == null ? 0 : atual.dinheiro);
        EditText car = campoMoeda("Cartao + PIX", atual == null ? 0 : atual.cartao);
        card.addView(rotuloCampo("Entrada em dinheiro"), TechCellUi.fullCardParams(this, 12));
        card.addView(din, campoParams());
        card.addView(rotuloCampo("Entrada em cartao + PIX"), TechCellUi.fullCardParams(this, 12));
        card.addView(car, campoParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button salvar = botaoPrimario(atual == null ? "Criar lancamento" : "Salvar correcao");
        salvar.setEnabled(!TechCellDeveloperTestMode.ativo(this));
        salvar.setOnClickListener(v -> salvarMaster(dataMaster, din, car));
        Button excluir = botaoPerigo("Excluir");
        excluir.setEnabled(atual != null && !TechCellDeveloperTestMode.ativo(this));
        excluir.setOnClickListener(v -> excluirMaster(dataMaster));
        actions.addView(salvar, navParams(true));
        actions.addView(excluir, navParams(false));
        card.addView(actions, TechCellUi.fullCardParams(this, 16));
        root.addView(card);

        LinearLayout historico = TechCellUi.card(this);
        historico.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        historico.addView(texto("Historico • " + movimentos.size() + " lancamento(s)", 18, true));
        if (movimentos.isEmpty()) {
            TextView vazio = texto("Nenhum lancamento encontrado nesta empresa.", 13, false);
            vazio.setTextColor(TechCellUi.MUTED);
            historico.addView(vazio, TechCellUi.fullCardParams(this, 12));
        } else {
            for (Movimento m : movimentos) {
                TextView linha = texto(formatarDia(m.data) + "\nDinheiro " + moeda.format(m.dinheiro)
                        + " • Cartao/PIX " + moeda.format(m.cartao) + "\nTotal " + moeda.format(m.total), 14, false);
                linha.setPadding(dp(10), dp(12), dp(10), dp(12));
                linha.setBackground(TechCellUi.bordered(this, Color.WHITE, 10, TechCellUi.BORDER));
                linha.setOnClickListener(v -> { dataMaster = m.data; render(); });
                historico.addView(linha, TechCellUi.fullCardParams(this, 8));
            }
        }
        root.addView(historico);
    }

    private void abrirCalendario() {
        Calendar c = Calendar.getInstance();
        try { c.setTime(isoDia.parse(dataMaster)); } catch (Throwable ignored) {}
        DatePickerDialog d = new DatePickerDialog(this, (view, ano, mes, dia) -> {
            Calendar n = Calendar.getInstance();
            n.set(ano, mes, dia);
            dataMaster = isoDia.format(n.getTime());
            render();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private void renderResumo(LinearLayout root) {
        LinearLayout filtros = new LinearLayout(this);
        filtros.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = {"hoje", "mes", "ano", "tudo"};
        String[] nomes = {"Hoje", "Mes", "Ano", "Tudo"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button b = new Button(this);
            b.setText(nomes[i]);
            if (id.equals(periodo)) TechCellUi.stylePrimary(this, b, TechCellUi.NAVY); else TechCellUi.styleSecondary(this, b);
            b.setOnClickListener(v -> { periodo = id; render(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
            p.setMargins(i == 0 ? 0 : dp(3), 0, i == ids.length - 1 ? 0 : dp(3), 0);
            filtros.addView(b, p);
        }
        root.addView(filtros, TechCellUi.fullCardParams(this, 16));

        double dinheiro = 0, cartao = 0;
        int dias = 0;
        for (Movimento m : movimentos) {
            if (!incluiPeriodo(m.data)) continue;
            dinheiro += m.dinheiro;
            cartao += m.cartao;
            dias++;
        }
        LinearLayout card = TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        card.addView(texto("Resumo " + periodoExibicao(), 21, true));
        card.addView(linhaResumo("Dinheiro", moeda.format(dinheiro)), TechCellUi.fullCardParams(this, 12));
        card.addView(linhaResumo("Cartao + PIX", moeda.format(cartao)), TechCellUi.fullCardParams(this, 8));
        card.addView(linhaResumo("Total", moeda.format(dinheiro + cartao)), TechCellUi.fullCardParams(this, 8));
        TextView q = texto(dias + " dia(s) com lancamento", 12, false);
        q.setTextColor(TechCellUi.MUTED);
        card.addView(q, TechCellUi.fullCardParams(this, 10));
        root.addView(card);
    }

    private TextView linhaResumo(String nome, String valor) {
        TextView t = texto(nome + "\n" + valor, 16, true);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setBackground(TechCellUi.bordered(this, Color.WHITE, 10, TechCellUi.BORDER));
        return t;
    }

    private boolean incluiPeriodo(String data) {
        String h = hoje();
        if ("hoje".equals(periodo)) return h.equals(data);
        if ("mes".equals(periodo)) return data != null && data.startsWith(h.substring(0, 7));
        if ("ano".equals(periodo)) return data != null && data.startsWith(h.substring(0, 4));
        return true;
    }

    private String periodoExibicao() {
        if ("hoje".equals(periodo)) return "de hoje";
        if ("mes".equals(periodo)) return "do mes";
        if ("ano".equals(periodo)) return "do ano";
        return "geral";
    }

    private void renderFechamento(LinearLayout root) {
        LinearLayout card = TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this, 16));
        card.addView(texto("Fechamento mensal", 21, true));

        EditText mes = campoTexto(mesFechamento, "AAAA-MM");
        card.addView(rotuloCampo("Mes"), TechCellUi.fullCardParams(this, 12));
        card.addView(mes, campoParams());

        Fechamento atual = fechamento(mesFechamento);
        EditText lucro = campoMoeda("Lucro", atual == null ? entradasMes(mesFechamento) : atual.lucro);
        EditText despesas = campoMoeda("Despesas", atual == null ? 0 : atual.despesasTotal);
        card.addView(rotuloCampo("Lucro / entradas consideradas"), TechCellUi.fullCardParams(this, 12));
        card.addView(lucro, campoParams());
        card.addView(rotuloCampo("Total de despesas"), TechCellUi.fullCardParams(this, 12));
        card.addView(despesas, campoParams());

        Button salvar = botaoPrimario("Salvar fechamento");
        salvar.setEnabled(!TechCellDeveloperTestMode.ativo(this));
        salvar.setOnClickListener(v -> {
            String m = mes.getText().toString().trim();
            if (!m.matches("\\d{4}-\\d{2}")) { toast("Informe o mes no formato AAAA-MM."); return; }
            mesFechamento = m;
            salvarFechamento(m, valor(lucro), valor(despesas));
        });
        card.addView(salvar, botaoParams(16));
        root.addView(card);

        LinearLayout hist = TechCellUi.card(this);
        hist.setLayoutParams(TechCellUi.fullCardParams(this, 12));
        hist.addView(texto("Fechamentos salvos", 18, true));
        if (fechamentos.isEmpty()) {
            TextView v = texto("Nenhum fechamento encontrado.", 13, false);
            v.setTextColor(TechCellUi.MUTED);
            hist.addView(v, TechCellUi.fullCardParams(this, 10));
        } else {
            for (Fechamento f : fechamentos) {
                TextView l = texto(f.mes + "\nLucro " + moeda.format(f.lucro)
                        + " • Despesas " + moeda.format(f.despesasTotal)
                        + "\nResultado " + moeda.format(f.resultadoLiquido), 14, false);
                l.setPadding(dp(10), dp(12), dp(10), dp(12));
                l.setBackground(TechCellUi.bordered(this, Color.WHITE, 10, TechCellUi.BORDER));
                l.setOnClickListener(v -> { mesFechamento = f.mes; render(); });
                hist.addView(l, TechCellUi.fullCardParams(this, 8));
            }
        }
        root.addView(hist);
    }

    private void renderExportar(LinearLayout root) {
        LinearLayout card = TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this, 22));
        TextView t = texto("Planilha do Caixa", 24, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        TextView s = texto(movimentos.size() + " lancamento(s) • " + fechamentos.size() + " fechamento(s)\nSomente desta empresa.", 13, false);
        s.setTextColor(TechCellUi.MUTED);
        s.setGravity(Gravity.CENTER);
        card.addView(s, TechCellUi.fullCardParams(this, 10));
        Button exportar = botaoPrimario("Baixar Excel (.xls)");
        exportar.setOnClickListener(v -> exportarXls());
        card.addView(exportar, botaoParams(18));
        root.addView(card);
    }

    private void salvarCaixa(String data, EditText dinheiro, EditText cartao) {
        if (TechCellDeveloperTestMode.ativo(this)) return;
        double din = valor(dinheiro), car = valor(cartao);
        if (din < 0 || car < 0) { toast("Informe valores validos."); return; }
        DocumentReference ref = empresaRef.collection("caixa_movimentos").document(data);
        Map<String, Object> dados = movimentoMap(data, din, car);
        firestore.runTransaction(tx -> {
            DocumentSnapshot atual = tx.get(ref);
            if (atual.exists()) throw new FirebaseFirestoreException("EXISTS", FirebaseFirestoreException.Code.ABORTED);
            tx.set(ref, dados);
            return null;
        }).addOnSuccessListener(x -> toast("Lancamento salvo."))
          .addOnFailureListener(e -> toast(e instanceof FirebaseFirestoreException && "EXISTS".equals(e.getMessage())
                  ? "O caixa de hoje ja foi lancado." : "Nao foi possivel salvar: " + TechCellCloudSync.mensagemCloud(e)));
    }

    private void salvarMaster(String data, EditText dinheiro, EditText cartao) {
        if (TechCellDeveloperTestMode.ativo(this)) return;
        double din = valor(dinheiro), car = valor(cartao);
        if (data == null || data.trim().isEmpty() || din < 0 || car < 0) { toast("Confira a data e os valores."); return; }
        empresaRef.collection("caixa_movimentos").document(data)
                .set(movimentoMap(data, din, car), SetOptions.merge())
                .addOnSuccessListener(x -> toast("Lancamento salvo."))
                .addOnFailureListener(e -> toast("Nao foi possivel salvar: " + TechCellCloudSync.mensagemCloud(e)));
    }

    private Map<String, Object> movimentoMap(String data, double din, double car) {
        FirebaseUser user = TechCellCloudSync.auth(this).getCurrentUser();
        Map<String, Object> m = new HashMap<>();
        m.put("empresa_uuid", sessao.empresaUuid);
        m.put("source_uid", user == null ? "" : user.getUid());
        m.put("data", data);
        m.put("dinheiro", din);
        m.put("cartao", car);
        m.put("total", din + car);
        m.put("atualizadoPor", user == null || user.getEmail() == null ? sessao.email : user.getEmail());
        m.put("atualizadoEm", FieldValue.serverTimestamp());
        return m;
    }

    private void excluirMaster(String data) {
        if (!master || TechCellDeveloperTestMode.ativo(this)) return;
        empresaRef.collection("caixa_movimentos").document(data).delete()
                .addOnSuccessListener(x -> toast("Lancamento excluido."))
                .addOnFailureListener(e -> toast("Nao foi possivel excluir: " + TechCellCloudSync.mensagemCloud(e)));
    }

    private void salvarFechamento(String mes, double lucro, double despesas) {
        if (!master || TechCellDeveloperTestMode.ativo(this)) return;
        FirebaseUser user = TechCellCloudSync.auth(this).getCurrentUser();
        Map<String, Object> m = new HashMap<>();
        m.put("empresa_uuid", sessao.empresaUuid);
        m.put("source_uid", user == null ? "" : user.getUid());
        m.put("mes", mes);
        m.put("lucro", lucro);
        m.put("despesasTotal", despesas);
        m.put("resultadoLiquido", lucro - despesas);
        m.put("atualizadoPor", user == null || user.getEmail() == null ? sessao.email : user.getEmail());
        m.put("atualizadoEm", FieldValue.serverTimestamp());
        empresaRef.collection("caixa_fechamentos").document(mes).set(m, SetOptions.merge())
                .addOnSuccessListener(x -> toast("Fechamento salvo."))
                .addOnFailureListener(e -> toast("Nao foi possivel salvar: " + TechCellCloudSync.mensagemCloud(e)));
    }

    private void exportarXls() {
        try {
            StringBuilder rm = new StringBuilder();
            for (int i = movimentos.size() - 1; i >= 0; i--) {
                Movimento m = movimentos.get(i);
                rm.append("<Row>").append(cell(m.data, "String"))
                        .append(cell(String.valueOf(m.dinheiro), "Number"))
                        .append(cell(String.valueOf(m.cartao), "Number"))
                        .append(cell(String.valueOf(m.total), "Number"))
                        .append(cell(m.atualizadoPor, "String")).append("</Row>");
            }
            StringBuilder rf = new StringBuilder();
            for (int i = fechamentos.size() - 1; i >= 0; i--) {
                Fechamento f = fechamentos.get(i);
                rf.append("<Row>").append(cell(f.mes, "String"))
                        .append(cell(String.valueOf(f.lucro), "Number"))
                        .append(cell(String.valueOf(f.despesasTotal), "Number"))
                        .append(cell(String.valueOf(f.resultadoLiquido), "Number"))
                        .append("</Row>");
            }
            String xml = "<?xml version=\"1.0\"?>"
                    + "<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">"
                    + "<Worksheet ss:Name=\"Lancamentos\"><Table><Row>"
                    + cell("Data", "String") + cell("Dinheiro", "String") + cell("Cartao + PIX", "String") + cell("Total", "String") + cell("Atualizado por", "String")
                    + "</Row>" + rm + "</Table></Worksheet>"
                    + "<Worksheet ss:Name=\"Fechamentos\"><Table><Row>"
                    + cell("Mes", "String") + cell("Lucro", "String") + cell("Despesas", "String") + cell("Resultado liquido", "String")
                    + "</Row>" + rf + "</Table></Worksheet></Workbook>";
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            String nome = "caixa-da-loja-" + hoje() + ".xls";
            salvarArquivo(nome, "application/vnd.ms-excel", bytes);
            Toast.makeText(this, "Planilha salva em Downloads.", Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Nao foi possivel salvar a planilha.", Toast.LENGTH_LONG).show();
        }
    }

    private String cell(String valor, String tipo) {
        return "<Cell><Data ss:Type=\"" + tipo + "\">" + xml(valor) + "</Data></Cell>";
    }

    private String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void salvarArquivo(String nome, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, nome);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            android.net.Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Falha ao criar arquivo");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Falha ao abrir arquivo");
                out.write(bytes);
            }
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new IllegalStateException("Pasta indisponivel");
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(dir, nome))) { out.write(bytes); }
        }
    }

    private Movimento movimento(String data) {
        for (Movimento m : movimentos) if (data.equals(m.data)) return m;
        return null;
    }

    private Fechamento fechamento(String mes) {
        for (Fechamento f : fechamentos) if (mes.equals(f.mes)) return f;
        return null;
    }

    private double entradasMes(String mes) {
        double t = 0;
        for (Movimento m : movimentos) if (m.data != null && m.data.startsWith(mes)) t += m.total;
        return t;
    }

    private ScrollView envolver(LinearLayout root) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(TechCellUi.BG);
        s.addView(root);
        return s;
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(34));
        return root;
    }

    private TextView texto(String s, int tamanho, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tamanho);
        t.setTextColor(TechCellUi.TEXT);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView textoGrande(String s) { return texto(s, 34, true); }

    private TextView rotuloCampo(String s) {
        TextView t = texto(s, 13, true);
        t.setTextColor(TechCellUi.TEXT);
        return t;
    }

    private EditText campoTexto(String valor, String hint) {
        EditText e = new EditText(this);
        e.setText(valor == null ? "" : valor);
        e.setHint(hint);
        e.setTextSize(17);
        e.setSingleLine(true);
        TechCellUi.styleSearch(this, e);
        return e;
    }

    private EditText campoMoeda(String hint, double valor) {
        EditText e = campoTexto(valor == 0 ? "" : String.format(Locale.US, "%.2f", valor).replace('.', ','), hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private LinearLayout.LayoutParams campoParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        p.setMargins(0, dp(6), 0, 0);
        return p;
    }

    private Button botaoPrimario(String s) {
        Button b = new Button(this);
        b.setText(s);
        TechCellUi.stylePrimary(this, b, TechCellUi.NAVY);
        return b;
    }

    private Button botaoSecundario(String s) {
        Button b = new Button(this);
        b.setText(s);
        TechCellUi.styleSecondary(this, b);
        return b;
    }

    private Button botaoPerigo(String s) {
        Button b = new Button(this);
        b.setText(s);
        TechCellUi.styleDanger(this, b);
        return b;
    }

    private LinearLayout.LayoutParams botaoParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private double valor(EditText e) {
        if (e == null) return 0;
        String s = e.getText().toString().trim().replace(".", "").replace(',', '.');
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (Throwable ignored) { return -1; }
    }

    private String hoje() { return isoDia.format(Calendar.getInstance().getTime()); }

    private String formatarDia(String iso) {
        try { return brDia.format(isoDia.parse(iso)); } catch (Throwable ignored) { return iso; }
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }

    private static double numero(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v == null) return 0;
        try { return Double.parseDouble(String.valueOf(v).replace(',', '.')); }
        catch (Throwable ignored) { return 0; }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        try { if (movimentosListener != null) movimentosListener.remove(); } catch (Throwable ignored) {}
        try { if (fechamentosListener != null) fechamentosListener.remove(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
