package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Leitura e gravação das permissões personalizadas pelo Master da empresa. */
public final class TechCellPermissionAdmin {
    private static final String ROOT = "techcell_empresas";
    private static final long TIMEOUT = 15L;
    private TechCellPermissionAdmin() {}

    public static Set<String> carregar(Context context, String uid, String perfil) throws Exception {
        Context app = context.getApplicationContext();
        GestaoDbHelper db = new GestaoDbHelper(app);
        String empresa;
        try { empresa = db.getSyncContext().empresaUuid; }
        finally { db.close(); }
        if (empresa == null || empresa.trim().isEmpty()) throw new IllegalStateException("Empresa não configurada.");
        DocumentSnapshot d = Tasks.await(TechCellCloudSync.firestore(app).collection(ROOT).document(empresa)
                .collection("usuarios").document(uid).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!d.exists()) throw new IllegalStateException("Usuário não encontrado na empresa.");
        return TechCellPermissions.ler(d.get("permissoes"), perfil);
    }

    public static void salvar(Context context, String uid, String perfil, Set<String> permissoes) throws Exception {
        Context app = context.getApplicationContext();
        if (!TechCellAccess.podeAdministrar(app)) throw new IllegalStateException("Somente o Master pode alterar permissões.");
        FirebaseUser autor = TechCellCloudSync.auth(app).getCurrentUser();
        if (autor == null) throw new IllegalStateException("Conta Master não conectada.");

        GestaoDbHelper db = new GestaoDbHelper(app);
        String empresa;
        try { empresa = db.getSyncContext().empresaUuid; }
        finally { db.close(); }
        if (empresa == null || empresa.trim().isEmpty()) throw new IllegalStateException("Empresa não configurada.");

        DocumentReference ref = TechCellCloudSync.firestore(app).collection(ROOT).document(empresa)
                .collection("usuarios").document(uid);
        DocumentSnapshot atual = Tasks.await(ref.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!atual.exists()) throw new IllegalStateException("Usuário não encontrado na empresa.");
        if (Boolean.TRUE.equals(atual.get("proprietario")) || uid.equals(autor.getUid())) {
            throw new IllegalStateException("A conta Master proprietária mantém acesso total.");
        }

        Map<String,Object> v = new HashMap<>();
        v.put("permissoes", TechCellPermissions.lista(permissoes));
        v.put("permissoes_modelo", perfil == null ? "" : perfil);
        v.put("permissions_updated_by_uid", autor.getUid());
        v.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(v, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
    }
}
