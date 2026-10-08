package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
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

/** Licença comercial da empresa. O SQLite continua operacional; esta camada controla habilitação e vagas de aparelhos. */
public final class TechCellLicenseManager {
    public static final String LICENCAS = "techcell_licenses";
    public static final String SLOTS_CAIXA = "caixa_slots";
    public static final String ATIVA = "ATIVA";
    public static final String PENDENTE = "PENDENTE";
    public static final String SUSPENSA = "SUSPENSA";
    public static final String CANCELADA = "CANCELADA";
    public static final String LIVRE = "LIVRE";
    public static final String EM_USO = "EM_USO";

    private static final String PREF = "techcell_license_v1";
    private static final long TIMEOUT = 12L;

    public static class Licenca {
        public boolean existe;
        public String empresaUuid = "";
        public String status = "LEGADO";
        public String plano = "LEGADO";
        public boolean masterAtivo = true;
        public int caixasContratados;
        public int gerentesContratados;
        public long atualizadoEm;

        public boolean ativa() { return !existe || ATIVA.equalsIgnoreCase(status); }
    }

    private TechCellLicenseManager() {}

    public static DocumentReference ref(Context context, String empresaUuid) {
        return TechCellCloudSync.firestore(context).collection(LICENCAS).document(empresaUuid);
    }

    public static CollectionReference slotsCaixa(Context context, String empresaUuid) {
        return ref(context, empresaUuid).collection(SLOTS_CAIXA);
    }

