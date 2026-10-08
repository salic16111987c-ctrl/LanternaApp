package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Sessão local + permissões granulares vindas da empresa na nuvem. */
public final class TechCellAccess {
    private static final String ROOT = "techcell_empresas";
    private static final String PREF = "techcell_access_v1";
    private static final long READ_TIMEOUT_SECONDS = 12L;

    public enum Perfil { MASTER, GERENTE, CAIXA, NENHUM }

    public static class Sessao {
        public boolean valida;
        public boolean ativo;
        public String uid = "";
        public String nome = "";
        public String email = "";
        public String empresaUuid = "";
        public Perfil perfil = Perfil.NENHUM;
        public Set<String> permissoes = new LinkedHashSet<>();
    }

    private TechCellAccess() {}

    public static boolean controleAtivo(Context context) {
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        try { return db.getSyncContext().cloudAtiva; }
        finally { db.close(); }
    }

    public static Sessao sessao(Context context) {
        Sessao s = new Sessao();
        Context app = context.getApplicationContext();
        GestaoDbHelper db = new GestaoDbHelper(app);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }

        if (!ctx.cloudAtiva) {
            s.valida = true;
            s.ativo = true;
            s.nome = "Desenvolvimento";
            s.perfil = Perfil.MASTER;
            s.empresaUuid = ctx.empresaUuid == null ? "" : ctx.empresaUuid;
            s.permissoes = TechCellPermissions.padrao("MASTER");
            return s;
        }

        FirebaseUser atual;
        try { atual = TechCellCloudSync.auth(app).getCurrentUser(); }
        catch (Throwable e) { atual = null; }
        if (atual == null) return s;

        SharedPreferences p = prefs(app);
        String uid = p.getString("uid", "");
        String empresa = p.getString("empresa_uuid", "");
        String empresaAtual = ctx.empresaUuid == null ? "" : ctx.empresaUuid;
        if (!atual.getUid().equals(uid) || !empresaAtual.equals(empresa)) return s;

