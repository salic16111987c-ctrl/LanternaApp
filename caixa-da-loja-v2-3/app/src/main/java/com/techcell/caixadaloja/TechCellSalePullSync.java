package com.techcell.caixadaloja;

import android.content.Context;

public final class TechCellSalePullSync {
    public static class Resultado {
        public int atualizadas;
        public int removidas;
        public int paginas;
        public long cursorFinal;
        public boolean semAutorizacao;
        public String erro = "";
        public int total() { return atualizadas + removidas; }
    }

    private TechCellSalePullSync(){}

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
        if (ctx.masterAuthToken == null || ctx.masterAuthToken.trim().isEmpty()) {
            out.semAutorizacao = true;
            out.cursorFinal = ctx.lastSalePullSeq;
            return out;
        }

        long cursor = Math.max(0, ctx.lastSalePullSeq);
        try {
            for (int pagina = 0; pagina < 50; pagina++) {
                String json = TechCellLanClient.baixarDeltaVendas(
                        ctx.masterHost,
                        ctx.masterPort > 0 ? ctx.masterPort : 8765,
                        ctx.dispositivoUuid,
                        ctx.masterAuthToken,
                        cursor);

                GestaoDbHelper.SaleDeltaStats st = db.aplicarDeltaVendas(json);
                out.atualizadas += st.upserts;
                out.removidas += st.deletes;
                out.paginas++;
                out.cursorFinal = st.cursor;

                if (!st.hasMore || st.cursor <= cursor) break;
                cursor = st.cursor;
            }
            return out;
        } catch (Throwable e) {
            out.erro = mensagem(e);
            db.registrarErroPullVendas(out.erro);
            return out;
        }
    }

    private static String mensagem(Throwable e) {
        if (e == null) return "erro desconhecido";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
