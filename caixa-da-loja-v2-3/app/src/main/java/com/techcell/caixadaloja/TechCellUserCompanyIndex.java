package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Índice seguro de vínculo conta -> empresa usado no primeiro login pela internet.
 *
 * O usuário lê somente o próprio índice. Quem grava o índice é o proprietário da
 * empresa. Depois de descobrir o empresa_uuid, o app ainda valida o documento real
 * usuarios/{uid}; portanto o índice nunca concede acesso sozinho.
 */
public final class TechCellUserCompanyIndex {
    private static final String ROOT = "techcell_empresas";
    private static final String INDEX = "techcell_user_empresas";
    private static final String PREF = "techcell_user_company_index_v1";
    private static final long TIMEOUT = 15L;
    private static final long MIGRATION_INTERVAL_MS = 10000L; // curto durante os testes remotos

    public static class Vinculo {
        public String empresaUuid = "";
        public String filialUuid = "";
        public String perfil = "CAIXA";
        public String nome = "";
        public boolean proprietario;
    }

    private TechCellUserCompanyIndex() {}

    /** Descobre a empresa sem exigir que o aparelho já tenha sido pareado na LAN. */
    public static Vinculo descobrir(Context context, FirebaseUser user) throws Exception {
        if (user == null || user.getUid() == null || user.getUid().trim().isEmpty()) return null;
        Context app = context.getApplicationContext();
        QuerySnapshot qs = Tasks.await(
                TechCellCloudSync.firestore(app)
                        .collection(INDEX).document(user.getUid())
                        .collection("empresas").get(Source.SERVER),
                TIMEOUT, TimeUnit.SECONDS);
        if (qs == null || qs.isEmpty()) return null;
        if (qs.size() > 1) {
            throw new IllegalStateException("Esta conta está vinculada a mais de uma empresa. A seleção da empresa será exibida em uma próxima etapa.");
        }
        DocumentSnapshot d = qs.getDocuments().get(0);
        Vinculo v = new Vinculo();
        v.empresaUuid = texto(d.get("empresa_uuid"));
        if (v.empresaUuid.isEmpty()) v.empresaUuid = d.getId();
        v.filialUuid = texto(d.get("filial_uuid"));
        if (v.filialUuid.isEmpty()) v.filialUuid = v.empresaUuid;
        v.perfil = texto(d.get("perfil"));
        if (v.perfil.isEmpty()) v.perfil = "CAIXA";
        v.nome = texto(d.get("nome"));
        v.proprietario = Boolean.TRUE.equals(d.get("proprietario"));
        return v;
    }

    /**
     * O proprietário mantém o índice dos usuários existentes. Isso também migra
     * contas antigas criadas antes do login remoto sem LAN.
     */
    public static int garantirIndicesSeNecessario(Context context) throws Exception {
        Context app = context.getApplicationContext();
        GestaoDbHelper db = new GestaoDbHelper(app);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }
        if (!ctx.configurado || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) return 0;

        FirebaseUser atual = TechCellCloudSync.auth(app).getCurrentUser();
        if (atual == null) return 0;

        SharedPreferences pref = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String key = "last_" + ctx.empresaUuid;
        long agora = System.currentTimeMillis();
        if (agora - pref.getLong(key, 0L) < MIGRATION_INTERVAL_MS) return 0;

        DocumentReference empresa = TechCellCloudSync.firestore(app).collection(ROOT).document(ctx.empresaUuid);
        DocumentSnapshot ed = Tasks.await(empresa.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!ed.exists()) return 0;
        String owner = texto(ed.get("owner_uid"));
        if (!atual.getUid().equals(owner)) return 0;

        int total = 0;
        QuerySnapshot usuarios = Tasks.await(empresa.collection("usuarios").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (usuarios != null) {
            for (QueryDocumentSnapshot u : usuarios) {
                String uid = texto(u.get("uid"));
                if (uid.isEmpty()) uid = u.getId();
                if (uid.isEmpty()) continue;
                gravar(app, ctx, uid,
                        texto(u.get("nome")), texto(u.get("email")), texto(u.get("perfil")),
                        Boolean.TRUE.equals(u.get("proprietario")) || uid.equals(owner));
                total++;
            }
        }

        // Garante o proprietário mesmo se o documento usuarios/{owner} ainda não existir.
        if (!owner.isEmpty()) {
            DocumentReference ownerRef = empresa.collection("usuarios").document(owner);
            DocumentSnapshot od = null;
            try { od = Tasks.await(ownerRef.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS); }
            catch (Throwable ignored) {}
            String nome = od != null && od.exists() ? texto(od.get("nome")) : "Master";
            String email = od != null && od.exists() ? texto(od.get("email")) : (atual.getEmail() == null ? "" : atual.getEmail());
            gravar(app, ctx, owner, nome, email, "MASTER", true);
        }

        pref.edit().putLong(key, agora).apply();
        return total;
    }

    private static void gravar(Context app, GestaoDbHelper.SyncContext ctx, String uid,
                               String nome, String email, String perfil, boolean proprietario) throws Exception {
        Map<String,Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("empresa_uuid", ctx.empresaUuid);
        m.put("filial_uuid", ctx.filialUuid == null || ctx.filialUuid.trim().isEmpty() ? ctx.empresaUuid : ctx.filialUuid);
        m.put("nome", nome == null ? "" : nome);
        m.put("email", email == null ? "" : email);
        m.put("perfil", perfil == null || perfil.trim().isEmpty() ? (proprietario ? "MASTER" : "CAIXA") : perfil);
        m.put("proprietario", proprietario);
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(TechCellCloudSync.firestore(app)
                        .collection(INDEX).document(uid)
                        .collection("empresas").document(ctx.empresaUuid)
                        .set(m, SetOptions.merge()),
                TIMEOUT, TimeUnit.SECONDS);
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
