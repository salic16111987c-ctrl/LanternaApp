package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Modo de teste/impersonação do Desenvolvedor.
 *
 * A autenticação Firebase continua sendo a conta Desenvolvedor. A identidade simulada
 * existe apenas no app para reproduzir menus/permissões. Os dados são carregados para
 * um SQLite separado por empresa e alterações locais desse modo nunca são enviadas à nuvem.
 */
public final class TechCellDeveloperTestMode {
    private static final String PREF = "techcell_developer_test_v1";
    private static final String DB_PREFIX = "gestao_techcell_devtest_";

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

    public static String nomeBanco(Context context) {
        Estado e = estado(context);
        if (!e.ativo || e.empresaUuid == null || e.empresaUuid.trim().isEmpty()) return "gestao_techcell.db";
        return nomeBancoEmpresa(e.empresaUuid);
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

        String perfil = normalizarPerfil(usuario.proprietario ? "MASTER" : usuario.perfil);
        Set<String> permissoes = usuario.proprietario
                ? TechCellPermissions.padrao("MASTER")
                : new LinkedHashSet<>(usuario.permissoes == null ? TechCellPermissions.padrao(perfil) : usuario.permissoes);
        StringBuilder csv = new StringBuilder();
        for (String x : permissoes) { if (csv.length() > 0) csv.append(','); csv.append(x); }

        String filial = empresa.filialUuid == null || empresa.filialUuid.trim().isEmpty()
                ? empresa.empresaUuid : empresa.filialUuid.trim();
        String dbName = nomeBancoEmpresa(empresa.empresaUuid);

        // Ativa primeiro para que GestaoDbHelper abra o banco de teste, nunca o banco real da loja.
        prefs(app).edit()
                .putBoolean("ativo", true)
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

        // Cada entrada começa com snapshot limpo da nuvem para não reaproveitar alterações de um teste anterior.
        app.deleteDatabase(dbName);
        app.getSharedPreferences("techcell_cloud_realtime_devtest_v1", Context.MODE_PRIVATE).edit().clear().apply();

        GestaoDbHelper db = new GestaoDbHelper(app);
        try {
            ContentValues v = new ContentValues();
            v.put("empresa_uuid", empresa.empresaUuid);
            v.put("filial_uuid", filial);
            v.put("papel_dispositivo", "ADMIN");
            v.put("nome_dispositivo", "Teste Desenvolvedor");
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

        TechCellBackgroundSync.parar(app);
        TechCellCloudRealtimeSync.Resultado r = TechCellCloudRealtimeSync.sincronizar(app);
        if (r == null || !r.ok) {
            String erro = r == null ? "Falha ao carregar a base da empresa." : r.erro;
            sair(app);
            throw new IllegalStateException(erro == null || erro.trim().isEmpty()
                    ? "Falha ao carregar a base da empresa para o modo de teste." : erro);
        }
    }

    public static void sair(Context context) {
        Context app = context.getApplicationContext();
        Estado e = estado(app);
        String dbName = e.empresaUuid == null || e.empresaUuid.trim().isEmpty() ? "" : nomeBancoEmpresa(e.empresaUuid);
        prefs(app).edit().clear().apply();
        app.getSharedPreferences("techcell_cloud_realtime_devtest_v1", Context.MODE_PRIVATE).edit().clear().apply();
        if (!dbName.isEmpty()) app.deleteDatabase(dbName);
        TechCellBackgroundSync.parar(app);
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

    public static boolean redeSomenteLeitura(Context context) {
        return ativo(context);
    }

    private static String nomeBancoEmpresa(String empresaUuid) {
        String safe = empresaUuid == null ? "empresa" : empresaUuid.replaceAll("[^A-Za-z0-9_-]", "");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        if (safe.isEmpty()) safe = "empresa";
        return DB_PREFIX + safe + ".db";
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