    public static Licenca lerServidor(Context context, String empresaUuid) throws Exception {
        if (empresaUuid == null || empresaUuid.trim().isEmpty()) throw new IllegalArgumentException("Empresa sem identificação.");
        DocumentSnapshot d = Tasks.await(ref(context, empresaUuid).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        Licenca l = fromDoc(empresaUuid, d);
        salvarCache(context, l);
        return l;
    }

    public static Licenca lerLocal(Context context, String empresaUuid) {
        Licenca l = new Licenca();
        l.empresaUuid = empresaUuid == null ? "" : empresaUuid;
        SharedPreferences p = prefs(context);
        if (!l.empresaUuid.equals(p.getString("empresa_uuid", ""))) return l;
        boolean conhecida = p.getBoolean("conhecida", false);
        if (!conhecida) return l;
        l.existe = p.getBoolean("existe", false);
        l.status = p.getString("status", l.existe ? PENDENTE : "LEGADO");
        l.plano = p.getString("plano", l.existe ? "LOCAL" : "LEGADO");
        l.masterAtivo = p.getBoolean("master_ativo", !l.existe);
        l.caixasContratados = p.getInt("caixas_contratados", 0);
        l.gerentesContratados = p.getInt("gerentes_contratados", 0);
        l.atualizadoEm = p.getLong("updated_at", 0L);
        return l;
    }

    /**
     * Valida na nuvem. Empresas antigas sem documento de licença permanecem em modo legado
     * até o Desenvolvedor migrá-las pelo painel; empresas novas recebem PENDENTE.
     */
    public static Licenca validarAcesso(Context context, String empresaUuid, boolean proprietario) throws Exception {
        if (TechCellDeveloperAccess.ehDesenvolvedor(context)) {
            Licenca l = lerServidor(context, empresaUuid);
            return l;
        }
        Licenca l = lerServidor(context, empresaUuid);
        if (!l.existe) return l; // compatibilidade de empresas anteriores ao sistema de licenças
        if (!ATIVA.equalsIgnoreCase(l.status)) {
            throw new IllegalStateException(mensagemStatus(l.status));
        }
        if (proprietario && !l.masterAtivo) {
            throw new IllegalStateException("A licença Master desta empresa ainda não foi liberada pelo Desenvolvedor.");
        }
        return l;
    }

    /** Usa o último estado confirmado para a sessão local/offline. */
    public static boolean permiteSessaoLocal(Context context, String empresaUuid, boolean proprietario) {
        if (TechCellDeveloperAccess.ehDesenvolvedor(context)) return true;
        SharedPreferences p = prefs(context);
        if (!empresaUuid.equals(p.getString("empresa_uuid", "")) || !p.getBoolean("conhecida", false)) return true;
        boolean existe = p.getBoolean("existe", false);
        if (!existe) return true;
        if (!ATIVA.equalsIgnoreCase(p.getString("status", PENDENTE))) return false;
        return !proprietario || p.getBoolean("master_ativo", false);
    }

    /** Nova empresa cria apenas uma solicitação PENDENTE; o proprietário não pode promovê-la para ATIVA. */
    public static void criarPendenteSeAusente(Context context, String empresaUuid) throws Exception {
        FirebaseUser u = TechCellCloudSync.auth(context).getCurrentUser();
        if (u == null) throw new IllegalStateException("Conta Master não conectada.");
        DocumentReference r = ref(context, empresaUuid);
        DocumentSnapshot d = Tasks.await(r.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (d.exists()) {
            salvarCache(context, fromDoc(empresaUuid, d));
            return;
        }
        Map<String,Object> m = new HashMap<>();
        m.put("empresa_uuid", empresaUuid);
        m.put("status", PENDENTE);
        m.put("plano", "LOCAL");
        m.put("master_ativo", false);
        m.put("caixas_contratados", 0);
        m.put("gerentes_contratados", 0);
        m.put("requested_by_uid", u.getUid());
        m.put("created_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(r.set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        Licenca l = new Licenca();
        l.existe = true; l.empresaUuid = empresaUuid; l.status = PENDENTE; l.plano = "LOCAL"; l.masterAtivo = false;
        salvarCache(context, l);
    }

    public static int contarSlotsEmUso(Context context, String empresaUuid) throws Exception {
        QuerySnapshot qs = Tasks.await(slotsCaixa(context, empresaUuid).whereEqualTo("status", EM_USO)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        return qs == null ? 0 : qs.size();
    }

    public static QueryDocumentSnapshot primeiroSlotLivre(Context context, String empresaUuid) throws Exception {
        QuerySnapshot qs = Tasks.await(slotsCaixa(context, empresaUuid).whereEqualTo("status", LIVRE).limit(1)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (qs == null || qs.isEmpty()) return null;
        return qs.iterator().next();
    }

    public static String mensagemStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (PENDENTE.equals(s)) return "Esta empresa está aguardando liberação da licença pelo Desenvolvedor.";
        if (SUSPENSA.equals(s)) return "A licença desta empresa está suspensa. Procure o responsável pelo sistema.";
        if (CANCELADA.equals(s)) return "A licença desta empresa foi cancelada.";
        return "A licença desta empresa não está ativa.";
    }

    private static Licenca fromDoc(String empresaUuid, DocumentSnapshot d) {
        Licenca l = new Licenca();
        l.empresaUuid = empresaUuid;
        l.existe = d != null && d.exists();
        if (!l.existe) return l;
        l.status = texto(d.get("status")); if (l.status.isEmpty()) l.status = PENDENTE;
        l.plano = texto(d.get("plano")); if (l.plano.isEmpty()) l.plano = "LOCAL";
        l.masterAtivo = bool(d.get("master_ativo"), false);
        l.caixasContratados = inteiro(d.get("caixas_contratados"));
        l.gerentesContratados = inteiro(d.get("gerentes_contratados"));
        Object t = d.get("updated_at");
        if (t instanceof com.google.firebase.Timestamp) l.atualizadoEm = ((com.google.firebase.Timestamp)t).toDate().getTime();
        return l;
    }

    private static void salvarCache(Context context, Licenca l) {
        prefs(context).edit()
                .putString("empresa_uuid", l.empresaUuid)
                .putBoolean("conhecida", true)
                .putBoolean("existe", l.existe)
                .putString("status", l.status)
                .putString("plano", l.plano)
                .putBoolean("master_ativo", l.masterAtivo)
                .putInt("caixas_contratados", l.caixasContratados)
                .putInt("gerentes_contratados", l.gerentesContratados)
                .putLong("updated_at", System.currentTimeMillis())
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private static boolean bool(Object v, boolean padrao) { return v instanceof Boolean ? (Boolean)v : padrao; }
    private static int inteiro(Object v) { return v instanceof Number ? Math.max(0, ((Number)v).intValue()) : 0; }
    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
