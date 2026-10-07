package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Primeira camada de nuvem nativa do Gestão Tech Cell.
 *
 * Regras desta fase:
 * - o SQLite local continua sendo a base de operação;
 * - somente o Master envia cadastros para a nuvem;
 * - nenhuma coleção antiga do projeto Firebase é alterada;
 * - todos os dados novos ficam sob techcell_empresas/{empresa_uuid};
 * - a ativação só é gravada localmente depois que o servidor confirma a empresa
 *   e a carga inicial de cadastros termina sem erro.
 */
public final class TechCellCloudSync {
    private static final String APP_NAME = "TECHCELL_CLOUD";
    private static final String PROJECT_ID = "caixa-da-loja-5dd34";
    private static final String API_KEY = "AIzaSyAcMqWWeaEKfdjIST0NSwkXWWsbst6iY2k";
    private static final String APPLICATION_ID = "1:243340178302:web:09e0bc0265edc2d2cab92f";
    private static final String STORAGE_BUCKET = "caixa-da-loja-5dd34.firebasestorage.app";
    private static final String ROOT = "techcell_empresas";
    private static final String PREF = "techcell_cloud_v1";
    private static final int BATCH_MAX = 350;
    private static final long READ_TIMEOUT_SECONDS = 6L;
    private static final long WRITE_TIMEOUT_SECONDS = 20L;

    public static class Resultado {
        public boolean ok;
        public int produtos;
        public int clientes;
        public int fornecedores;
        public int despesas;
        public int total;
        public long finalizadoEm;
        public String mensagem = "";
    }

    public static class Estado {
        public boolean autenticado;
        public String email = "";
        public boolean cloudAtiva;
        public long ultimoSucesso;
        public String ultimoErro = "";
        public int ultimaCargaCadastros;
    }

    private static class RefLinha {
        long id;
        long versao;
        RefLinha(long id, long versao) { this.id = id; this.versao = versao; }
    }

    private TechCellCloudSync() {}

    public static synchronized FirebaseApp app(Context context) {
        try {
            return FirebaseApp.getInstance(APP_NAME);
        } catch (IllegalStateException ignored) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApiKey(API_KEY)
                    .setApplicationId(APPLICATION_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build();
            return FirebaseApp.initializeApp(context.getApplicationContext(), options, APP_NAME);
        }
    }

    public static FirebaseAuth auth(Context context) {
        return FirebaseAuth.getInstance(app(context));
    }

    public static FirebaseFirestore firestore(Context context) {
        return FirebaseFirestore.getInstance(app(context));
    }

    public static boolean estaAutenticado(Context context) {
        try { return auth(context).getCurrentUser() != null; }
        catch (Throwable ignored) { return false; }
    }

    public static Estado estado(Context context) {
        Estado e = new Estado();
        try {
            FirebaseUser u = auth(context).getCurrentUser();
            e.autenticado = u != null;
            e.email = u == null || u.getEmail() == null ? "" : u.getEmail();
        } catch (Throwable ignored) {}
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        try { e.cloudAtiva = db.getSyncContext().cloudAtiva; }
        finally { db.close(); }
        SharedPreferences p = prefs(context);
        e.ultimoSucesso = p.getLong("last_success", 0);
        e.ultimoErro = p.getString("last_error", "");
        e.ultimaCargaCadastros = p.getInt("last_cadastros", 0);
        return e;
    }

