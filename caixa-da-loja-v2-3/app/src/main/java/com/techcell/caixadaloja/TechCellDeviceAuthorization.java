package com.techcell.caixadaloja;

import android.content.Context;
import android.os.Build;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Autorização de aparelhos novos pelo Master. */
public final class TechCellDeviceAuthorization {
    private static final String REQUESTS = "techcell_device_requests";
    private static final String CODES = "techcell_device_codes";
    private static final String ROOT = "techcell_empresas";
    private static final long TIMEOUT = 15L;
    private static final long CODE_VALID_MS = 10L * 60L * 1000L;
    private static final SecureRandom RNG = new SecureRandom();

    public static final String PENDENTE = "PENDENTE";
    public static final String AUTORIZADO = "AUTORIZADO";
    public static final String NEGADO = "NEGADO";
    public static final String BLOQUEADO = "BLOQUEADO";

    public static class Dispositivo {
        public String deviceUuid = "";
        public String uid = "";
        public String email = "";
        public String nomeUsuario = "";
        public String perfil = "";
        public String nomeAparelho = "";
        public String empresaUuid = "";
        public String status = PENDENTE;
        public long solicitadoEm;
        public long atualizadoEm;

        public boolean pendente(){ return PENDENTE.equalsIgnoreCase(status); }
        public boolean autorizado(){ return AUTORIZADO.equalsIgnoreCase(status); }
        public boolean bloqueado(){ return BLOQUEADO.equalsIgnoreCase(status); }
    }

    public static class Codigo {
        public String valor = "";
        public long expiraEm;
    }

    private static class OwnerSession {
        String empresaUuid;
        FirebaseUser user;
    }

    private TechCellDeviceAuthorization() {}

    public static String deviceUuid(Context context) {
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        try {
            String id = db.getSyncContext().dispositivoUuid;
            return id == null ? "" : id.trim();
        } finally { db.close(); }
    }

    public static String nomeAparelho() {
        String fabricante = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER.trim();
        String modelo = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (fabricante.isEmpty()) fabricante = "Android";
        if (modelo.isEmpty()) return fabricante;
        if (modelo.toLowerCase(Locale.ROOT).startsWith(fabricante.toLowerCase(Locale.ROOT))) return modelo;
        return fabricante + " " + modelo;
    }

