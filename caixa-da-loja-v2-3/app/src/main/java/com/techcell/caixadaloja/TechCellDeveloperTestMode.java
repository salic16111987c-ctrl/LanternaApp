package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Modo de teste/impersonação do Desenvolvedor.
 *
 * A conta Firebase continua sendo a conta Desenvolvedor. O usuário Master/Gerente/Caixa
 * é apenas simulado no app. Antes do teste o banco local real é guardado; durante o teste
 * trabalhamos em uma cópia temporária. Ao sair, o banco real é restaurado.
 */
public final class TechCellDeveloperTestMode {
    private static final String PREF = "techcell_developer_test_v1";
    private static final String DB_NAME = "gestao_techcell.db";
    private static final String BACKUP_NAME = "gestao_techcell_before_devtest.db";

    public static class Estado {
        public boolean ativo;
        public String empresaUuid = "";
        public String filialUuid = "";
        public String empresaNome = "";
        public String uidAlvo = "";
        public String nomeAlvo = "";
        public String emailAlvo = "";
        public String perfil = "CAIXA";
        public boolean proprietario;
        public Set<String> permissoes = new LinkedHashSet<>();
    }

    private TechCellDeveloperTestMode() {}

    public static boolean ativo(Context context) {
        return prefs(context).getBoolean("ativo", false);
    }

    public static Estado estado(Context context) {
        SharedPreferences p = prefs(context);
        Estado e = new Estado();
        e.ativo = p.getBoolean("ativo", false);
        e.empresaUuid = p.getString("empresa_uuid", "");
        e.filialUuid = p.getString("filial_uuid", "");
        e.empresaNome = p.getString("empresa_nome", "");
        e.uidAlvo = p.getString("uid_alvo", "");
        e.nomeAlvo = p.getString("nome_alvo", "");
        e.emailAlvo = p.getString("email_alvo", "");
        e.perfil = p.getString("perfil", "CAIXA");
        e.proprietario = p.getBoolean("proprietario", false);
        String csv = p.getString("permissoes_csv", "");
        if (csv != null && !csv.trim().isEmpty()) {
            for (String x : csv.split(",")) if (!x.trim().isEmpty()) e.permissoes.add(x.trim().toUpperCase(Locale.ROOT));
        }
        if (e.permissoes.isEmpty()) e.permissoes = TechCellPermissions.padrao(e.perfil);
        return e;
    }

    public static void iniciar(Context context, TechCellDeveloper.Empresa empresa,
                               TechCellDeveloper.UsuarioTeste usuario) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (empresa == null || empresa.empresaUuid == null || empresa.empresaUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Empresa inválida para teste.");
        }
        if (usuario == null || usuario.uid == null || usuario.uid.trim().isEmpty()) {
            throw new IllegalArgumentException("Usuário inválido para teste.");
        }
        if (!usuario.ativo) throw new IllegalStateException("Este usuário está bloqueado na empresa.");

        // Captura a empresa do banco real antes de entrar no teste. Se for a mesma empresa,
        // a cópia temporária começa exatamente com os dados locais atuais, inclusive vendas do dia.
        String empresaLocal = "";
        try {
            GestaoDbHelper local = new GestaoDbHelper(app);
            try {
                GestaoDbHelper.SyncContext lc = local.getSyncContext();
                empresaLocal = lc.empresaUuid == null ? "" : lc.empresaUuid.trim();
            } finally { local.close(); }
        } catch (Throwable ignored) {}

        TechCellBackgroundSync.parar(app);
        garantirBackupBancoReal(app);

        // Descarta um teste anterior. Quando a empresa testada é a mesma empresa deste aparelho,
        // clona o banco real para o teste; caso contrário começa vazio e carrega snapshot da nuvem.
        app.deleteDatabase(DB_NAME);
        boolean mesmaEmpresaLocal = !empresaLocal.isEmpty()
                && empresa.empresaUuid.equalsIgnoreCase(empresaLocal);
        boolean usandoCloneLocal = mesmaEmpresaLocal && clonarBackupParaTeste(app);

        String perfil = normalizarPerfil(usuario.proprietario ? "MASTER" : usuario.perfil);
        Set<String> permissoes = usuario.proprietario
                ? TechCellPermissions.padrao("MASTER")
                : new LinkedHashSet<>(usuario.permissoes == null ? TechCellPermissions.padrao(perfil) : usuario.permissoes);
        StringBuilder csv = new StringBuilder();
        for (String x : permissoes) { if (csv.length() > 0) csv.append(','); csv.append(x); }
        String filial = empresa.filialUuid == null || empresa.filialUuid.trim().isEmpty()
                ? empresa.empresaUuid : empresa.filialUuid.trim();

        prefs(app).edit()
                .putBoolean("ativo", true)
                .putBoolean("clone_local", usandoCloneLocal)
                .putString("empresa_uuid", empresa.empresaUuid)
                .putString("filial_uuid", filial)
                .putString("empresa_nome", empresa.nome == null ? "" : empresa.nome)
                .putString("uid_alvo", usuario.uid)
                .putString("nome_alvo", usuario.nome == null ? "" : usuario.nome)
                .putString("email_alvo", usuario.email == null ? "" : usuario.email)
                .putString("perfil", perfil)
                .putBoolean("proprietario", usuario.proprietario)
                .putString("permissoes_csv", csv.toString())
                .apply();

