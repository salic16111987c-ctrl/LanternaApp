package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Envia para a nuvem o histórico consolidado de vendas do Master por delta LAN. */
public final class TechCellCloudSales {
    private static final String PREF = "techcell_cloud_sales_v1";
    private static final String ROOT = "techcell_empresas";
    private static final Object LOCK = new Object();

    public static class Resultado {
        public boolean ok;
        public int vendas;
        public int eventos;
        public int pendentes;
        public long ultimaSeq;
        public String mensagem = "";
    }

    private static class Mudanca {
        long seq;
        String uuid = "";
        String acao = "UPSERT";
    }

    private TechCellCloudSales() {}

    public static Resultado sincronizar(Context context, int limiteEventos) {
        synchronized (LOCK) {
            Resultado out = new Resultado();
            Context app = context.getApplicationContext();
            GestaoDbHelper helper = new GestaoDbHelper(app);
            try {
                GestaoDbHelper.SyncContext ctx = helper.getSyncContext();
                if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                    throw new IllegalStateException("A sincronização de vendas da nuvem roda somente no Master.");
                }
                if (!ctx.cloudAtiva) {
                    throw new IllegalStateException("A nuvem ainda não foi ativada para esta empresa.");
                }

                FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
                if (user == null) throw new IllegalStateException("Entre na conta Master da nuvem.");

                DocumentReference empresa = TechCellCloudSync.firestore(app)
                        .collection(ROOT).document(ctx.empresaUuid);
                DocumentSnapshot ed = Tasks.await(empresa.get(Source.SERVER));
                if (!ed.exists()) throw new IllegalStateException("Empresa não encontrada na nuvem.");
                String owner = ed.getString("owner_uid");
                if (owner == null || !owner.equals(user.getUid())) {
                    throw new IllegalStateException("A conta conectada não é a conta Master proprietária desta empresa.");
                }

                int limite = Math.max(1, Math.min(500, limiteEventos));
                SharedPreferences p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
                long cursor = Math.max(0, p.getLong("last_sale_seq", 0));
                SQLiteDatabase db = helper.getReadableDatabase();

                LinkedHashMap<String,Mudanca> ultimas = new LinkedHashMap<>();
                long maiorSeq = cursor;
                int eventos = 0;
                Cursor c = db.rawQuery(
                        "SELECT seq,venda_uuid,acao FROM lan_sale_changes WHERE seq>? ORDER BY seq LIMIT " + limite,
                        new String[]{String.valueOf(cursor)});
                try {
                    while (c.moveToNext()) {
                        Mudanca m = new Mudanca();
                        m.seq = c.getLong(0);
                        m.uuid = c.getString(1) == null ? "" : c.getString(1).trim();
                        m.acao = c.getString(2) == null ? "UPSERT" : c.getString(2).trim();
                        maiorSeq = Math.max(maiorSeq, m.seq);
                        eventos++;
                        if (m.uuid.isEmpty()) continue;
                        ultimas.remove(m.uuid);
                        ultimas.put(m.uuid, m);
                    }
                } finally { c.close(); }

                if (eventos == 0) {
                    out.ok = true;
                    out.ultimaSeq = cursor;
                    out.pendentes = 0;
                    out.mensagem = "Vendas já estavam em dia.";
                    registrarSucesso(app, cursor, 0, 0);
                    return out;
                }

                WriteBatch batch = empresa.getFirestore().batch();
                int vendas = 0;
                for (Mudanca m : ultimas.values()) {
                    DocumentReference destino = empresa.collection("vendas").document(m.uuid);
                    if ("DELETE".equalsIgnoreCase(m.acao)) {
                        batch.delete(destino);
                        vendas++;
                        continue;
                    }

                    Cursor v = db.rawQuery("SELECT * FROM vendas WHERE uuid=? LIMIT 1", new String[]{m.uuid});
                    try {
                        if (!v.moveToFirst()) {
                            batch.delete(destino);
                            vendas++;
                            continue;
                        }
                        long vendaId = v.getLong(v.getColumnIndexOrThrow("id"));
                        Map<String,Object> dados = cursorMap(v, true);
                        dados.put("empresa_uuid", ctx.empresaUuid);
                        dados.put("filial_uuid", ctx.filialUuid);
                        dados.put("master_dispositivo_uuid", ctx.dispositivoUuid);
                        dados.put("cloud_received_at", FieldValue.serverTimestamp());
                        dados.put("itens", itens(db, vendaId));
                        batch.set(destino, dados, SetOptions.merge());
                        vendas++;
                    } finally { v.close(); }
                }

                Tasks.await(batch.commit());
                int pendentes = contarDepois(db, maiorSeq);
                p.edit()
                        .putLong("last_sale_seq", maiorSeq)
                        .putLong("last_success", System.currentTimeMillis())
                        .putInt("last_count", vendas)
                        .putInt("pending", pendentes)
                        .putString("last_error", "")
                        .apply();

                out.ok = true;
                out.vendas = vendas;
                out.eventos = eventos;
                out.pendentes = pendentes;
                out.ultimaSeq = maiorSeq;
                out.mensagem = pendentes > 0
                        ? vendas + " venda(s) enviadas; ainda existem eventos para processar."
                        : vendas + " venda(s) sincronizadas; histórico em dia.";
                return out;
            } catch (Throwable e) {
                out.mensagem = TechCellCloudSync.mensagemCloud(e);
                app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString("last_error", out.mensagem == null ? "" : out.mensagem)
                        .apply();
                return out;
            } finally {
                helper.close();
            }
        }
    }

    public static int pendentes(Context context) {
        GestaoDbHelper helper = new GestaoDbHelper(context.getApplicationContext());
        try {
            long seq = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getLong("last_sale_seq", 0);
            return contarDepois(helper.getReadableDatabase(), seq);
        } finally { helper.close(); }
    }

    private static int contarDepois(SQLiteDatabase db, long seq) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM lan_sale_changes WHERE seq>?", new String[]{String.valueOf(seq)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private static List<Map<String,Object>> itens(SQLiteDatabase db, long vendaId) {
        List<Map<String,Object>> out = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT * FROM venda_itens WHERE venda_id=? ORDER BY id", new String[]{String.valueOf(vendaId)});
        try {
            while (c.moveToNext()) out.add(cursorMapItem(c));
        } finally { c.close(); }
        return out;
    }

    private static Map<String,Object> cursorMapItem(Cursor c) {
        Map<String,Object> out = new LinkedHashMap<>();
        String[] cols = c.getColumnNames();
        for (int i=0;i<cols.length;i++) {
            String nome = cols[i];
            if ("id".equalsIgnoreCase(nome) || "venda_id".equalsIgnoreCase(nome) ||
                    "produto_id".equalsIgnoreCase(nome) || "sync_status".equalsIgnoreCase(nome)) continue;
            putCursor(out, nome, c, i);
        }
        return out;
    }

    private static Map<String,Object> cursorMap(Cursor c, boolean removerId) {
        Map<String,Object> out = new LinkedHashMap<>();
        String[] cols = c.getColumnNames();
        for (int i=0;i<cols.length;i++) {
            String nome = cols[i];
            if (removerId && "id".equalsIgnoreCase(nome)) continue;
            if ("sync_status".equalsIgnoreCase(nome)) continue;
            putCursor(out, nome, c, i);
        }
        return out;
    }

    private static void putCursor(Map<String,Object> out, String nome, Cursor c, int i) {
        switch (c.getType(i)) {
            case Cursor.FIELD_TYPE_NULL: out.put(nome, null); break;
            case Cursor.FIELD_TYPE_INTEGER: out.put(nome, c.getLong(i)); break;
            case Cursor.FIELD_TYPE_FLOAT: out.put(nome, c.getDouble(i)); break;
            case Cursor.FIELD_TYPE_BLOB: out.put(nome, Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)); break;
            default: out.put(nome, c.getString(i));
        }
    }

    private static void registrarSucesso(Context context, long seq, int count, int pending) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("last_sale_seq", seq)
                .putLong("last_success", System.currentTimeMillis())
                .putInt("last_count", count)
                .putInt("pending", pending)
                .putString("last_error", "")
                .apply();
    }
}
