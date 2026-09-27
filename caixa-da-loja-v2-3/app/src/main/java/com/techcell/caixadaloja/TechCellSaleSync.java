package com.techcell.caixadaloja;

import android.content.Context;

import java.util.List;

public final class TechCellSaleSync {
    public static class Resultado {
        public int enviadas;
        public int jaExistiam;
        public int pendentesAntes;
        public int pendentesDepois;
        public String erro = "";
    }

    private TechCellSaleSync(){}

    public static Resultado enviarPendentes(Context context, String codigoPareamento) {
        Resultado out = new Resultado();
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();

        if (!ctx.configurado || "MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            return out;
        }
        if (ctx.masterHost == null || ctx.masterHost.trim().isEmpty()) {
            out.erro = "Master não configurado.";
            db.registrarResultadoEnvioVendas(0, out.erro);
            return out;
        }

        List<Long> pendentes = db.listarIdsVendasPendentesMaster();
        out.pendentesAntes = pendentes.size();
        if (pendentes.isEmpty()) {
            db.registrarResultadoEnvioVendas(0, "");
            return out;
        }

        String token = ctx.masterAuthToken == null ? "" : ctx.masterAuthToken.trim();
        String codigo = codigoPareamento == null ? "" : codigoPareamento.trim();

        for (Long vendaId : pendentes) {
            try {
                String json = db.exportarVendaParaMaster(vendaId);
                TechCellLanClient.SaleAck ack = TechCellLanClient.enviarVenda(
                        ctx.masterHost,
                        ctx.masterPort > 0 ? ctx.masterPort : 8765,
                        ctx.dispositivoUuid,
                        token,
                        codigo,
                        json);

                if (ack.authToken != null && !ack.authToken.trim().isEmpty() &&
                        !ack.authToken.equals(token)) {
                    token = ack.authToken.trim();
                    db.salvarTokenMaster(token);
                }

                db.marcarVendaSincronizadaMaster(vendaId, ack.masterVendaId);
                out.enviadas++;
                if (ack.jaExistia) out.jaExistiam++;
            } catch (Throwable e) {
                out.erro = mensagem(e);
                break;
            }
        }

        out.pendentesDepois = db.countVendasPendentesMaster();
        db.registrarResultadoEnvioVendas(out.enviadas, out.erro);
        return out;
    }

    private static String mensagem(Throwable e) {
        if (e == null) return "erro desconhecido";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
