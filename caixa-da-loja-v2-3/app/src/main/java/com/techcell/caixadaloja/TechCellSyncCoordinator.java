package com.techcell.caixadaloja;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Alpha 41: ponto único de sincronização LAN no terminal.
 * Evita que PDV, telas e serviço em segundo plano executem ciclos simultâneos.
 */
public final class TechCellSyncCoordinator {
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static volatile boolean emAndamento;
    private static volatile long ultimoInicio;
    private static volatile long ultimoFim;
    private static volatile long ultimoSucesso;
    private static volatile String ultimoErro = "";

    public static class Resultado {
        public boolean executou;
        public boolean ocupado;
        public boolean ignorado;
        public boolean conflitoEstoque;
        public int vendasEnviadas;
        public int vendasJaExistiam;
        public int vendasRecebidas;
        public int produtosAlterados;
        public int pendentesDepois;
        public long iniciadoEm;
        public long finalizadoEm;
        public String erro = "";

        public int totalAlteracoesRecebidas() {
            return vendasRecebidas + produtosAlterados;
        }

        public boolean ok() {
            return executou && (erro == null || erro.trim().isEmpty());
        }
    }

    private TechCellSyncCoordinator(){}

    public static Resultado sincronizar(Context context) {
        return sincronizar(context, "");
    }

    public static Resultado sincronizar(Context context, String codigoPareamento) {
        Resultado out = new Resultado();
        Context app = context.getApplicationContext();

        if (!LOCK.tryLock()) {
            out.ocupado = true;
            return out;
        }

        emAndamento = true;
        out.executou = true;
        out.iniciadoEm = System.currentTimeMillis();
        ultimoInicio = out.iniciadoEm;

        try {
            GestaoDbHelper db = new GestaoDbHelper(app);
            GestaoDbHelper.SyncContext ctx = db.getSyncContext();

            if (!ctx.configurado || "MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                out.ignorado = true;
                return finalizar(out);
            }
            if (ctx.masterHost == null || ctx.masterHost.trim().isEmpty()) {
                out.erro = "Master não vinculado.";
                return falhar(out);
            }
            boolean temToken = ctx.masterAuthToken != null && !ctx.masterAuthToken.trim().isEmpty();
            String codigo = codigoPareamento == null ? "" : codigoPareamento.trim();
            if (!temToken && codigo.isEmpty()) {
                out.erro = "Terminal ainda não autorizado pelo Master.";
                return falhar(out);
            }

            TechCellSaleSync.Resultado envio = null;
            if (db.countVendasPendentesMaster() > 0) {
                envio = TechCellSaleSync.enviarPendentes(app, codigo);
                out.vendasEnviadas = envio.enviadas;
                out.vendasJaExistiam = envio.jaExistiam;
                out.pendentesDepois = envio.pendentesDepois;

                if (envio.erro != null && !envio.erro.trim().isEmpty()) {
                    out.erro = envio.erro.trim();
                    out.conflitoEstoque = ehConflitoEstoque(out.erro);
                    return falhar(out);
                }
            }

            // O envio pode ter obtido o token de autorização; recarrega o contexto.
            ctx = db.getSyncContext();
            if (ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) {
                out.erro = "Terminal ainda não autorizado pelo Master.";
                return falhar(out);
            }

            TechCellProductSync.Resultado produtos = TechCellProductSync.puxarAlteracoes(app);
            out.produtosAlterados = produtos.total();
            if (produtos.erro != null && !produtos.erro.trim().isEmpty()) {
                out.erro = produtos.erro.trim();
                return falhar(out);
            }

            TechCellSalePullSync.Resultado vendas = TechCellSalePullSync.puxarAlteracoes(app);
            out.vendasRecebidas = vendas.total();
            if (vendas.erro != null && !vendas.erro.trim().isEmpty()) {
                out.erro = vendas.erro.trim();
                return falhar(out);
            }

            out.pendentesDepois = db.countVendasPendentesMaster();
            ultimoErro = "";
            ultimoSucesso = System.currentTimeMillis();
            return finalizar(out);
        } catch (Throwable e) {
            out.erro = mensagem(e);
            out.conflitoEstoque = ehConflitoEstoque(out.erro);
            return falhar(out);
        } finally {
            emAndamento = false;
            ultimoFim = System.currentTimeMillis();
            LOCK.unlock();
        }
    }

    private static Resultado falhar(Resultado out) {
        ultimoErro = out.erro == null ? "" : out.erro;
        return finalizar(out);
    }

    private static Resultado finalizar(Resultado out) {
        out.finalizadoEm = System.currentTimeMillis();
        return out;
    }

    private static boolean ehConflitoEstoque(String erro) {
        if (erro == null) return false;
        String x = erro.toLowerCase(new Locale("pt","BR"));
        return x.contains("estoque insuficiente") ||
                x.contains("estoque mudou") ||
                x.contains("disponível:");
    }

    private static String mensagem(Throwable e) {
        if (e == null) return "erro desconhecido";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m.trim();
    }

    public static boolean iniciarManutencao() {
        if (!LOCK.tryLock()) return false;
        emAndamento = true;
        ultimoInicio = System.currentTimeMillis();
        return true;
    }

    public static void finalizarManutencao() {
        if (!LOCK.isHeldByCurrentThread()) return;
        emAndamento = false;
        ultimoFim = System.currentTimeMillis();
        LOCK.unlock();
    }

    public static boolean estaSincronizando() {
        return emAndamento || LOCK.isLocked();
    }

    public static long ultimoSucessoEm() {
        return ultimoSucesso;
    }

    public static String ultimoErro() {
        return ultimoErro == null ? "" : ultimoErro;
    }

    public static String resumo(Context context) {
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();

        if (!ctx.configurado) return "Sincronização: dispositivo ainda não configurado";
        if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            return "Sincronização: Master é a fonte principal desta loja";
        }
        if (ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) {
            return "Sincronização: aguardando autorização do Master";
        }
        if (estaSincronizando()) return "Sincronização: atualizando agora…";

        int pendentes = db.countVendasPendentesMaster();
        String erro = primeiroErro(ctx);
        if (!erro.isEmpty()) {
            if (ehConflitoEstoque(erro)) {
                return "Sincronização: venda pendente por conflito de estoque";
            }
            return "Sincronização: atenção • " + encurtar(erro, 90);
        }
        if (pendentes > 0) {
            return "Sincronização: " + pendentes + " venda(s) aguardando o Master";
        }

        long ultima = Math.max(ctx.lastSalePushAt,
                Math.max(ctx.lastProductPullAt, ctx.lastSalePullAt));
        if (ultima <= 0) return "Sincronização: pronta • aguardando primeiro ciclo";

        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss", new Locale("pt","BR"));
        return "Sincronização: em dia ✓ • última " + f.format(new Date(ultima));
    }

    private static String primeiroErro(GestaoDbHelper.SyncContext ctx) {
        if (ctx.lastSalePushError != null && !ctx.lastSalePushError.trim().isEmpty())
            return ctx.lastSalePushError.trim();
        if (ctx.lastProductPullError != null && !ctx.lastProductPullError.trim().isEmpty())
            return ctx.lastProductPullError.trim();
        if (ctx.lastSalePullError != null && !ctx.lastSalePullError.trim().isEmpty())
            return ctx.lastSalePullError.trim();
        return ultimoErro();
    }

    private static String encurtar(String s, int max) {
        if (s == null) return "";
        String x = s.replace('\n',' ').replace('\r',' ').trim();
        return x.length() <= max ? x : x.substring(0, max - 1) + "…";
    }
}
