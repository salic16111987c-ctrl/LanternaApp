package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Envia alterações de produtos feitas por Caixa/Gerente com permissão. */
public final class TechCellCloudProductPush {
    private static final String ROOT = "techcell_empresas";
    private static final long TIMEOUT = 20L;
    private TechCellCloudProductPush() {}

    public static int sincronizar(Context context) throws Exception {
        Context app = context.getApplicationContext();
        if (!TechCellAccess.podeEditarProdutos(app)) return 0;
        if (TechCellAccess.perfilAtual(app) == TechCellAccess.Perfil.MASTER) return 0; // owner já usa o sync principal
        FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
        if (user == null) return 0;

        GestaoDbHelper helper = new GestaoDbHelper(app);
        try {
            GestaoDbHelper.SyncContext ctx = helper.getSyncContext();
            if (!ctx.configurado || !ctx.cloudAtiva || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) return 0;
            SQLiteDatabase db = helper.getWritableDatabase();
            DocumentReference empresa = TechCellCloudSync.firestore(app).collection(ROOT).document(ctx.empresaUuid);
            Cursor c = db.rawQuery("SELECT * FROM produtos WHERE sync_status='PENDENTE' ORDER BY id LIMIT 200", null);
            int total = 0;
            try {
                while (c.moveToNext()) {
                    int uuidCol = c.getColumnIndex("uuid");
                    if (uuidCol < 0 || c.isNull(uuidCol)) continue;
                    String uuid = c.getString(uuidCol).trim();
                    if (uuid.isEmpty()) continue;
                    long id = c.getLong(c.getColumnIndexOrThrow("id"));
                    long versao = colLong(c, "sync_version", 0L);
                    Map<String,Object> row = cursorMap(c);
                    row.put("empresa_uuid", ctx.empresaUuid);
                    row.put("filial_uuid", ctx.filialUuid);
                    row.put("cloud_source_uid", user.getUid());
                    row.put("cloud_source_device_uuid", ctx.dispositivoUuid);
                    row.put("cloud_received_at", FieldValue.serverTimestamp());
                    Tasks.await(empresa.collection("produtos").document(uuid).set(row, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);

                    ContentValues done = new ContentValues();
                    done.put("sync_status", "CLOUD_SYNCED");
                    done.put("sync_updated_at", System.currentTimeMillis());
                    if (versao > 0) db.update("produtos", done, "id=? AND sync_version=?", new String[]{String.valueOf(id), String.valueOf(versao)});
                    else db.update("produtos", done, "id=?", new String[]{String.valueOf(id)});
                    total++;
                }
            } finally { c.close(); }
            return total;
        } finally { helper.close(); }
    }

    private static Map<String,Object> cursorMap(Cursor c) {
        Map<String,Object> out = new HashMap<>();
        String[] cols = c.getColumnNames();
        for (int i=0;i<cols.length;i++) {
            String k = cols[i];
            if ("id".equalsIgnoreCase(k) || "sync_status".equalsIgnoreCase(k)) continue;
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: out.put(k, null); break;
                case Cursor.FIELD_TYPE_INTEGER: out.put(k, c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: out.put(k, c.getDouble(i)); break;
                case Cursor.FIELD_TYPE_BLOB: out.put(k, Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)); break;
                default: out.put(k, c.getString(i));
            }
        }
        return out;
    }

    private static long colLong(Cursor c, String nome, long padrao) {
        int i = c.getColumnIndex(nome);
        return i < 0 || c.isNull(i) ? padrao : c.getLong(i);
    }
}