        GestaoDbHelper db = new GestaoDbHelper(app);
        try {
            ContentValues v = new ContentValues();
            v.put("empresa_uuid", empresa.empresaUuid);
            v.put("filial_uuid", filial);
            // ADMIN identifica o acesso de suporte/teste; o perfil visual vem da sessão simulada.
            v.put("papel_dispositivo", "ADMIN");
            v.put("nome_dispositivo", usandoCloneLocal ? "Teste Desenvolvedor • cópia local" : "Teste Desenvolvedor");
            v.put("master_tipo", "NUVEM");
            v.put("master_host", "");
            v.put("master_device_uuid", "DEV_TEST");
            v.put("master_name", "Modo de teste");
            v.put("master_auth_token", "");
            v.put("configurado", 1);
            v.put("cloud_ativa", 1);
            v.put("updated_at", System.currentTimeMillis());
            db.getWritableDatabase().update("sync_context", v, "id=1", null);
        } finally { db.close(); }

        // Para a mesma empresa deste aparelho, a cópia local já é a fonte mais atual e contém
        // inclusive vendas ainda não enviadas à nuvem. Para outra empresa, usa snapshot cloud.
        if (!usandoCloneLocal) {
            try {
                TechCellDeveloperSnapshotSync.Resultado r = TechCellDeveloperSnapshotSync.carregar(app);
                if (r == null || !r.ok) {
                    String erro = r == null ? "Falha ao carregar a base da empresa." : r.erro;
                    throw new IllegalStateException(erro == null || erro.trim().isEmpty()
                            ? "Falha ao carregar a base da empresa para o modo de teste." : erro);
                }
            } catch (Throwable e) {
                sair(app);
                if (e instanceof Exception) throw (Exception)e;
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /** Sai do personagem de teste e volta à mesma conta Desenvolvedor. */
    public static void sair(Context context) {
        Context app = context.getApplicationContext();
        TechCellBackgroundSync.parar(app);
        try { app.deleteDatabase(DB_NAME); } catch (Throwable ignored) {}
        restaurarBancoReal(app);
        SharedPreferences p = prefs(app);
        boolean backupPronto = p.getBoolean("backup_pronto", false);
        boolean tinhaOriginal = p.getBoolean("tinha_original", false);
        p.edit().clear().putBoolean("backup_pronto", backupPronto).putBoolean("tinha_original", tinhaOriginal).apply();
        // O backup já foi restaurado; remove também os marcadores.
        p.edit().clear().apply();
    }

    public static TechCellAccess.Sessao sessaoSimulada(Context context) {
        Estado e = estado(context);
        TechCellAccess.Sessao s = new TechCellAccess.Sessao();
        if (!e.ativo) return s;
        s.valida = true;
        s.ativo = true;
        s.proprietario = e.proprietario;
        s.uid = e.uidAlvo;
        s.nome = e.nomeAlvo;
        s.email = e.emailAlvo;
        s.empresaUuid = e.empresaUuid;
        s.perfil = perfilEnum(e.perfil);
        s.permissoes = new LinkedHashSet<>(e.permissoes);
        return s;
    }

    /** Qualquer alteração feita no teste fica apenas no SQLite temporário. */
    public static boolean redeSomenteLeitura(Context context) {
        return ativo(context);
    }

    private static void garantirBackupBancoReal(Context app) throws Exception {
        SharedPreferences p = prefs(app);
        if (p.getBoolean("backup_pronto", false)) return;

        File origem = app.getDatabasePath(DB_NAME);
        File backup = new File(app.getFilesDir(), BACKUP_NAME);
        boolean existe = origem.exists();
        if (existe) {
            checkpoint(origem);
            copiar(origem, backup);
        } else if (backup.exists()) {
            backup.delete();
        }
        p.edit().putBoolean("backup_pronto", true).putBoolean("tinha_original", existe).apply();
    }

    private static boolean clonarBackupParaTeste(Context app) {
        File backup = new File(app.getFilesDir(), BACKUP_NAME);
        if (!backup.exists()) return false;
        File destino = app.getDatabasePath(DB_NAME);
        try {
            File parent = destino.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            copiar(backup, destino);
            return destino.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void restaurarBancoReal(Context app) {
        SharedPreferences p = prefs(app);
        if (!p.getBoolean("backup_pronto", false)) return;
        File backup = new File(app.getFilesDir(), BACKUP_NAME);
        File destino = app.getDatabasePath(DB_NAME);
        try {
            if (p.getBoolean("tinha_original", false) && backup.exists()) {
                File parent = destino.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
                copiar(backup, destino);
            }
        } catch (Throwable ignored) {
            return;
        }
        try { if (backup.exists()) backup.delete(); } catch (Throwable ignored) {}
    }

    private static void checkpoint(File banco) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(banco.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.execSQL("PRAGMA wal_checkpoint(FULL)");
        } catch (Throwable ignored) {
        } finally { if (db != null) try { db.close(); } catch (Throwable ignored) {} }
    }

    private static void copiar(File origem, File destino) throws Exception {
        File parent = destino.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream in = new FileInputStream(origem); FileOutputStream out = new FileOutputStream(destino, false)) {
            byte[] buf = new byte[64 * 1024]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static String normalizarPerfil(String valor) {
        String v = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if ("MASTER".equals(v) || "ADMINISTRADOR".equals(v) || "PROPRIETARIO".equals(v) || "PROPRIETÁRIO".equals(v)) return "MASTER";
        if ("GERENTE".equals(v)) return "GERENTE";
        return "CAIXA";
    }

    private static TechCellAccess.Perfil perfilEnum(String perfil) {
        if ("MASTER".equalsIgnoreCase(perfil)) return TechCellAccess.Perfil.MASTER;
        if ("GERENTE".equalsIgnoreCase(perfil)) return TechCellAccess.Perfil.GERENTE;
        if ("CAIXA".equalsIgnoreCase(perfil)) return TechCellAccess.Perfil.CAIXA;
        return TechCellAccess.Perfil.NENHUM;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
