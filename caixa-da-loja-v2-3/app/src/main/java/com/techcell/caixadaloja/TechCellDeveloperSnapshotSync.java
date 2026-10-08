package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Carga somente leitura da empresa para o SQLite temporário do modo Desenvolvedor. */
public final class TechCellDeveloperSnapshotSync {
    private static final String ROOT = "techcell_empresas";
    private static final long TIMEOUT = 20L;

    public static class Resultado {
        public boolean ok;
        public int produtos;
        public int clientes;
        public int fornecedores;
        public int despesas;
        public int vendas;
        public String erro = "";
    }

    private TechCellDeveloperSnapshotSync() {}

    public static Resultado carregar(Context context) {
        Resultado out = new Resultado();
        Context app = context.getApplicationContext();
        GestaoDbHelper helper = null;
        try {
            TechCellDeveloperAccess.exigir(app);
            if (!TechCellDeveloperTestMode.ativo(app)) throw new IllegalStateException("Modo de teste não está ativo.");
            TechCellDeveloperTestMode.Estado teste = TechCellDeveloperTestMode.estado(app);
            helper = new GestaoDbHelper(app);
            GestaoDbHelper.SyncContext ctx = helper.getSyncContext();
            SQLiteDatabase db = helper.getWritableDatabase();

            DocumentReference empresa = TechCellCloudSync.firestore(app).collection(ROOT).document(teste.empresaUuid);
            DocumentSnapshot emp = Tasks.await(empresa.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
            if (!emp.exists()) throw new IllegalStateException("Empresa não encontrada na nuvem.");

            out.produtos = puxarTabela(db, empresa, ctx, "produtos", "produtos");
            out.clientes = puxarTabela(db, empresa, ctx, "clientes", "clientes");
            out.fornecedores = puxarTabela(db, empresa, ctx, "fornecedores", "fornecedores");
            out.despesas = puxarTabela(db, empresa, ctx, "despesas", "despesas");
            aplicarConfig(db, emp, ctx);
            out.vendas = puxarVendas(db, empresa, ctx);
            out.ok = true;
        } catch (Throwable e) {
            out.erro = TechCellCloudUsers.mensagem(e);
        } finally {
            if (helper != null) try { helper.close(); } catch (Throwable ignored) {}
        }
        return out;
    }

    private static int puxarTabela(SQLiteDatabase db, DocumentReference empresa,
                                   GestaoDbHelper.SyncContext ctx, String colecao,
                                   String tabela) throws Exception {
        QuerySnapshot qs = Tasks.await(empresa.collection(colecao).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        int total = 0;
        if (qs == null) return 0;
        for (QueryDocumentSnapshot d : qs) {
            aplicarDocumento(db, tabela, d.getId(), d.getData(), ctx);
            total++;
        }
        return total;
    }

    private static void aplicarConfig(SQLiteDatabase db, DocumentSnapshot empresaDoc,
                                      GestaoDbHelper.SyncContext ctx) {
        Object raw = empresaDoc.get("config");
        if (!(raw instanceof Map)) return;
        @SuppressWarnings("unchecked") Map<String,Object> mapa = (Map<String,Object>) raw;
        Set<String> cols = colunas(db, "empresa_config");
        ContentValues v = mapaParaValues(mapa, cols);
        v.put("id", 1);
        if (cols.contains("empresa_uuid")) v.put("empresa_uuid", ctx.empresaUuid);
        if (cols.contains("filial_uuid")) v.put("filial_uuid", ctx.filialUuid);
        if (cols.contains("sync_status")) v.put("sync_status", "DEV_TEST");
        if (cols.contains("sync_updated_at")) v.put("sync_updated_at", System.currentTimeMillis());
        int n = db.update("empresa_config", v, "id=1", null);
        if (n == 0) db.insert("empresa_config", null, v);
    }

    private static void aplicarDocumento(SQLiteDatabase db, String tabela, String uuid,
                                         Map<String,Object> mapa, GestaoDbHelper.SyncContext ctx) {
        Set<String> cols = colunas(db, tabela);
        ContentValues v = mapaParaValues(mapa, cols);
        if (cols.contains("uuid")) v.put("uuid", uuid);
        if (cols.contains("empresa_uuid")) v.put("empresa_uuid", ctx.empresaUuid);
        if (cols.contains("filial_uuid")) v.put("filial_uuid", ctx.filialUuid);
        if (cols.contains("dispositivo_uuid")) v.put("dispositivo_uuid", "DEV_TEST");
        if (cols.contains("sync_status")) v.put("sync_status", "DEV_TEST");
        if (cols.contains("sync_updated_at")) v.put("sync_updated_at", System.currentTimeMillis());
        long now = System.currentTimeMillis();
        if (cols.contains("created_at") && !v.containsKey("created_at")) v.put("created_at", now);
        if (cols.contains("updated_at") && !v.containsKey("updated_at")) v.put("updated_at", now);
        db.insertWithOnConflict(tabela, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static int puxarVendas(SQLiteDatabase db, DocumentReference empresa,
                                   GestaoDbHelper.SyncContext ctx) throws Exception {
        QuerySnapshot qs = Tasks.await(empresa.collection("vendas").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (qs == null) return 0;
        int total = 0;
        Set<String> colVenda = colunas(db, "vendas");
        Set<String> colItem = colunas(db, "venda_itens");
        for (QueryDocumentSnapshot d : qs) {
            Map<String,Object> dados = new HashMap<>(d.getData());
            Object itensRaw = dados.remove("itens");
            dados.remove("cloud_received_at");
            dados.remove("master_dispositivo_uuid");
            dados.remove("cloud_source_uid");
            dados.remove("cloud_source_device_uuid");

            ContentValues venda = mapaParaValues(dados, colVenda);
            venda.put("uuid", d.getId());
            if (colVenda.contains("empresa_uuid")) venda.put("empresa_uuid", ctx.empresaUuid);
            if (colVenda.contains("filial_uuid")) venda.put("filial_uuid", ctx.filialUuid);
            if (colVenda.contains("dispositivo_uuid")) venda.put("dispositivo_uuid", "DEV_TEST");
            if (colVenda.contains("sync_status")) venda.put("sync_status", "DEV_TEST");
            if (colVenda.contains("sync_updated_at")) venda.put("sync_updated_at", System.currentTimeMillis());
            long now = System.currentTimeMillis();
            if (colVenda.contains("created_at") && !venda.containsKey("created_at")) venda.put("created_at", now);
            if (colVenda.contains("updated_at") && !venda.containsKey("updated_at")) venda.put("updated_at", now);
            long vendaId = db.insert("vendas", null, venda);
            if (vendaId <= 0) continue;

            if (itensRaw instanceof List) {
                @SuppressWarnings("unchecked") List<Object> itens = (List<Object>) itensRaw;
                for (Object o : itens) {
                    if (!(o instanceof Map)) continue;
                    @SuppressWarnings("unchecked") Map<String,Object> itemMap = (Map<String,Object>) o;
                    ContentValues item = mapaParaValues(itemMap, colItem);
                    item.remove("id");
                    item.put("venda_id", vendaId);
                    item.put("produto_id", localizarProdutoId(db, texto(itemMap.get("codigo"))));
                    if (colItem.contains("empresa_uuid")) item.put("empresa_uuid", ctx.empresaUuid);
                    if (colItem.contains("filial_uuid")) item.put("filial_uuid", ctx.filialUuid);
                    if (colItem.contains("dispositivo_uuid")) item.put("dispositivo_uuid", "DEV_TEST");
                    if (colItem.contains("sync_status")) item.put("sync_status", "DEV_TEST");
                    if (colItem.contains("sync_updated_at")) item.put("sync_updated_at", System.currentTimeMillis());
                    db.insert("venda_itens", null, item);
                }
            }
            total++;
        }
        return total;
    }

    private static ContentValues mapaParaValues(Map<String,Object> mapa, Set<String> permitidas) {
        ContentValues out = new ContentValues();
        for (Map.Entry<String,Object> e : mapa.entrySet()) {
            String k = e.getKey();
            if (k == null || !permitidas.contains(k) || "id".equalsIgnoreCase(k)) continue;
            Object v = e.getValue();
            if (v == null) out.putNull(k);
            else if (v instanceof Boolean) out.put(k, ((Boolean)v) ? 1 : 0);
            else if (v instanceof Double || v instanceof Float) out.put(k, ((Number)v).doubleValue());
            else if (v instanceof Number) out.put(k, ((Number)v).longValue());
            else if (v instanceof Timestamp) out.put(k, ((Timestamp)v).toDate().getTime());
            else if (v instanceof Map || v instanceof List) { /* somente estrutura cloud */ }
            else out.put(k, String.valueOf(v));
        }
        return out;
    }

    private static Set<String> colunas(SQLiteDatabase db, String tabela) {
        Set<String> out = new HashSet<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + tabela + ")", null);
        try { while (c.moveToNext()) out.add(c.getString(c.getColumnIndexOrThrow("name"))); }
        finally { c.close(); }
        return out;
    }

    private static long localizarProdutoId(SQLiteDatabase db, String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return 0;
        Cursor c = db.rawQuery("SELECT id FROM produtos WHERE codigo=? LIMIT 1", new String[]{codigo.trim()});
        try { return c.moveToFirst() ? c.getLong(0) : 0; }
        finally { c.close(); }
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
