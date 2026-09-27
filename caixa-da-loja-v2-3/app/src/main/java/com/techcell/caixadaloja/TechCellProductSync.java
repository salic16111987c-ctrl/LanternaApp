package com.techcell.caixadaloja;

import android.content.Context;

public final class TechCellProductSync {
    public static class Resultado {
        public int atualizados;
        public int removidos;
        public int paginas;
        public long cursorFinal;
        public boolean bloqueadoPorVendas;
        public boolean semAutorizacao;
        public String erro = "";
        public int total() { return atualizados + removidos; }
    }

    private TechCellProductSync(){}

    public static Resultado puxarAlteracoes(Context context) {
        Resultado out = new Resultado();
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();

        if (!ctx.configurado || "MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            return out;
        }
        if (ctx.masterHost == null || ctx.masterHost.trim().isEmpty()) {
            return out;
        }
        if (db.countVendasPendentesMaster() > 0) {
            out.bloqueadoPorVendas = true;
            out.cursorFinal = ctx.lastProductPullSeq;
            return out;
        }
        if (ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) {
            out.semAutorizacao = true;
            out.cursorFinal = ctx.lastProductPullSeq;
            return out;
        }

        long cursor = Math.max(0, ctx.lastProductPullSeq);
        try {
            for (int pagina = 0; pagina < 50; pagina++) {
                String json = TechCellLanClient.baixarDeltaProdutos(
                        ctx.masterHost,
                        ctx.masterPort > 0 ? ctx.masterPort : 8765,
                        ctx.dispositivoUuid,
                        ctx.masterAuthToken,
                        cursor);

                GestaoDbHelper.ProductDeltaStats st = db.aplicarDeltaProdutos(json);
                out.atualizados += st.upserts;
                out.removidos += st.deletes;
                out.paginas++;
                out.cursorFinal = st.cursor;

                if (!st.hasMore || st.cursor <= cursor) break;
                cursor = st.cursor;
            }
            return out;
        } catch (Throwable e) {
            out.erro = mensagem(e);
            db.registrarErroPullProdutos(out.erro);
            return out;
        }
    }

    private static String mensagem(Throwable e) {
        if (e == null) return "erro desconhecido";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
