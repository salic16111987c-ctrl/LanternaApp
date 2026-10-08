package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fila híbrida de vendas pela nuvem.
 *
 * A LAN continua sendo o caminho local principal. Quando o Caixa está fora da
 * rede do Master, a mesma venda pendente é colocada em vendas_entrada. O Master
 * consome a fila usando exatamente a mesma rotina transacional da LAN, portanto
 * UUID, estoque, estorno e numeração continuam idempotentes.
 */
public final class TechCellCloudSaleQueue {
    private static final String ROOT = "techcell_empresas";
    private static final String INBOX = "vendas_entrada";
    private static final long READ_TIMEOUT_SECONDS = 12L;
    private static final long WRITE_TIMEOUT_SECONDS = 20L;
    private static final Object LOCK = new Object();

    public static class Resultado {
        public boolean ok;
        public int enviadas;
        public int aceitas;
        public int confirmadas;
        public int erros;
        public String erro = "";
    }

    private TechCellCloudSaleQueue() {}

    public static Resultado sincronizar(Context context) {
        synchronized (LOCK) {
            Resultado out = new Resultado();
            Context app = context.getApplicationContext();
            GestaoDbHelper helper = new GestaoDbHelper(app);
            try {
                GestaoDbHelper.SyncContext ctx = helper.getSyncContext();
                if (!ctx.configurado || !ctx.cloudAtiva || vazio(ctx.empresaUuid)) {
                    out.ok = true;
                    return out;
                }

                FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
                if (user == null) {
                    out.ok = true;
                    return out;
                }

                DocumentReference empresa = TechCellCloudSync.firestore(app)
                        .collection(ROOT).document(ctx.empresaUuid);
                DocumentSnapshot emp = Tasks.await(
                        empresa.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!emp.exists()) throw new IllegalStateException("Empresa não encontrada na Nuvem Tech Cell.");

                String ownerUid = texto(emp.get("owner_uid"));
                boolean owner = user.getUid().equals(ownerUid);
                if (!owner) validarMembroAtivo(empresa, user.getUid());

                if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    if (!owner) {
                        throw new IllegalStateException("O Master físico precisa estar conectado à conta Administrador proprietária.");
                    }
                    processarNoMaster(helper, empresa, ctx, out);
                } else {
                    enviarOuConfirmarDoTerminal(helper, empresa, ctx, user, out);
                }

                out.ok = true;
                return out;
            } catch (Throwable e) {
                out.erro = TechCellCloudSync.mensagemCloud(e);
                return out;
            } finally {
                helper.close();
            }
        }
    }

    private static void validarMembroAtivo(DocumentReference empresa, String uid) throws Exception {
        DocumentSnapshot u = Tasks.await(
                empresa.collection("usuarios").document(uid).get(Source.SERVER),
                READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!u.exists()) throw new IllegalStateException("Esta conta não está vinculada a esta empresa.");
        Object ativo = u.get("ativo");
        if (ativo instanceof Boolean && !((Boolean) ativo)) {
            throw new IllegalStateException("Esta conta está bloqueada pelo Administrador.");
        }
    }

    /**
     * Terminal/Caixa: mantém a venda PENDENTE localmente até o ACK do Master.
     * Assim a LAN ainda pode vencer a corrida e concluir a mesma venda sem duplicar.
     */
    private static void enviarOuConfirmarDoTerminal(GestaoDbHelper helper,
                                                     DocumentReference empresa,
                                                     GestaoDbHelper.SyncContext ctx,
                                                     FirebaseUser user,
                                                     Resultado out) throws Exception {
        List<Long> pendentes = helper.listarIdsVendasPendentesMaster();
        for (Long vendaId : pendentes) {
            if (vendaId == null || vendaId <= 0) continue;

            JSONObject pacote = new JSONObject(helper.exportarVendaParaMaster(vendaId));
            JSONObject venda = pacote.getJSONObject("venda");
            JSONArray itens = pacote.getJSONArray("itens");
            String uuid = venda.optString("uuid", "").trim();
            if (uuid.isEmpty()) continue;

            DocumentReference ref = empresa.collection(INBOX).document(uuid);
            DocumentSnapshot atual = Tasks.await(
                    ref.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (atual.exists()) {
                String status = texto(atual.get("status"));
                if ("ACEITA_MASTER".equalsIgnoreCase(status)) {
                    long masterId = numeroLong(atual.get("master_sale_id"));
                    confirmarLocal(helper, vendaId, masterId);
                    out.confirmadas++;
                    continue;
                }
                if ("PENDENTE_MASTER".equalsIgnoreCase(status)) {
                    continue;
                }
                if ("ERRO_MASTER".equalsIgnoreCase(status)) {
                    long versaoLocal = venda.optLong("sync_updated_at", 0L);
                    long versaoFila = numeroLong(atual.get("source_sync_updated_at"));
                    if (versaoLocal <= versaoFila) {
                        String erro = texto(atual.get("master_error"));
                        helper.registrarResultadoEnvioVendas(0,
                                erro.isEmpty() ? "Venda aguardando correção no Master." : erro);
                        out.erros++;
                        continue;
                    }
                }
            }

            Map<String,Object> fila = new HashMap<>();
            fila.put("empresa_uuid", ctx.empresaUuid);
            fila.put("filial_uuid", ctx.filialUuid);
            fila.put("dispositivo_uuid", ctx.dispositivoUuid);
            fila.put("source_uid", user.getUid());
            fila.put("source_email", user.getEmail() == null ? "" : user.getEmail());
            fila.put("source_sync_updated_at", venda.optLong("sync_updated_at", System.currentTimeMillis()));
            fila.put("status", "PENDENTE_MASTER");
            fila.put("venda", jsonObjetoParaMap(venda));
            fila.put("itens", jsonArrayParaList(itens));
            fila.put("queued_at", FieldValue.serverTimestamp());
            fila.put("updated_at", FieldValue.serverTimestamp());
            fila.put("master_error", "");

            Tasks.await(ref.set(fila, SetOptions.merge()),
                    WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            out.enviadas++;
        }
    }

    /** Master: consome somente documentos pendentes e usa a mesma regra da LAN. */
    private static void processarNoMaster(GestaoDbHelper helper,
                                          DocumentReference empresa,
                                          GestaoDbHelper.SyncContext ctx,
                                          Resultado out) throws Exception {
        QuerySnapshot qs = Tasks.await(
                empresa.collection(INBOX)
                        .whereEqualTo("status", "PENDENTE_MASTER")
                        .limit(100)
                        .get(Source.SERVER),
                READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (QueryDocumentSnapshot d : qs) {
            DocumentReference ref = d.getReference();
            try {
                String empresaUuid = texto(d.get("empresa_uuid"));
                String filialUuid = texto(d.get("filial_uuid"));
                if (!ctx.empresaUuid.equalsIgnoreCase(empresaUuid) ||
                        !ctx.filialUuid.equalsIgnoreCase(filialUuid)) {
                    throw new IllegalStateException("Venda remota pertence a outra empresa/filial.");
                }

                Object vendaRaw = d.get("venda");
                Object itensRaw = d.get("itens");
                if (!(vendaRaw instanceof Map) || !(itensRaw instanceof List)) {
                    throw new IllegalStateException("Venda remota incompleta na nuvem.");
                }

                @SuppressWarnings("unchecked")
                Map<String,Object> vendaMap = (Map<String,Object>) vendaRaw;
                @SuppressWarnings("unchecked")
                List<Object> itensList = (List<Object>) itensRaw;

                JSONObject root = new JSONObject();
                root.put("schema", 1);
                root.put("empresa_uuid", ctx.empresaUuid);
                root.put("filial_uuid", ctx.filialUuid);
                root.put("dispositivo_uuid", texto(d.get("dispositivo_uuid")));
                root.put("venda", new JSONObject(vendaMap));

                JSONArray itens = new JSONArray();
                for (Object item : itensList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) item;
                        itens.put(new JSONObject(m));
                    }
                }
                root.put("itens", itens);

                GestaoDbHelper.RecebimentoVenda recebido = helper.receberVendaDoTerminal(root.toString());

                Map<String,Object> ack = new HashMap<>();
                ack.put("status", "ACEITA_MASTER");
                ack.put("master_sale_id", recebido.vendaIdMaster);
                ack.put("master_device_uuid", ctx.dispositivoUuid);
                ack.put("master_accepted_at", FieldValue.serverTimestamp());
                ack.put("updated_at", FieldValue.serverTimestamp());
                ack.put("master_error", "");
                Tasks.await(ref.set(ack, SetOptions.merge()),
                        WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                out.aceitas++;
            } catch (Throwable e) {
                String erro = mensagem(e);
                Map<String,Object> falha = new HashMap<>();
                falha.put("status", "ERRO_MASTER");
                falha.put("master_error", erro);
                falha.put("master_device_uuid", ctx.dispositivoUuid);
                falha.put("master_checked_at", FieldValue.serverTimestamp());
                falha.put("updated_at", FieldValue.serverTimestamp());
                try {
                    Tasks.await(ref.set(falha, SetOptions.merge()),
                            WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Throwable ignored) {}
                out.erros++;
            }
        }
    }

    private static void confirmarLocal(GestaoDbHelper helper, long vendaId, long masterVendaId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();

        ContentValues venda = new ContentValues();
        if (masterVendaId > 0) venda.put("master_sale_id", masterVendaId);
        venda.put("sync_status", "MASTER_SYNCED");
        venda.put("sync_updated_at", now);
        db.update("vendas", venda, "id=?", new String[]{String.valueOf(vendaId)});

        ContentValues itens = new ContentValues();
        itens.put("sync_status", "MASTER_SYNCED");
        itens.put("sync_updated_at", now);
        db.update("venda_itens", itens, "venda_id=?", new String[]{String.valueOf(vendaId)});
        helper.registrarResultadoEnvioVendas(1, "");
    }

    private static Map<String,Object> jsonObjetoParaMap(JSONObject o) throws Exception {
        Map<String,Object> out = new HashMap<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            out.put(k, jsonValor(o.opt(k)));
        }
        return out;
    }

    private static List<Object> jsonArrayParaList(JSONArray a) throws Exception {
        List<Object> out = new ArrayList<>();
        for (int i=0;i<a.length();i++) out.add(jsonValor(a.opt(i)));
        return out;
    }

    private static Object jsonValor(Object v) throws Exception {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof JSONObject) return jsonObjetoParaMap((JSONObject) v);
        if (v instanceof JSONArray) return jsonArrayParaList((JSONArray) v);
        return v;
    }

    private static long numeroLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(texto(v)); }
        catch (Throwable ignored) { return 0L; }
    }

    private static boolean vazio(String s) { return s == null || s.trim().isEmpty(); }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }

    private static String mensagem(Throwable e) {
        Throwable x = e;
        while (x != null && x.getCause() != null && x.getCause() != x) x = x.getCause();
        if (x == null) return "Erro desconhecido";
        String m = x.getMessage();
        return m == null || m.trim().isEmpty() ? x.getClass().getSimpleName() : m.trim();
    }
}