        s.uid = uid;
        s.empresaUuid = empresa;
        s.nome = p.getString("nome", "");
        s.email = p.getString("email", atual.getEmail() == null ? "" : atual.getEmail());
        s.ativo = p.getBoolean("ativo", false);
        s.perfil = normalizar(p.getString("perfil", ""));
        String csv = p.getString("permissoes_csv", "");
        if (csv == null || csv.trim().isEmpty()) {
            s.permissoes = TechCellPermissions.padrao(s.perfil.name());
        } else {
            LinkedHashSet<String> ps = new LinkedHashSet<>();
            for (String x : csv.split(",")) if (!x.trim().isEmpty()) ps.add(x.trim().toUpperCase(Locale.ROOT));
            s.permissoes = ps;
        }
        s.valida = s.ativo && s.perfil != Perfil.NENHUM;
        return s;
    }

    public static boolean temSessaoValida(Context context) { return sessao(context).valida; }
    public static Perfil perfilAtual(Context context) { return sessao(context).perfil; }

    public static Sessao atualizarDaNuvem(Context context) throws Exception {
        Context app = context.getApplicationContext();
        FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
        if (user == null) throw new IllegalStateException("Entre com seu e-mail e senha.");

        GestaoDbHelper db = new GestaoDbHelper(app);
        GestaoDbHelper.SyncContext ctx;
        try {
            ctx = db.getSyncContext();

            // Primeiro acesso remoto: antes de exigir LAN, tenta localizar a empresa
            // pelo índice privado da própria conta. Isso funciona para Caixa/Gerente.
            if (!ctx.configurado || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
                boolean configurou = false;
                Throwable erroIndice = null;
                try {
                    configurou = TechCellCloudFirstLogin.configurarSePossivel(app, user, db);
                } catch (Throwable e) {
                    erroIndice = e;
                }
                if (!configurou) {
                    // Mantém compatibilidade com a conta proprietária já existente.
                    if (descobrirEmpresaProprietario(app, user, db)) configurou = true;
                }
                if (configurou) ctx = db.getSyncContext();
                else if (erroIndice != null) {
                    String m = TechCellCloudSync.mensagemCloud(erroIndice);
                    throw new IllegalStateException(
                            "Não foi possível localizar a empresa desta conta pela nuvem. " +
                            (m == null ? "" : m), erroIndice);
                }
            }

            if (!ctx.configurado) {
                throw new IllegalStateException("Esta conta ainda não possui um vínculo de empresa disponível na nuvem.");
            }
            if (ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
                throw new IllegalStateException("Este aparelho ainda não possui uma empresa vinculada.");
            }

            String papelAparelho = ctx.papelDispositivo == null ? "" : ctx.papelDispositivo.trim();
            boolean remotoNuvem = "ADMIN".equalsIgnoreCase(papelAparelho)
                    || "CONSULTA".equalsIgnoreCase(papelAparelho)
                    || TechCellCloudFirstLogin.aceitaSemPareamento(ctx);
            if (!"MASTER".equalsIgnoreCase(papelAparelho) && !remotoNuvem) {
                String masterId = ctx.masterDeviceUuid == null ? "" : ctx.masterDeviceUuid.trim();
                if (masterId.isEmpty()) {
                    throw new IllegalStateException("Este aparelho ainda não está vinculado ao Master. Faça o pareamento inicial ou entre pela nuvem.");
                }
            }

            DocumentReference empresa = TechCellCloudSync.firestore(app).collection(ROOT).document(ctx.empresaUuid);
            DocumentSnapshot emp = Tasks.await(empresa.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!emp.exists()) throw new IllegalStateException("Esta empresa ainda não foi registrada na Nuvem Tech Cell.");

            String owner = texto(emp.get("owner_uid"));
            Sessao s = new Sessao();
            s.uid = user.getUid();
            s.email = user.getEmail() == null ? "" : user.getEmail();
            s.empresaUuid = ctx.empresaUuid;

            if (user.getUid().equals(owner)) {
                s.perfil = Perfil.MASTER;
                s.ativo = true;
                s.permissoes = TechCellPermissions.padrao("MASTER");
                DocumentSnapshot ud = null;
                try {
                    ud = Tasks.await(empresa.collection("usuarios").document(user.getUid()).get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Throwable ignored) {}
                if (ud != null && ud.exists()) {
                    s.nome = texto(ud.get("nome"));
                    Object ativo = ud.get("ativo");
                    if (ativo instanceof Boolean) s.ativo = (Boolean) ativo;
                }
                if (s.nome.isEmpty()) s.nome = "Master";
            } else {
                DocumentSnapshot ud = Tasks.await(empresa.collection("usuarios").document(user.getUid()).get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!ud.exists()) throw new IllegalStateException("Esta conta não está vinculada a esta empresa.");
                Object ativo = ud.get("ativo");
                s.ativo = !(ativo instanceof Boolean) || (Boolean) ativo;
                s.perfil = normalizar(texto(ud.get("perfil")));
                s.nome = texto(ud.get("nome"));
                s.permissoes = TechCellPermissions.ler(ud.get("permissoes"), s.perfil.name());
                if (s.nome.isEmpty()) s.nome = s.email;
            }

            if (!s.ativo) throw new IllegalStateException("Esta conta está bloqueada pelo Master.");
            if (s.perfil == Perfil.NENHUM) throw new IllegalStateException("Esta conta está sem nível de acesso válido.");

            ContentValues cloud = new ContentValues();
            cloud.put("cloud_ativa", 1);
            cloud.put("updated_at", System.currentTimeMillis());
            db.getWritableDatabase().update("sync_context", cloud, "id=1", null);

            salvar(app, s);
            s.valida = true;
            return s;
        } finally { db.close(); }
    }

    private static boolean descobrirEmpresaProprietario(Context app, FirebaseUser user, GestaoDbHelper db) throws Exception {
        QuerySnapshot qs;
        try {
            qs = Tasks.await(TechCellCloudSync.firestore(app).collection(ROOT)
                    .whereEqualTo("owner_uid", user.getUid()).limit(2).get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable e) { return false; }
        if (qs == null || qs.isEmpty()) return false;
        if (qs.size() > 1) throw new IllegalStateException("Esta conta administra mais de uma empresa. A seleção de empresa será habilitada no painel do Desenvolvedor.");

        DocumentSnapshot emp = qs.getDocuments().get(0);
        String empresaUuid = emp.getId();
        String filialUuid = texto(emp.get("filial_uuid"));
        if (filialUuid.isEmpty()) filialUuid = empresaUuid;

        SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("empresa_uuid", empresaUuid); v.put("filial_uuid", filialUuid);
        v.put("papel_dispositivo", "ADMIN"); v.put("nome_dispositivo", "Acesso remoto");
        v.put("master_tipo", "NUVEM"); v.put("master_host", ""); v.put("master_device_uuid", "");
        v.put("master_name", "Master da loja"); v.put("master_auth_token", "");
        v.put("configurado", 1); v.put("cloud_ativa", 1); v.put("updated_at", System.currentTimeMillis());
        sql.update("sync_context", v, "id=1", null);
        return true;
    }

    public static void encerrar(Context context) {
        try { TechCellCloudSync.auth(context).signOut(); } catch (Throwable ignored) {}
        prefs(context).edit().clear().apply();
    }

    public static boolean temPermissao(Context c, String permissao) {
        Sessao s = sessao(c);
        if (!s.valida) return false;
        if (s.perfil == Perfil.MASTER) return true;
        return TechCellPermissions.contem(s.permissoes, permissao);
    }

    public static boolean podeVender(Context c) { return temPermissao(c, TechCellPermissions.VENDER); }
    public static boolean podeClientes(Context c) { return temPermissao(c, TechCellPermissions.CLIENTES); }
    public static boolean podeProdutos(Context c) { return temPermissao(c, TechCellPermissions.PRODUTOS); }
    public static boolean podeEditarProdutos(Context c) { return temPermissao(c, TechCellPermissions.PRODUTOS_EDITAR); }
    public static boolean podeVerCusto(Context c) { return temPermissao(c, TechCellPermissions.PRODUTOS_CUSTO); }
    public static boolean podeEstoque(Context c) { return temPermissao(c, TechCellPermissions.ESTOQUE); }
    public static boolean podeResumoDia(Context c) { return temPermissao(c, TechCellPermissions.RESUMO_DIA); }
    public static boolean podeFinanceiro(Context c) { return temPermissao(c, TechCellPermissions.FINANCEIRO); }
    public static boolean podeRelatorios(Context c) { return temPermissao(c, TechCellPermissions.RELATORIOS); }
    public static boolean podeFornecedores(Context c) { return temPermissao(c, TechCellPermissions.FORNECEDORES); }
    public static boolean podeHistorico(Context c) { return temPermissao(c, TechCellPermissions.HISTORICO); }
    public static boolean podeResumoFinanceiro(Context c) { return temPermissao(c, TechCellPermissions.LUCRO_CUSTO); }
    public static boolean podeAdministrar(Context c) { return perfilAtual(c) == Perfil.MASTER; }

    public static String perfilExibicao(Perfil p) {
        if (p == Perfil.MASTER) return "Master";
        if (p == Perfil.GERENTE) return "Gerente";
        if (p == Perfil.CAIXA) return "Caixa";
        return "Sem acesso";
    }

    private static Perfil normalizar(String valor) {
        String p = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if ("MASTER".equals(p) || "ADMINISTRADOR".equals(p) || "PROPRIETARIO".equals(p) || "PROPRIETÁRIO".equals(p)) return Perfil.MASTER;
        if ("GERENTE".equals(p)) return Perfil.GERENTE;
        if ("CAIXA".equals(p)) return Perfil.CAIXA;
        return Perfil.NENHUM;
    }

    private static void salvar(Context context, Sessao s) {
        StringBuilder csv = new StringBuilder();
        for (String x : s.permissoes) { if (csv.length() > 0) csv.append(','); csv.append(x); }
        prefs(context).edit()
                .putString("uid", s.uid).putString("empresa_uuid", s.empresaUuid)
                .putString("nome", s.nome).putString("email", s.email)
                .putString("perfil", s.perfil.name()).putBoolean("ativo", s.ativo)
                .putString("permissoes_csv", csv.toString())
                .putLong("validado_em", System.currentTimeMillis()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
