package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sincronização pela internet usada na fase de testes multi-local.
 *
 * A conta Administrador/proprietária pode alterar cadastros de qualquer cidade.
 * Master, Caixa e terminais remotos recebem as alterações da mesma empresa pela
 * nuvem. O SQLite continua existindo como cache/continuidade quando a internet cai.
 */
public final class TechCellCloudRealtimeSync {
    private static final String ROOT = "techcell_empresas";
    private static final String PREF = "techcell_cloud_realtime_v1";
    private static final long READ_TIMEOUT_SECONDS = 12L;
    private static final long WRITE_TIMEOUT_SECONDS = 20L;
    private static final Object LOCK = new Object();

    public static class Resultado {
        public boolean ok;
        public boolean owner;
        public int enviados;
        public int recebidos;
        public int vendasRecebidas;
        public int exclusoes;
        public String erro = "";
    }

    private TechCellCloudRealtimeSync() {}

    public static Resultado sincronizar(Context context) {
        synchronized (LOCK) {
            Resultado out = new Resultado();
            Context app = context.getApplicationContext();
            GestaoDbHelper helper = new GestaoDbHelper(app);
            try {
                GestaoDbHelper.SyncContext ctx = helper.getSyncContext();
                if (!ctx.configurado || !ctx.cloudAtiva || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
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
                DocumentSnapshot empresaDoc = Tasks.await(empresa.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!empresaDoc.exists()) throw new IllegalStateException("Empresa não encontrada na Nuvem Tech Cell.");

                String ownerUid = texto(empresaDoc.get("owner_uid"));
                out.owner = user.getUid().equals(ownerUid);
                if (!out.owner) validarMembroAtivo(empresa, user.getUid());

                SQLiteDatabase db = helper.getWritableDatabase();

                // A conta proprietária pode trabalhar de qualquer aparelho/cidade.
                // Os demais perfis continuam leitura cloud + operação local/LAN nesta etapa.
                if (out.owner) {
                    out.enviados += enviarConfigPendente(db, empresa, ctx, user);
                    out.enviados += enviarTabelaPendente(db, empresa, ctx, user, "produtos", "produtos");
                    out.enviados += enviarTabelaPendente(db, empresa, ctx, user, "clientes", "clientes");
                    out.enviados += enviarTabelaPendente(db, empresa, ctx, user, "fornecedores", "fornecedores");
                    out.enviados += enviarTabelaPendente(db, empresa, ctx, user, "despesas", "despesas");
                    out.exclusoes += enviarExclusoes(db, empresa, ctx, user);
                }

                // Busca alterações feitas em qualquer cidade.
                long inicioCiclo = System.currentTimeMillis();
                SharedPreferences p = prefs(app);
                String chave = ctx.empresaUuid;
                long desdeCadastros = p.getLong("cadastros_" + chave, 0L);
                long desdeExclusoes = p.getLong("exclusoes_" + chave, 0L);
                long desdeVendas = p.getLong("vendas_" + chave, 0L);

                out.recebidos += puxarColecao(db, empresa, ctx, "produtos", "produtos", desdeCadastros);
                out.recebidos += puxarColecao(db, empresa, ctx, "clientes", "clientes", desdeCadastros);
                out.recebidos += puxarColecao(db, empresa, ctx, "fornecedores", "fornecedores", desdeCadastros);
                out.recebidos += puxarColecao(db, empresa, ctx, "despesas", "despesas", desdeCadastros);
                out.recebidos += aplicarConfigEmpresa(db, empresaDoc, ctx);
                out.exclusoes += puxarExclusoes(db, empresa, ctx, desdeExclusoes);

                // O Master físico já possui o histórico consolidado. Terminais remotos/
                // Caixas recebem o histórico pela nuvem para relatórios e painel.
                if (!"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    out.vendasRecebidas += puxarVendas(db, empresa, ctx, desdeVendas);
                }

                SharedPreferences.Editor pe = p.edit()
                        .putLong("cadastros_" + chave, inicioCiclo)
                        .putLong("exclusoes_" + chave, inicioCiclo);
                if (!"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    pe.putLong("vendas_" + chave, inicioCiclo);
                }
                pe.putLong("last_success", System.currentTimeMillis())
                        .putString("last_error", "")
                        .apply();

                if (out.owner) registrarDispositivo(empresa, ctx, user);
                out.ok = true;
                return out;
            } catch (Throwable e) {
                out.erro = TechCellCloudSync.mensagemCloud(e);
                prefs(app).edit().putString("last_error", out.erro == null ? "" : out.erro).apply();
                return out;
            } finally {
                helper.close();
            }
        }
    }

    private static void validarMembroAtivo(DocumentReference empresa, String uid) throws Exception {
        DocumentSnapshot u = Tasks.await(empresa.collection("usuarios").document(uid).get(Source.SERVER),
                READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!u.exists()) throw new IllegalStateException("Esta conta não está vinculada a esta empresa.");
        Object ativo = u.get("ativo");
        if (ativo instanceof Boolean && !((Boolean) ativo)) {
            throw new IllegalStateException("Esta conta está bloqueada pelo Administrador.");
        }
    }

    private static int enviarConfigPendente(SQLiteDatabase db, DocumentReference empresa,
                                             GestaoDbHelper.SyncContext ctx, FirebaseUser user) throws Exception {
        Cursor c = db.rawQuery("SELECT * FROM empresa_config WHERE id=1 LIMIT 1", null);
        try {
            if (!c.moveToFirst()) return 0;
            String status = colunaString(c, "sync_status");
            if (!"PENDENTE".equalsIgnoreCase(status)) return 0;

            Map<String,Object> config = cursorMap(c, true);
            Map<String,Object> update = new HashMap<>();
            update.put("config", config);
            update.put("config_source_uid", user.getUid());
            update.put("config_source_device_uuid", ctx.dispositivoUuid);
            update.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(empresa.set(update, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            marcarSincronizado(db, "empresa_config", c.getLong(c.getColumnIndexOrThrow("id")),
                    colunaLong(c, "sync_version", 0));
            return 1;
        } finally { c.close(); }
    }

    private static int enviarTabelaPendente(SQLiteDatabase db, DocumentReference empresa,
                                            GestaoDbHelper.SyncContext ctx, FirebaseUser user,
                                            String tabela, String colecao) throws Exception {
        Cursor c = db.rawQuery("SELECT * FROM " + tabela + " WHERE sync_status='PENDENTE' ORDER BY id LIMIT 300", null);
        int total = 0;
        try {
            while (c.moveToNext()) {
                String uuid = colunaString(c, "uuid");
                if (uuid.isEmpty()) continue;
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                long versao = colunaLong(c, "sync_version", 0);
                Map<String,Object> row = cursorMap(c, true);
                row.put("cloud_received_at", FieldValue.serverTimestamp());
                row.put("cloud_source_uid", user.getUid());
                row.put("cloud_source_device_uuid", ctx.dispositivoUuid);
                Tasks.await(empresa.collection(colecao).document(uuid).set(row, SetOptions.merge()),
                        WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                marcarSincronizado(db, tabela, id, versao);
                total++;
            }
        } finally { c.close(); }
        return total;
    }

    private static int enviarExclusoes(SQLiteDatabase db, DocumentReference empresa,
                                       GestaoDbHelper.SyncContext ctx, FirebaseUser user) throws Exception {
        Cursor c = db.rawQuery(
                "SELECT id,entidade,entidade_uuid FROM sync_tombstones WHERE sync_status='PENDENTE' ORDER BY deleted_at LIMIT 200",
                null);
        int total = 0;
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String entidade = c.getString(1) == null ? "" : c.getString(1).trim().toUpperCase();
                String uuid = c.getString(2) == null ? "" : c.getString(2).trim();
                String colecao = colecaoEntidade(entidade);
                if (colecao == null || uuid.isEmpty()) continue;

                Tasks.await(empresa.collection(colecao).document(uuid).delete(), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<String,Object> tomb = new HashMap<>();
                tomb.put("entidade", entidade);
                tomb.put("entidade_uuid", uuid);
                tomb.put("deleted_by_uid", user.getUid());
                tomb.put("source_device_uuid", ctx.dispositivoUuid);
                tomb.put("deleted_at", FieldValue.serverTimestamp());
                Tasks.await(empresa.collection("sync_tombstones")
                                .document(entidade + "_" + uuid).set(tomb, SetOptions.merge()),
                        WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                ContentValues done = new ContentValues();
                done.put("sync_status", "CLOUD_SYNCED");
                db.update("sync_tombstones", done, "id=?", new String[]{String.valueOf(id)});
                total++;
            }
        } finally { c.close(); }
        return total;
    }

    private static int puxarColecao(SQLiteDatabase db, DocumentReference empresa,
                                    GestaoDbHelper.SyncContext ctx, String colecao,
                                    String tabela, long desdeMillis) throws Exception {
        QuerySnapshot qs = consultarDesde(empresa, colecao, "cloud_received_at", desdeMillis);
        int total = 0;
        for (QueryDocumentSnapshot d : qs) {
            if (aplicarDocumento(db, tabela, d.getId(), d.getData(), ctx)) {
                total++;
                if ("produtos".equals(tabela) && "MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    registrarMudancaProdutoLan(db, d.getId(), "UPSERT");
                }
            }
        }
        return total;
    }

    private static QuerySnapshot consultarDesde(DocumentReference empresa, String colecao,
                                                 String campoTempo, long desdeMillis) throws Exception {
        if (desdeMillis <= 0) {
            return Tasks.await(empresa.collection(colecao).get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        Timestamp desde = new Timestamp(desdeMillis / 1000L,
                (int) ((desdeMillis % 1000L) * 1_000_000L));
        return Tasks.await(empresa.collection(colecao)
                        .whereGreaterThan(campoTempo, desde)
                        .get(Source.SERVER),
                READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static int aplicarConfigEmpresa(SQLiteDatabase db, DocumentSnapshot empresaDoc,
                                             GestaoDbHelper.SyncContext ctx) {
        Object raw = empresaDoc.get("config");
        if (!(raw instanceof Map)) return 0;
        Cursor local = db.rawQuery("SELECT sync_status FROM empresa_config WHERE id=1 LIMIT 1", null);
        try {
            if (local.moveToFirst() && "PENDENTE".equalsIgnoreCase(local.getString(0))) return 0;
        } finally { local.close(); }

        @SuppressWarnings("unchecked") Map<String,Object> mapa = (Map<String,Object>) raw;
        Set<String> colunas = colunas(db, "empresa_config");
        ContentValues v = mapaParaValues(mapa, colunas);
        v.put("id", 1);
        if (colunas.contains("empresa_uuid")) v.put("empresa_uuid", ctx.empresaUuid);
        if (colunas.contains("filial_uuid")) v.put("filial_uuid", ctx.filialUuid);
        if (colunas.contains("sync_status")) v.put("sync_status", "CLOUD_SYNCED");
        if (colunas.contains("sync_updated_at")) v.put("sync_updated_at", System.currentTimeMillis());
        int n = db.update("empresa_config", v, "id=1", null);
        if (n == 0) db.insert("empresa_config", null, v);
        return 1;
    }

    private static int puxarExclusoes(SQLiteDatabase db, DocumentReference empresa,
                                      GestaoDbHelper.SyncContext ctx, long desdeMillis) throws Exception {
        QuerySnapshot qs = consultarDesde(empresa, "sync_tombstones", "deleted_at", desdeMillis);
        int total = 0;
        for (QueryDocumentSnapshot d : qs) {
            String entidade = texto(d.get("entidade")).toUpperCase();
            String uuid = texto(d.get("entidade_uuid"));
            String tabela = tabelaEntidade(entidade);
            if (tabela == null || uuid.isEmpty()) continue;

            Cursor pend = db.rawQuery("SELECT sync_status FROM " + tabela + " WHERE uuid=? LIMIT 1", new String[]{uuid});
            boolean localPendente = false;
            try { localPendente = pend.moveToFirst() && "PENDENTE".equalsIgnoreCase(pend.getString(0)); }
            finally { pend.close(); }
            if (localPendente) continue;

            int apagados = db.delete(tabela, "uuid=?", new String[]{uuid});
            if (apagados > 0) {
                total++;
                if ("PRODUTO".equals(entidade) && "MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    registrarMudancaProdutoLan(db, uuid, "DELETE");
                }
            }
        }
        return total;
    }

    private static int puxarVendas(SQLiteDatabase db, DocumentReference empresa,
                                   GestaoDbHelper.SyncContext ctx, long desdeMillis) throws Exception {
        QuerySnapshot qs = consultarDesde(empresa, "vendas", "cloud_received_at", desdeMillis);
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

            String uuid = d.getId();
            Cursor pend = db.rawQuery("SELECT id,sync_status FROM vendas WHERE uuid=? LIMIT 1", new String[]{uuid});
            long vendaId = 0;
            boolean localPendente = false;
            try {
                if (pend.moveToFirst()) {
                    vendaId = pend.getLong(0);
                    localPendente = "PENDENTE".equalsIgnoreCase(pend.getString(1));
                }
            } finally { pend.close(); }
            if (localPendente) continue;

            ContentValues venda = mapaParaValues(dados, colVenda);
            venda.put("uuid", uuid);
            if (colVenda.contains("empresa_uuid")) venda.put("empresa_uuid", ctx.empresaUuid);
            if (colVenda.contains("filial_uuid")) venda.put("filial_uuid", ctx.filialUuid);
            if (colVenda.contains("sync_status")) venda.put("sync_status", "CLOUD_SYNCED");
            if (colVenda.contains("sync_updated_at")) venda.put("sync_updated_at", System.currentTimeMillis());

            if (vendaId > 0) {
                db.update("vendas", venda, "id=?", new String[]{String.valueOf(vendaId)});
            } else {
                vendaId = db.insert("vendas", null, venda);
            }
            if (vendaId <= 0) continue;

            db.delete("venda_itens", "venda_id=?", new String[]{String.valueOf(vendaId)});
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
                    if (colItem.contains("sync_status")) item.put("sync_status", "CLOUD_SYNCED");
                    if (colItem.contains("sync_updated_at")) item.put("sync_updated_at", System.currentTimeMillis());
                    db.insert("venda_itens", null, item);
                }
            }
            total++;
        }
        return total;
    }

    private static boolean aplicarDocumento(SQLiteDatabase db, String tabela, String uuid,
                                             Map<String,Object> mapa, GestaoDbHelper.SyncContext ctx) {
        Cursor local = db.rawQuery("SELECT id,sync_status FROM " + tabela + " WHERE uuid=? LIMIT 1", new String[]{uuid});
        long id = 0;
        boolean pendente = false;
        try {
            if (local.moveToFirst()) {
                id = local.getLong(0);
                pendente = "PENDENTE".equalsIgnoreCase(local.getString(1));
            }
        } finally { local.close(); }
        if (pendente) return false;

        Set<String> cols = colunas(db, tabela);
        ContentValues v = mapaParaValues(mapa, cols);
        v.put("uuid", uuid);
        if (cols.contains("empresa_uuid")) v.put("empresa_uuid", ctx.empresaUuid);
        if (cols.contains("filial_uuid")) v.put("filial_uuid", ctx.filialUuid);
        if (cols.contains("sync_status")) v.put("sync_status", "CLOUD_SYNCED");
        if (cols.contains("sync_updated_at")) v.put("sync_updated_at", System.currentTimeMillis());
        long now = System.currentTimeMillis();
        if (cols.contains("created_at") && !v.containsKey("created_at")) v.put("created_at", now);
        if (cols.contains("updated_at") && !v.containsKey("updated_at")) v.put("updated_at", now);

        if (id > 0) db.update(tabela, v, "id=?", new String[]{String.valueOf(id)});
        else db.insert(tabela, null, v);
        return true;
    }

    private static ContentValues mapaParaValues(Map<String,Object> mapa, Set<String> permitidas) {
        ContentValues out = new ContentValues();
        for (Map.Entry<String,Object> e : mapa.entrySet()) {
            String k = e.getKey();
            if (k == null || !permitidas.contains(k) || "id".equalsIgnoreCase(k)) continue;
            Object v = e.getValue();
            if (v == null) out.putNull(k);
            else if (v instanceof Boolean) out.put(k, ((Boolean) v) ? 1 : 0);
            else if (v instanceof Double || v instanceof Float) out.put(k, ((Number) v).doubleValue());
            else if (v instanceof Number) out.put(k, ((Number) v).longValue());
            else if (v instanceof Timestamp) out.put(k, ((Timestamp) v).toDate().getTime());
            else if (v instanceof byte[]) out.put(k, (byte[]) v);
            else if (v instanceof Map || v instanceof List) { /* estrutura cloud, não é coluna SQLite */ }
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

    private static void registrarMudancaProdutoLan(SQLiteDatabase db, String uuid, String acao) {
        ContentValues v = new ContentValues();
        v.put("produto_uuid", uuid);
        v.put("acao", "DELETE".equalsIgnoreCase(acao) ? "DELETE" : "UPSERT");
        v.put("changed_at", System.currentTimeMillis());
        db.insert("lan_product_changes", null, v);
    }

    private static void registrarDispositivo(DocumentReference empresa, GestaoDbHelper.SyncContext ctx,
                                              FirebaseUser user) throws Exception {
        Map<String,Object> v = new HashMap<>();
        v.put("dispositivo_uuid", ctx.dispositivoUuid);
        v.put("filial_uuid", ctx.filialUuid);
        v.put("nome", ctx.nomeDispositivo == null ? "" : ctx.nomeDispositivo);
        v.put("papel", ctx.papelDispositivo == null ? "" : ctx.papelDispositivo);
        v.put("usuario_uid", user.getUid());
        v.put("last_seen", FieldValue.serverTimestamp());
        Tasks.await(empresa.collection("dispositivos").document(ctx.dispositivoUuid)
                .set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void marcarSincronizado(SQLiteDatabase db, String tabela, long id, long versao) {
        ContentValues v = new ContentValues();
        v.put("sync_status", "CLOUD_SYNCED");
        if (versao > 0) {
            db.update(tabela, v, "id=? AND sync_version=?",
                    new String[]{String.valueOf(id), String.valueOf(versao)});
        } else {
            db.update(tabela, v, "id=?", new String[]{String.valueOf(id)});
        }
    }

    private static Map<String,Object> cursorMap(Cursor c, boolean removerId) {
        Map<String,Object> out = new HashMap<>();
        String[] cols = c.getColumnNames();
        for (int i=0;i<cols.length;i++) {
            String nome = cols[i];
            if (removerId && "id".equalsIgnoreCase(nome)) continue;
            if ("sync_status".equalsIgnoreCase(nome)) continue;
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: out.put(nome, null); break;
                case Cursor.FIELD_TYPE_INTEGER: out.put(nome, c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: out.put(nome, c.getDouble(i)); break;
                case Cursor.FIELD_TYPE_BLOB: out.put(nome, Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)); break;
                default: out.put(nome, c.getString(i));
            }
        }
        return out;
    }

    private static String colunaString(Cursor c, String nome) {
        int i = c.getColumnIndex(nome);
        return i < 0 || c.isNull(i) ? "" : c.getString(i);
    }

    private static long colunaLong(Cursor c, String nome, long padrao) {
        int i = c.getColumnIndex(nome);
        return i < 0 || c.isNull(i) ? padrao : c.getLong(i);
    }

    private static String colecaoEntidade(String entidade) {
        if ("PRODUTO".equals(entidade)) return "produtos";
        if ("CLIENTE".equals(entidade)) return "clientes";
        if ("FORNECEDOR".equals(entidade)) return "fornecedores";
        if ("DESPESA".equals(entidade)) return "despesas";
        return null;
    }

    private static String tabelaEntidade(String entidade) {
        return colecaoEntidade(entidade);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