    public static Resultado testarRegistrarEmpresa(Context context) {
        Resultado out = new Resultado();
        GestaoDbHelper helper = new GestaoDbHelper(context.getApplicationContext());
        try {
            GestaoDbHelper.SyncContext ctx = exigirMaster(helper);
            FirebaseUser user = exigirUsuario(context);
            FirebaseFirestore fs = firestore(context);
            DocumentReference empresa = fs.collection(ROOT).document(ctx.empresaUuid);

            DocumentSnapshot atual = lerDocumentoComCache(empresa);
            if (atual.exists()) {
                String owner = atual.getString("owner_uid");
                if (owner != null && !owner.trim().isEmpty() && !owner.equals(user.getUid())) {
                    throw new IllegalStateException("Esta empresa já está vinculada a outra conta da nuvem.");
                }
            }

            Map<String,Object> dados = new HashMap<>();
            dados.put("schema_version", 1);
            dados.put("empresa_uuid", ctx.empresaUuid);
            dados.put("filial_uuid", ctx.filialUuid);
            dados.put("owner_uid", user.getUid());
            dados.put("owner_email", user.getEmail() == null ? "" : user.getEmail());
            dados.put("source", "TECHCELL_PDV");
            dados.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(empresa.set(dados, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String,Object> dispositivo = new HashMap<>();
            dispositivo.put("dispositivo_uuid", ctx.dispositivoUuid);
            dispositivo.put("filial_uuid", ctx.filialUuid);
            dispositivo.put("nome", ctx.nomeDispositivo == null ? "" : ctx.nomeDispositivo);
            dispositivo.put("papel", ctx.papelDispositivo == null ? "" : ctx.papelDispositivo);
            dispositivo.put("app", "Tech Cell PDV TESTE");
            dispositivo.put("last_seen", FieldValue.serverTimestamp());
            Tasks.await(empresa.collection("dispositivos").document(ctx.dispositivoUuid)
                    .set(dispositivo, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            DocumentSnapshot confirmado = lerDocumentoComCache(empresa);
            if (!confirmado.exists()) throw new IllegalStateException("A empresa não foi confirmada pela nuvem.");
            String ownerConfirmado = confirmado.getString("owner_uid");
            if (ownerConfirmado == null || !ownerConfirmado.equals(user.getUid())) {
                throw new IllegalStateException("A confirmação de propriedade da empresa falhou.");
            }

            out.ok = true;
            out.finalizadoEm = System.currentTimeMillis();
            out.mensagem = "Conexão confirmada. Empresa e Master registrados na nuvem.";
            registrarSucesso(context, 0);
            return out;
        } catch (Throwable e) {
            out.mensagem = mensagemCloud(e);
            registrarErro(context, out.mensagem);
            return out;
        } finally {
            helper.close();
        }
    }

    public static Resultado enviarCargaInicialCadastros(Context context) {
        Resultado out = new Resultado();
        GestaoDbHelper helper = new GestaoDbHelper(context.getApplicationContext());
        try {
            GestaoDbHelper.SyncContext ctx = exigirMaster(helper);
            FirebaseUser user = exigirUsuario(context);
            FirebaseFirestore fs = firestore(context);
            DocumentReference empresa = fs.collection(ROOT).document(ctx.empresaUuid);
            validarEmpresaServidor(empresa, user);

            SQLiteDatabase db = helper.getWritableDatabase();
            enviarConfigEmpresa(db, empresa);
            out.produtos = enviarTabela(db, empresa, "produtos", "produtos", false);
            out.clientes = enviarTabela(db, empresa, "clientes", "clientes", false);
            out.fornecedores = enviarTabela(db, empresa, "fornecedores", "fornecedores", false);
            out.despesas = enviarTabela(db, empresa, "despesas", "despesas", false);
            out.total = out.produtos + out.clientes + out.fornecedores + out.despesas;

            Map<String,Object> meta = new HashMap<>();
            meta.put("cadastros_carga_inicial_em", FieldValue.serverTimestamp());
            meta.put("cadastros_carga_inicial_total", out.total);
            meta.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(empresa.set(meta, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            ContentValues ativa = new ContentValues();
            ativa.put("cloud_ativa", 1);
            ativa.put("updated_at", System.currentTimeMillis());
            db.update("sync_context", ativa, "id=1", null);

            out.ok = true;
            out.finalizadoEm = System.currentTimeMillis();
            out.mensagem = "Carga inicial de cadastros concluída.";
            registrarSucesso(context, out.total);
            return out;
        } catch (Throwable e) {
            out.mensagem = mensagemCloud(e);
            registrarErro(context, out.mensagem);
            return out;
        } finally {
            helper.close();
        }
    }

    public static Resultado sincronizarCadastrosPendentes(Context context) {
        Resultado out = new Resultado();
        GestaoDbHelper helper = new GestaoDbHelper(context.getApplicationContext());
        try {
            GestaoDbHelper.SyncContext ctx = exigirMaster(helper);
            if (!ctx.cloudAtiva) throw new IllegalStateException("Faça primeiro a carga inicial da nuvem.");
            FirebaseUser user = exigirUsuario(context);
            FirebaseFirestore fs = firestore(context);
            DocumentReference empresa = fs.collection(ROOT).document(ctx.empresaUuid);
            validarEmpresaServidor(empresa, user);

            SQLiteDatabase db = helper.getWritableDatabase();
            enviarConfigEmpresa(db, empresa);
            out.produtos = enviarTabela(db, empresa, "produtos", "produtos", true);
            out.clientes = enviarTabela(db, empresa, "clientes", "clientes", true);
            out.fornecedores = enviarTabela(db, empresa, "fornecedores", "fornecedores", true);
            out.despesas = enviarTabela(db, empresa, "despesas", "despesas", true);
            sincronizarExclusoes(db, empresa);
            out.total = out.produtos + out.clientes + out.fornecedores + out.despesas;

            Map<String,Object> dispositivo = new HashMap<>();
            dispositivo.put("last_seen", FieldValue.serverTimestamp());
            dispositivo.put("papel", ctx.papelDispositivo);
            dispositivo.put("nome", ctx.nomeDispositivo);
            Tasks.await(empresa.collection("dispositivos").document(ctx.dispositivoUuid)
                    .set(dispositivo, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            out.ok = true;
            out.finalizadoEm = System.currentTimeMillis();
            out.mensagem = out.total == 0 ? "Cadastros já estavam em dia." : "Cadastros pendentes enviados.";
            registrarSucesso(context, out.total);
            return out;
        } catch (Throwable e) {
            out.mensagem = mensagemCloud(e);
            registrarErro(context, out.mensagem);
            return out;
        } finally {
            helper.close();
        }
    }

    private static GestaoDbHelper.SyncContext exigirMaster(GestaoDbHelper db) {
        GestaoDbHelper.SyncContext ctx = db.getSyncContext();
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            throw new IllegalStateException("A configuração da nuvem deve ser feita no aparelho Master.");
        }
        if (ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
            throw new IllegalStateException("Empresa local sem identificação UUID.");
        }
        return ctx;
    }

    private static FirebaseUser exigirUsuario(Context context) {
        FirebaseUser user = auth(context).getCurrentUser();
        if (user == null) throw new IllegalStateException("Entre na conta Firebase antes de continuar.");
        return user;
    }

    private static void validarEmpresaServidor(DocumentReference empresa, FirebaseUser user) throws Exception {
        DocumentSnapshot d = lerDocumentoComCache(empresa);
        if (!d.exists()) throw new IllegalStateException("Registre a empresa na nuvem antes da carga inicial.");
        String owner = d.getString("owner_uid");
        if (owner == null || !owner.equals(user.getUid())) {
            throw new IllegalStateException("A conta atual não é proprietária desta empresa na nuvem.");
        }
    }

    private static DocumentSnapshot lerDocumentoComCache(DocumentReference ref) throws Exception {
        Throwable servidor = null;
        try {
            return Tasks.await(ref.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable e) {
            servidor = e;
        }
        try {
            DocumentSnapshot cache = Tasks.await(ref.get(Source.CACHE), 2L, TimeUnit.SECONDS);
            if (cache != null && cache.exists()) return cache;
        } catch (Throwable ignored) {}
        if (servidor instanceof Exception) throw (Exception) servidor;
        throw new IllegalStateException("Não foi possível consultar a nuvem.", servidor);
    }

    private static void enviarConfigEmpresa(SQLiteDatabase db, DocumentReference empresa) throws Exception {
        Cursor c = db.rawQuery("SELECT * FROM empresa_config WHERE id=1 LIMIT 1", null);
        try {
            if (!c.moveToFirst()) return;
            Map<String,Object> config = cursorMap(c, true);
            Map<String,Object> update = new HashMap<>();
            update.put("config", config);
            update.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(empresa.set(update, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            marcarLinhaSincronizada(db, "empresa_config", c.getLong(c.getColumnIndexOrThrow("id")),
                    colunaLong(c, "sync_version", 0));
        } finally { c.close(); }
    }

    private static int enviarTabela(SQLiteDatabase db, DocumentReference empresa,
                                    String tabela, String colecao, boolean somentePendentes) throws Exception {
        String where = somentePendentes ? " WHERE sync_status='PENDENTE'" : "";
        Cursor c = db.rawQuery("SELECT * FROM " + tabela + where + " ORDER BY id", null);
        int enviados = 0;
        try {
            WriteBatch batch = null;
            List<RefLinha> refs = new ArrayList<>();
            int noBatch = 0;
            while (c.moveToNext()) {
                String uuid = colunaString(c, "uuid");
                if (uuid.trim().isEmpty()) continue;
                if (batch == null) batch = firestoreFrom(empresa).batch();

                Map<String,Object> row = cursorMap(c, true);
                row.put("cloud_received_at", FieldValue.serverTimestamp());
                batch.set(empresa.collection(colecao).document(uuid), row, SetOptions.merge());
                refs.add(new RefLinha(c.getLong(c.getColumnIndexOrThrow("id")),
                        colunaLong(c, "sync_version", 0)));
                noBatch++;

                if (noBatch >= BATCH_MAX) {
                    Tasks.await(batch.commit(), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    for (RefLinha r : refs) marcarLinhaSincronizada(db, tabela, r.id, r.versao);
                    enviados += refs.size();
                    batch = null;
                    refs.clear();
                    noBatch = 0;
                }
            }
            if (batch != null && !refs.isEmpty()) {
                Tasks.await(batch.commit(), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                for (RefLinha r : refs) marcarLinhaSincronizada(db, tabela, r.id, r.versao);
                enviados += refs.size();
            }
        } finally { c.close(); }
        return enviados;
    }

    private static FirebaseFirestore firestoreFrom(DocumentReference ref) {
        return ref.getFirestore();
    }

    private static void sincronizarExclusoes(SQLiteDatabase db, DocumentReference empresa) throws Exception {
        Cursor c = db.rawQuery(
                "SELECT id,entidade,entidade_uuid FROM sync_tombstones " +
                        "WHERE sync_status='PENDENTE' ORDER BY deleted_at LIMIT 300", null);
        List<Long> ids = new ArrayList<>();
        WriteBatch batch = empresa.getFirestore().batch();
        int n = 0;
        try {
            while (c.moveToNext()) {
                String entidade = c.getString(1);
                String uuid = c.getString(2);
                String colecao = colecaoEntidade(entidade);
                if (colecao == null || uuid == null || uuid.trim().isEmpty()) continue;
                batch.delete(empresa.collection(colecao).document(uuid));
                ids.add(c.getLong(0));
                n++;
            }
        } finally { c.close(); }
        if (n == 0) return;
        Tasks.await(batch.commit(), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ContentValues v = new ContentValues();
        v.put("sync_status", "CLOUD_SYNCED");
        for (Long id : ids) db.update("sync_tombstones", v, "id=?", new String[]{String.valueOf(id)});
    }

    private static String colecaoEntidade(String entidade) {
        if (entidade == null) return null;
        String x = entidade.trim().toUpperCase();
        if ("PRODUTO".equals(x)) return "produtos";
        if ("CLIENTE".equals(x)) return "clientes";
        if ("FORNECEDOR".equals(x)) return "fornecedores";
        if ("DESPESA".equals(x)) return "despesas";
        return null;
    }

    private static void marcarLinhaSincronizada(SQLiteDatabase db, String tabela, long id, long versao) {
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
        for (int i = 0; i < cols.length; i++) {
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

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static void registrarSucesso(Context context, int cadastros) {
        SharedPreferences.Editor e = prefs(context).edit()
                .putLong("last_success", System.currentTimeMillis())
                .putString("last_error", "");
        if (cadastros > 0) e.putInt("last_cadastros", cadastros);
        e.apply();
    }

    private static void registrarErro(Context context, String erro) {
        prefs(context).edit().putString("last_error", erro == null ? "" : erro).apply();
    }

    public static String mensagemCloud(Throwable e) {
        Throwable x = e;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        String m = x.getMessage();
        if (m == null || m.trim().isEmpty()) m = x.getClass().getSimpleName();
        String lower = m.toLowerCase();
        if (lower.contains("permission_denied") || lower.contains("permission denied") ||
                lower.contains("missing or insufficient permissions")) {
            return "O Firebase bloqueou a nova estrutura Tech Cell. Precisamos publicar as regras da nuvem antes de continuar.";
        }
        if (lower.contains("network") || lower.contains("unavailable") || lower.contains("timeout") ||
                lower.contains("failed to get document from server")) {
            return "A nuvem demorou para responder. O app continua funcionando localmente. Verifique a internet e tente novamente. Detalhe: " + m;
        }
        return m.trim();
    }
}
