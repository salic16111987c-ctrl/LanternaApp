package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

/** Configura um aparelho novo usando somente o vínculo da conta na nuvem. */
public final class TechCellCloudFirstLogin {
    private TechCellCloudFirstLogin() {}

    public static boolean configurarSePossivel(Context context, FirebaseUser user, GestaoDbHelper db) throws Exception {
        TechCellUserCompanyIndex.Vinculo vinculo = TechCellUserCompanyIndex.descobrir(context, user);
        if (vinculo == null || vinculo.empresaUuid == null || vinculo.empresaUuid.trim().isEmpty()) return false;

        String perfil = vinculo.perfil == null ? "" : vinculo.perfil.trim().toUpperCase(Locale.ROOT);
        boolean admin = vinculo.proprietario || "MASTER".equals(perfil) || "ADMINISTRADOR".equals(perfil);

        SQLiteDatabase sql = db.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("empresa_uuid", vinculo.empresaUuid);
        v.put("filial_uuid", vinculo.filialUuid == null || vinculo.filialUuid.trim().isEmpty() ? vinculo.empresaUuid : vinculo.filialUuid);
        v.put("papel_dispositivo", admin ? "ADMIN" : "CAIXA");
        v.put("nome_dispositivo", admin ? "Acesso remoto" : "Caixa remoto");
        v.put("master_tipo", "NUVEM");
        v.put("master_host", "");
        v.put("master_device_uuid", "");
        v.put("master_name", "Master da loja");
        v.put("master_auth_token", "");
        v.put("configurado", 1);
        v.put("cloud_ativa", 1);
        v.put("updated_at", System.currentTimeMillis());
        sql.update("sync_context", v, "id=1", null);
        return true;
    }

    public static boolean aceitaSemPareamento(GestaoDbHelper.SyncContext ctx) {
        return ctx != null && "NUVEM".equalsIgnoreCase(ctx.masterTipo);
    }
}
