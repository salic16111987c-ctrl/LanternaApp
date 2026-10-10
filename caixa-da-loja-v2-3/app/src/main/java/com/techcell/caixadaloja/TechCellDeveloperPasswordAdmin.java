package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Operação administrativa protegida para redefinir a credencial do Master atual. */
public final class TechCellDeveloperPasswordAdmin {
    private static final long TIMEOUT_SECONDS = 25L;
    private static final String REGION = "southamerica-east1";
    private static final String FUNCTION = "techcellDefinirSenhaMaster";

    private TechCellDeveloperPasswordAdmin() {}

    public static void definirSenhaMaster(Context context,
                                           TechCellDeveloperCompanyManager.Dados empresa,
                                           String novaSenha) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (empresa == null || empresa.empresaUuid == null || empresa.empresaUuid.trim().isEmpty())
            throw new IllegalArgumentException("Empresa inválida.");
        if (empresa.ownerUid == null || empresa.ownerUid.trim().isEmpty())
            throw new IllegalStateException("Esta empresa não possui um Master válido vinculado.");
        if (novaSenha == null || novaSenha.length() < 6)
            throw new IllegalArgumentException("A nova senha do Master deve ter pelo menos 6 caracteres.");
        if (novaSenha.length() > 128)
            throw new IllegalArgumentException("A nova senha do Master é muito longa.");

        FirebaseUser dev = TechCellCloudSync.auth(app).getCurrentUser();
        if (dev == null) throw new IllegalStateException("Conta Desenvolvedor não conectada.");

        Map<String,Object> dados = new HashMap<>();
        dados.put("empresa_uuid", empresa.empresaUuid);
        dados.put("nova_senha", novaSenha);

        FirebaseFunctions funcoes = FirebaseFunctions.getInstance(TechCellCloudSync.app(app), REGION);
        Tasks.await(funcoes.getHttpsCallable(FUNCTION).call(dados), TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
