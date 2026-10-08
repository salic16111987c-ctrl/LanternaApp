package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Identifica a conta global do Desenvolvedor sem misturar com o papel MASTER da empresa. */
public final class TechCellDeveloperAccess {
    private static final String ADMINS = "techcell_platform_admins";
    private static final String PREF = "techcell_developer_v1";
    private static final long TIMEOUT_SECONDS = 10L;

    // Bootstrap único do primeiro Desenvolvedor da plataforma.
    // Exige autenticação Firebase + UID exato + e-mail exato.
    private static final String BOOTSTRAP_UID = "JMBDrlStQdNLD1km8hOrJmcJqXd2";
    private static final String BOOTSTRAP_EMAIL = "salic1611@hotmail.com";

    public static class Estado {
        public boolean conhecido;
        public boolean desenvolvedor;
        public boolean ativo;
        public String uid = "";
        public String nome = "";
        public long atualizadoEm;
    }

    private TechCellDeveloperAccess() {}

    public static Estado local(Context context) {
        Estado e = new Estado();
        FirebaseUser u;
        try { u = TechCellCloudSync.auth(context).getCurrentUser(); }
        catch (Throwable ex) { u = null; }
        if (u == null) return e;

        SharedPreferences p = prefs(context);
        String uid = p.getString("uid", "");
        if (!u.getUid().equals(uid)) return e;
        e.uid = uid;
        e.conhecido = p.getBoolean("conhecido", false);
        e.ativo = p.getBoolean("ativo", false);
        e.desenvolvedor = e.conhecido && e.ativo;
        e.nome = p.getString("nome", "");
        e.atualizadoEm = p.getLong("updated_at", 0L);
        return e;
    }

    public static boolean ehDesenvolvedor(Context context) {
        return local(context).desenvolvedor;
    }

    public static Estado atualizar(Context context) throws Exception {
        Context app = context.getApplicationContext();
        FirebaseUser u = TechCellCloudSync.auth(app).getCurrentUser();
        if (u == null) {
            limpar(app);
            throw new IllegalStateException("Entre na conta antes de validar o acesso de Desenvolvedor.");
        }

        DocumentReference ref = TechCellCloudSync.firestore(app).collection(ADMINS).document(u.getUid());
        DocumentSnapshot d = Tasks.await(ref.get(Source.SERVER), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!d.exists() && podeFazerBootstrap(u)) {
            Map<String,Object> dados = new HashMap<>();
            dados.put("ativo", true);
            dados.put("papel", "DEVELOPER");
            dados.put("nome", "Cilas Souza");
            dados.put("bootstrap_uid", u.getUid());
            dados.put("bootstrap_email", u.getEmail() == null ? "" : u.getEmail());
            dados.put("created_at", FieldValue.serverTimestamp());
            dados.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(ref.set(dados, SetOptions.merge()), TIMEOUT_SECONDS, TimeUnit.SECONDS);
            d = Tasks.await(ref.get(Source.SERVER), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        Estado e = new Estado();
        e.conhecido = true;
        e.uid = u.getUid();
        e.ativo = d.exists() && booleano(d.get("ativo"), true);
        String papel = d.exists() ? texto(d.get("papel")) : "";
        boolean papelOk = papel.isEmpty() || "DEVELOPER".equalsIgnoreCase(papel)
                || "DESENVOLVEDOR".equalsIgnoreCase(papel)
                || "PLATFORM_ADMIN".equalsIgnoreCase(papel);
        e.desenvolvedor = e.ativo && papelOk && d.exists();
        e.nome = d.exists() ? texto(d.get("nome")) : "";
        if (e.nome.isEmpty() && e.desenvolvedor) e.nome = "Desenvolvedor";
        e.atualizadoEm = System.currentTimeMillis();

        prefs(app).edit()
                .putString("uid", e.uid)
                .putBoolean("conhecido", true)
                .putBoolean("ativo", e.desenvolvedor)
                .putString("nome", e.nome)
                .putLong("updated_at", e.atualizadoEm)
                .apply();
        return e;
    }

    private static boolean podeFazerBootstrap(FirebaseUser u) {
        if (u == null || !BOOTSTRAP_UID.equals(u.getUid())) return false;
        String email = u.getEmail();
        return email != null && BOOTSTRAP_EMAIL.equalsIgnoreCase(email.trim());
    }

    public static void exigir(Context context) throws Exception {
        Estado e = atualizar(context);
        if (!e.desenvolvedor) throw new IllegalStateException("Esta conta não possui acesso de Desenvolvedor da plataforma.");
    }

    public static void limpar(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static boolean booleano(Object v, boolean padrao) {
        return v instanceof Boolean ? (Boolean) v : padrao;
    }
    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
