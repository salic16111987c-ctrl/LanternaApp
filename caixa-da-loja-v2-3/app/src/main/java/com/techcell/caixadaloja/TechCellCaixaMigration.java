package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Migra uma unica vez o historico dos Caixas antigos para dentro da empresa Tech Cell.
 * Nao apaga as colecoes antigas. A operacao e idempotente e pode ser repetida com seguranca.
 */
public final class TechCellCaixaMigration {
    private static final long TIMEOUT_SECONDS = 20L;
    private static final String ROOT = "techcell_empresas";
    private static final String MOVIMENTOS = "caixa_movimentos";
    private static final String FECHAMENTOS = "caixa_fechamentos";
    private static final String META = "caixa_meta";
    private static final String MARKER = "migracao_legacy_v1";

    private TechCellCaixaMigration() {}

    public static void iniciar(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try { migrarSeNecessario(app); }
            catch (Throwable ignored) {
                // A migracao sera tentada novamente na proxima abertura.
                // Nunca bloqueia o Caixa nem remove dados antigos.
            }
        }, "TechCell-Caixa-Migration").start();
    }

    private static void migrarSeNecessario(Context app) throws Exception {
        TechCellAccess.Sessao sessao = TechCellAccess.sessao(app);
        if (!sessao.valida || sessao.empresaUuid == null || sessao.empresaUuid.trim().isEmpty()) return;
        if (sessao.perfil != TechCellAccess.Perfil.MASTER) return;

        FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
        if (user == null || !ehContaLegada(user.getEmail())) return;

        FirebaseFirestore fs = TechCellCloudSync.firestore(app);
        DocumentReference empresa = fs.collection(ROOT).document(sessao.empresaUuid);
        DocumentReference marker = empresa.collection(META).document(MARKER);

        DocumentSnapshot meta = await(marker.get());
        if (meta.exists() && Boolean.TRUE.equals(meta.getBoolean("concluida"))) return;

        Map<String, Map<String, Object>> movimentos = new LinkedHashMap<>();
        Map<String, Map<String, Object>> fechamentos = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> despesasPorMes = new LinkedHashMap<>();

        // 1) Caixa 2.7.1: users/{uid} -> stores/{storeId}/...
        String storeId = "";
        try {
            DocumentSnapshot perfil = await(fs.collection("users").document(user.getUid()).get());
            storeId = texto(perfil.get("storeId"));
        } catch (Throwable ignored) {}

        if (!storeId.isEmpty()) {
            try {
                QuerySnapshot antigos = await(fs.collection("stores").document(storeId).collection("movements").get());
                for (DocumentSnapshot d : antigos.getDocuments()) {
                    String data = texto(d.get("date"));
                    if (data.isEmpty()) data = d.getId();
                    Map<String, Object> v = new HashMap<>();
                    v.put("empresa_uuid", sessao.empresaUuid);
                    v.put("data", data);
                    v.put("dinheiro", numero(d.get("cash")));
                    v.put("cartao", numero(d.get("card")));
                    double total = d.get("total") == null
                            ? numero(d.get("cash")) + numero(d.get("card"))
                            : numero(d.get("total"));
                    v.put("total", total);
                    v.put("migrado_de", "CAIXA_2_7_1");
                    movimentos.put(data, v);
                }
            } catch (Throwable ignored) {}

            try {
                QuerySnapshot antigos = await(fs.collection("stores").document(storeId).collection("expenses").get());
                for (DocumentSnapshot d : antigos.getDocuments()) {
                    String mes = texto(d.get("monthRef"));
                    if (mes.isEmpty()) {
                        String data = texto(d.get("date"));
                        if (data.length() >= 7) mes = data.substring(0, 7);
                    }
                    if (mes.isEmpty()) continue;
                    Map<String, Object> e = new HashMap<>();
                    e.put("descricao", texto(d.get("description")));
                    e.put("valor", numero(d.get("amount")));
                    e.put("data", texto(d.get("date")));
                    despesasPorMes.computeIfAbsent(mes, k -> new ArrayList<>()).add(e);
                }
            } catch (Throwable ignored) {}

            try {
                QuerySnapshot antigos = await(fs.collection("stores").document(storeId).collection("closings").get());
                for (DocumentSnapshot d : antigos.getDocuments()) {
                    String mes = texto(d.get("monthRef"));
                    if (mes.isEmpty()) mes = d.getId();
                    Map<String, Object> v = new HashMap<>();
                    v.put("empresa_uuid", sessao.empresaUuid);
                    v.put("mes", mes);
                    v.put("lucro", numero(d.get("profit")));
                    v.put("despesasTotal", numero(d.get("expensesTotal")));
                    v.put("resultadoLiquido", d.get("netProfit") == null
                            ? numero(d.get("profit")) - numero(d.get("expensesTotal"))
                            : numero(d.get("netProfit")));
                    v.put("migrado_de", "CAIXA_2_7_1");
                    fechamentos.put(mes, v);
                }
            } catch (Throwable ignored) {}
        }

        // 2) Caixa embutido mais novo: colecoes top-level. Ele tem prioridade
        // sobre o 2.7.1 quando existir o mesmo dia/mes.
        try {
            QuerySnapshot atuais = await(fs.collection("movimentos").get());
            for (DocumentSnapshot d : atuais.getDocuments()) {
                String data = texto(d.get("data"));
                if (data.isEmpty()) data = d.getId();
                Map<String, Object> v = new HashMap<>(d.getData());
                v.put("empresa_uuid", sessao.empresaUuid);
                v.put("data", data);
                v.put("migrado_de", "CAIXA_EMBUTIDO_LEGADO");
                movimentos.put(data, v);
            }
        } catch (Throwable ignored) {}

        try {
            QuerySnapshot atuais = await(fs.collection("fechamentos").get());
            for (DocumentSnapshot d : atuais.getDocuments()) {
                String mes = texto(d.get("mes"));
                if (mes.isEmpty()) mes = d.getId();
                Map<String, Object> v = new HashMap<>(d.getData());
                v.put("empresa_uuid", sessao.empresaUuid);
                v.put("mes", mes);
                v.put("migrado_de", "CAIXA_EMBUTIDO_LEGADO");
                fechamentos.put(mes, v);
            }
        } catch (Throwable ignored) {}

        // Preserva despesas do 2.7.1 quando ainda nao houver lista equivalente.
        for (Map.Entry<String, List<Map<String, Object>>> e : despesasPorMes.entrySet()) {
            Map<String, Object> f = fechamentos.computeIfAbsent(e.getKey(), mes -> {
                Map<String, Object> novo = new HashMap<>();
                novo.put("empresa_uuid", sessao.empresaUuid);
                novo.put("mes", mes);
                novo.put("lucro", 0d);
                novo.put("despesasTotal", 0d);
                novo.put("resultadoLiquido", 0d);
                novo.put("migrado_de", "CAIXA_2_7_1");
                return novo;
            });
            if (!f.containsKey("despesas")) f.put("despesas", e.getValue());
            if (numero(f.get("despesasTotal")) == 0d) {
                double total = 0d;
                for (Map<String, Object> despesa : e.getValue()) total += numero(despesa.get("valor"));
                f.put("despesasTotal", total);
                f.put("resultadoLiquido", numero(f.get("lucro")) - total);
            }
        }

        for (Map.Entry<String, Map<String, Object>> e : movimentos.entrySet()) {
            await(empresa.collection(MOVIMENTOS).document(e.getKey()).set(e.getValue(), SetOptions.merge()));
        }
        for (Map.Entry<String, Map<String, Object>> e : fechamentos.entrySet()) {
            await(empresa.collection(FECHAMENTOS).document(e.getKey()).set(e.getValue(), SetOptions.merge()));
        }

        Map<String, Object> fim = new HashMap<>();
        fim.put("concluida", true);
        fim.put("empresa_uuid", sessao.empresaUuid);
        fim.put("movimentos_migrados", movimentos.size());
        fim.put("fechamentos_migrados", fechamentos.size());
        fim.put("legacy_store_id", storeId);
        fim.put("executada_por_uid", user.getUid());
        fim.put("executada_em_ms", System.currentTimeMillis());
        await(marker.set(fim, SetOptions.merge()));
    }

    private static boolean ehContaLegada(String email) {
        if (email == null) return false;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return "techcellacs1@gmail.com".equals(e)
                || "techellacs1@gmail.com".equals(e)
                || "acsolar1611@gmail.com".equals(e);
    }

    private static String texto(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static double numero(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return 0d;
        try { return Double.parseDouble(String.valueOf(value).replace(',', '.')); }
        catch (Throwable ignored) { return 0d; }
    }

    private static <T> T await(com.google.android.gms.tasks.Task<T> task) throws Exception {
        return Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
