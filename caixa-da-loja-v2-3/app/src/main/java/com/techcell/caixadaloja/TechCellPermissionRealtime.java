package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Escuta em tempo real o documento do usuário atual na empresa.
 * Quando o Master altera perfil, status ou permissões, o Caixa/Gerente
 * recebe a mudança imediatamente e atualiza apenas o cache local da sessão.
 */
public final class TechCellPermissionRealtime {
    private static final String ROOT = "techcell_empresas";
    private static final String PREF = "techcell_access_v1";
    private static final AtomicLong VERSAO = new AtomicLong(0L);

    private static ListenerRegistration listener;
    private static String chaveAtual = "";

    private TechCellPermissionRealtime() {}

    public static long versao() { return VERSAO.get(); }

    public static synchronized void garantir(Context context) {
        Context app = context.getApplicationContext();
        try {
            if (TechCellDeveloperTestMode.ativo(app) || TechCellDeveloperAccess.ehDesenvolvedor(app)) {
                parar();
                return;
            }

            FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
            if (user == null) { parar(); return; }

            GestaoDbHelper helper = new GestaoDbHelper(app);
            GestaoDbHelper.SyncContext ctx;
            try { ctx = helper.getSyncContext(); }
            finally { helper.close(); }

            if (!ctx.configurado || !ctx.cloudAtiva || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
                parar();
                return;
            }

            SharedPreferences p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String uidSessao = p.getString("uid", "");
            String empresaSessao = p.getString("empresa_uuid", "");
            boolean proprietario = p.getBoolean("proprietario", false);
            String perfil = p.getString("perfil", "");

            if (!user.getUid().equals(uidSessao) || !ctx.empresaUuid.equals(empresaSessao)
                    || proprietario || "MASTER".equalsIgnoreCase(perfil)) {
                parar();
                return;
            }

            String chave = ctx.empresaUuid + "|" + user.getUid();
            if (listener != null && chave.equals(chaveAtual)) return;
            parar();
            chaveAtual = chave;

            listener = TechCellCloudSync.firestore(app)
                    .collection(ROOT).document(ctx.empresaUuid)
                    .collection("usuarios").document(user.getUid())
                    .addSnapshotListener((doc, erro) -> {
                        if (erro != null || doc == null) return;
                        aplicar(app, user, ctx.empresaUuid, doc);
                    });
        } catch (Throwable ignored) {
            // O listener é complementar. A operação local nunca deve parar por falha de internet.
        }
    }

    public static synchronized void parar() {
        if (listener != null) {
            try { listener.remove(); } catch (Throwable ignored) {}
        }
        listener = null;
        chaveAtual = "";
    }

    private static void aplicar(Context app, FirebaseUser user, String empresaUuid, DocumentSnapshot doc) {
        SharedPreferences p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!user.getUid().equals(p.getString("uid", ""))) return;
        if (!empresaUuid.equals(p.getString("empresa_uuid", ""))) return;
        if (p.getBoolean("proprietario", false)) return;

        if (!doc.exists()) {
            boolean mudou = p.getBoolean("ativo", true);
            p.edit().putBoolean("ativo", false).putLong("validado_em", System.currentTimeMillis()).apply();
            if (mudou) VERSAO.incrementAndGet();
            return;
        }

        String perfil = normalizarPerfil(texto(doc.get("perfil")));
        boolean ativo = !(doc.get("ativo") instanceof Boolean) || Boolean.TRUE.equals(doc.get("ativo"));
        String nome = texto(doc.get("nome"));
        if (nome.isEmpty()) nome = user.getEmail() == null ? "" : user.getEmail();
        Set<String> permissoes = TechCellPermissions.ler(doc.get("permissoes"), perfil);
        String csv = csv(permissoes);

        String antigoPerfil = p.getString("perfil", "");
        boolean antigoAtivo = p.getBoolean("ativo", false);
        String antigoNome = p.getString("nome", "");
        String antigoCsv = p.getString("permissoes_csv", "");

        boolean mudou = !perfil.equalsIgnoreCase(antigoPerfil)
                || ativo != antigoAtivo
                || !nome.equals(antigoNome)
                || !mesmasPermissoes(csv, antigoCsv);

        p.edit()
                .putString("perfil", perfil)
                .putBoolean("ativo", ativo)
                .putString("nome", nome)
                .putString("permissoes_csv", csv)
                .putLong("validado_em", System.currentTimeMillis())
                .apply();

        if (mudou) VERSAO.incrementAndGet();
    }

    private static String normalizarPerfil(String valor) {
        String v = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if ("GERENTE".equals(v)) return "GERENTE";
        if ("MASTER".equals(v) || "ADMINISTRADOR".equals(v)
                || "PROPRIETARIO".equals(v) || "PROPRIETÁRIO".equals(v)) return "MASTER";
        return "CAIXA";
    }

    private static String csv(Set<String> permissoes) {
        StringBuilder sb = new StringBuilder();
        if (permissoes != null) {
            for (String x : permissoes) {
                if (x == null || x.trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(x.trim().toUpperCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static boolean mesmasPermissoes(String a, String b) {
        return set(a).equals(set(b));
    }

    private static Set<String> set(String csv) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String x : csv.split(",")) if (!x.trim().isEmpty()) out.add(x.trim().toUpperCase(Locale.ROOT));
        return out;
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