    /** Cria a solicitação sem liberar acesso aos dados. */
    public static Dispositivo solicitar(Context context, TechCellUserCompanyIndex.Vinculo vinculo, FirebaseUser user) throws Exception {
        if (vinculo == null || vinculo.empresaUuid == null || vinculo.empresaUuid.trim().isEmpty()) {
            throw new IllegalStateException("Não foi possível identificar a empresa desta conta.");
        }
        if (user == null) throw new IllegalStateException("Conta não conectada.");
        Context app = context.getApplicationContext();
        String device = deviceUuid(app);
        if (device.isEmpty()) throw new IllegalStateException("Este aparelho está sem identificação local.");

        DocumentReference ref = requestRef(app, vinculo.empresaUuid, device);
        DocumentSnapshot atual = Tasks.await(ref.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (atual.exists()) {
            Dispositivo d = fromDoc(atual);
            // Não reabre automaticamente algo negado/bloqueado: só o Master decide.
            if (!PENDENTE.equalsIgnoreCase(d.status)) return d;
            Map<String,Object> toque = new HashMap<>();
            toque.put("last_request_at", FieldValue.serverTimestamp());
            toque.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(ref.set(toque, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
            return d;
        }

        Map<String,Object> m = new HashMap<>();
        m.put("empresa_uuid", vinculo.empresaUuid);
        m.put("filial_uuid", vinculo.filialUuid == null ? "" : vinculo.filialUuid);
        m.put("device_uuid", device);
        m.put("uid", user.getUid());
        m.put("email", user.getEmail() == null ? "" : user.getEmail());
        m.put("nome_usuario", vinculo.nome == null ? "" : vinculo.nome);
        m.put("perfil", vinculo.perfil == null ? "" : vinculo.perfil);
        m.put("nome_aparelho", nomeAparelho());
        m.put("status", PENDENTE);
        m.put("source", "REMOTE_FIRST_LOGIN");
        m.put("requested_at", FieldValue.serverTimestamp());
        m.put("last_request_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(m), TIMEOUT, TimeUnit.SECONDS);
        return consultar(app, vinculo.empresaUuid, device);
    }

    public static Dispositivo consultar(Context context, String empresaUuid, String deviceUuid) throws Exception {
        DocumentSnapshot d = Tasks.await(requestRef(context.getApplicationContext(), empresaUuid, deviceUuid)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!d.exists()) return null;
        return fromDoc(d);
    }

    public static boolean estaAutorizado(Context context, String empresaUuid, String deviceUuid, String uid) throws Exception {
        Dispositivo d = consultar(context, empresaUuid, deviceUuid);
        return d != null && d.autorizado() && uid != null && uid.equals(d.uid);
    }

    public static List<Dispositivo> listar(Context context) throws Exception {
        Context app = context.getApplicationContext();
        OwnerSession s = owner(app);
        QuerySnapshot qs = Tasks.await(TechCellCloudSync.firestore(app).collection(REQUESTS)
                .document(s.empresaUuid).collection("devices").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<Dispositivo> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : qs) out.add(fromDoc(d));
        Collections.sort(out, new Comparator<Dispositivo>() {
            @Override public int compare(Dispositivo a, Dispositivo b) {
                int pa = prioridade(a.status), pb = prioridade(b.status);
                if (pa != pb) return Integer.compare(pa, pb);
                return Long.compare(b.solicitadoEm, a.solicitadoEm);
            }
        });
        return out;
    }

    public static int contarPendentes(Context context) throws Exception {
        int n = 0;
        for (Dispositivo d : listar(context)) if (d.pendente()) n++;
        return n;
    }

    public static void aprovar(Context context, Dispositivo d) throws Exception {
        OwnerSession s = owner(context.getApplicationContext());
        validarEmpresa(d, s.empresaUuid);
        Map<String,Object> m = new HashMap<>();
        m.put("status", AUTORIZADO);
        m.put("approved_by_uid", s.user.getUid());
        m.put("approved_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(requestRef(context, s.empresaUuid, d.deviceUuid).set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        d.status = AUTORIZADO;
    }

    public static void negar(Context context, Dispositivo d) throws Exception {
        OwnerSession s = owner(context.getApplicationContext());
        validarEmpresa(d, s.empresaUuid);
        Map<String,Object> m = new HashMap<>();
        m.put("status", NEGADO);
        m.put("denied_by_uid", s.user.getUid());
        m.put("denied_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(requestRef(context, s.empresaUuid, d.deviceUuid).set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        d.status = NEGADO;
    }

    public static void bloquear(Context context, Dispositivo d) throws Exception {
        OwnerSession s = owner(context.getApplicationContext());
        validarEmpresa(d, s.empresaUuid);
        Map<String,Object> m = new HashMap<>();
        m.put("status", BLOQUEADO);
        m.put("blocked_by_uid", s.user.getUid());
        m.put("blocked_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(requestRef(context, s.empresaUuid, d.deviceUuid).set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        d.status = BLOQUEADO;
    }

    public static void reautorizar(Context context, Dispositivo d) throws Exception {
        aprovar(context, d);
    }

    /** Código alternativo de uso único lógico, válido por 10 minutos. */
    public static Codigo gerarCodigo(Context context, Dispositivo d) throws Exception {
        OwnerSession s = owner(context.getApplicationContext());
        validarEmpresa(d, s.empresaUuid);
        String codigo = String.format(Locale.ROOT, "%06d", RNG.nextInt(1_000_000));
        long expira = System.currentTimeMillis() + CODE_VALID_MS;
        Map<String,Object> m = new HashMap<>();
        m.put("empresa_uuid", s.empresaUuid);
        m.put("device_uuid", d.deviceUuid);
        m.put("code", codigo);
        m.put("expires_at", new Timestamp(new Date(expira)));
        m.put("created_by_uid", s.user.getUid());
        m.put("created_at", FieldValue.serverTimestamp());
        Tasks.await(codeRef(context, s.empresaUuid, d.deviceUuid).set(m), TIMEOUT, TimeUnit.SECONDS);
        Codigo c = new Codigo(); c.valor = codigo; c.expiraEm = expira; return c;
    }

    /** O próprio usuário confirma o código; a regra do Firestore valida o segredo e a validade. */
    public static void usarCodigo(Context context, String empresaUuid, String deviceUuid, String codigo) throws Exception {
        FirebaseUser user = TechCellCloudSync.auth(context).getCurrentUser();
        if (user == null) throw new IllegalStateException("Conta não conectada.");
        String c = codigo == null ? "" : codigo.replaceAll("[^0-9]", "");
        if (c.length() != 6) throw new IllegalArgumentException("Informe os 6 dígitos do código.");
        Map<String,Object> m = new HashMap<>();
        m.put("status", AUTORIZADO);
        m.put("provided_code", c);
        m.put("code_used_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(requestRef(context, empresaUuid, deviceUuid).set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        Dispositivo d = consultar(context, empresaUuid, deviceUuid);
        if (d == null || !d.autorizado() || !user.getUid().equals(d.uid)) {
            throw new IllegalStateException("O código não liberou este aparelho.");
        }
    }

    private static OwnerSession owner(Context context) throws Exception {
        GestaoDbHelper db = new GestaoDbHelper(context);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }
        if (ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) throw new IllegalStateException("Empresa não configurada.");
        FirebaseUser u = TechCellCloudSync.auth(context).getCurrentUser();
        if (u == null) throw new IllegalStateException("Entre na conta Master.");
        DocumentSnapshot empresa = Tasks.await(TechCellCloudSync.firestore(context).collection(ROOT)
                .document(ctx.empresaUuid).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!empresa.exists() || !u.getUid().equals(texto(empresa.get("owner_uid")))) {
            throw new IllegalStateException("Somente o Master proprietário pode autorizar aparelhos.");
        }
        OwnerSession s = new OwnerSession(); s.empresaUuid = ctx.empresaUuid; s.user = u; return s;
    }

    private static DocumentReference requestRef(Context context, String empresa, String device) {
        return TechCellCloudSync.firestore(context).collection(REQUESTS).document(empresa)
                .collection("devices").document(device);
    }

    private static DocumentReference codeRef(Context context, String empresa, String device) {
        return TechCellCloudSync.firestore(context).collection(CODES).document(empresa)
                .collection("devices").document(device);
    }

    private static void validarEmpresa(Dispositivo d, String empresa) {
        if (d == null || d.deviceUuid == null || d.deviceUuid.trim().isEmpty()) throw new IllegalArgumentException("Aparelho inválido.");
        if (d.empresaUuid != null && !d.empresaUuid.trim().isEmpty() && !empresa.equals(d.empresaUuid)) {
            throw new IllegalStateException("Este aparelho pertence a outra empresa.");
        }
    }

    private static int prioridade(String status) {
        if (PENDENTE.equalsIgnoreCase(status)) return 0;
        if (AUTORIZADO.equalsIgnoreCase(status)) return 1;
        if (BLOQUEADO.equalsIgnoreCase(status)) return 2;
        return 3;
    }

    private static Dispositivo fromDoc(DocumentSnapshot d) {
        Dispositivo x = new Dispositivo();
        x.deviceUuid = texto(d.get("device_uuid")); if (x.deviceUuid.isEmpty()) x.deviceUuid = d.getId();
        x.uid = texto(d.get("uid")); x.email = texto(d.get("email")); x.nomeUsuario = texto(d.get("nome_usuario"));
        x.perfil = texto(d.get("perfil")); x.nomeAparelho = texto(d.get("nome_aparelho")); x.empresaUuid = texto(d.get("empresa_uuid"));
        x.status = texto(d.get("status")); if (x.status.isEmpty()) x.status = PENDENTE;
        x.solicitadoEm = millis(d.get("requested_at")); x.atualizadoEm = millis(d.get("updated_at"));
        return x;
    }

    private static long millis(Object v) {
        if (v instanceof Timestamp) return ((Timestamp)v).toDate().getTime();
        if (v instanceof Number) return ((Number)v).longValue();
        return 0L;
    }
    private static String texto(Object v){ return v == null ? "" : String.valueOf(v).trim(); }
}
