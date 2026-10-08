package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Operações globais exclusivas da conta Desenvolvedor da plataforma. */
public final class TechCellDeveloper {
    private static final String EMPRESAS = "techcell_empresas";
    private static final String REQUESTS = "techcell_device_requests";
    private static final long TIMEOUT = 15L;

    public static class Empresa {
        public String empresaUuid = "";
        public String nome = "";
        public String ownerUid = "";
        public String ownerEmail = "";
        public TechCellLicenseManager.Licenca licenca = new TechCellLicenseManager.Licenca();
        public int caixasEmUso;
        public int caixasAutorizadosLegado;
        public int solicitacoesPendentes;

        public String statusExibicao() {
            if (!licenca.existe) return "LEGADO / SEM LICENÇA COMERCIAL";
            return licenca.status;
        }
    }

    private TechCellDeveloper() {}

    public static List<Empresa> listarEmpresas(Context context) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        QuerySnapshot qs = Tasks.await(TechCellCloudSync.firestore(app).collection(EMPRESAS)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<Empresa> out = new ArrayList<>();
        if (qs != null) {
            for (QueryDocumentSnapshot d : qs) {
                Empresa e = new Empresa();
                e.empresaUuid = d.getId();
                e.ownerUid = texto(d.get("owner_uid"));
                e.ownerEmail = texto(d.get("owner_email"));
                e.nome = nomeEmpresa(d);
                e.licenca = TechCellLicenseManager.lerServidor(app, e.empresaUuid);
                if (e.licenca.existe) e.caixasEmUso = TechCellLicenseManager.contarSlotsEmUso(app, e.empresaUuid);
                int[] req = contarPedidos(app, e.empresaUuid);
                e.caixasAutorizadosLegado = req[0];
                e.solicitacoesPendentes = req[1];
                out.add(e);
            }
        }
        Collections.sort(out, new Comparator<Empresa>() {
            @Override public int compare(Empresa a, Empresa b) {
                int pa = prioridade(a), pb = prioridade(b);
                if (pa != pb) return Integer.compare(pa, pb);
                return a.nome.compareToIgnoreCase(b.nome);
            }
        });
        return out;
    }

    public static void salvarLicenca(Context context, Empresa empresa, String status, String plano,
                                     boolean masterAtivo, int caixasContratados) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (empresa == null || empresa.empresaUuid == null || empresa.empresaUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Empresa inválida.");
        }
        String s = normalizarStatus(status);
        String p = normalizarPlano(plano);
        int caixas = Math.max(0, caixasContratados);

        List<DocumentSnapshot> autorizados = listarCaixasAutorizados(app, empresa.empresaUuid);
        if (caixas < autorizados.size()) {
            throw new IllegalStateException("Esta empresa já possui " + autorizados.size() +
                    " aparelho(s) Caixa autorizado(s). Bloqueie aparelhos antes de reduzir para " + caixas + ".");
        }

        DocumentReference licRef = TechCellLicenseManager.ref(app, empresa.empresaUuid);
        Map<String,Object> dados = new HashMap<>();
        dados.put("empresa_uuid", empresa.empresaUuid);
        dados.put("status", s);
        dados.put("plano", p);
        dados.put("master_ativo", masterAtivo);
        dados.put("caixas_contratados", caixas);
        dados.put("gerentes_contratados", 0);
        dados.put("updated_by_uid", TechCellCloudSync.auth(app).getCurrentUser().getUid());
        dados.put("updated_at", FieldValue.serverTimestamp());
        DocumentSnapshot anterior = Tasks.await(licRef.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!anterior.exists()) dados.put("created_at", FieldValue.serverTimestamp());
        Tasks.await(licRef.set(dados, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);

        reconciliarSlots(app, empresa.empresaUuid, caixas, autorizados);
        empresa.licenca = TechCellLicenseManager.lerServidor(app, empresa.empresaUuid);
        empresa.caixasEmUso = TechCellLicenseManager.contarSlotsEmUso(app, empresa.empresaUuid);
    }

    private static void reconciliarSlots(Context app, String empresaUuid, int desejados,
                                         List<DocumentSnapshot> autorizados) throws Exception {
        QuerySnapshot slotQs = Tasks.await(TechCellLicenseManager.slotsCaixa(app, empresaUuid)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        LinkedHashMap<String,DocumentSnapshot> slots = new LinkedHashMap<>();
        if (slotQs != null) for (QueryDocumentSnapshot d : slotQs) slots.put(d.getId(), d);

        // Cria vagas até atingir a quantidade contratada. IDs são estáveis e legíveis.
        int proximo = 1;
        WriteBatch criar = TechCellCloudSync.firestore(app).batch();
        int criados = 0;
        while (slots.size() + criados < desejados) {
            String id;
            do { id = String.format(Locale.ROOT, "C%03d", proximo++); }
            while (slots.containsKey(id));
            DocumentReference r = TechCellLicenseManager.slotsCaixa(app, empresaUuid).document(id);
            Map<String,Object> m = new HashMap<>();
            m.put("empresa_uuid", empresaUuid);
            m.put("tipo", "CAIXA");
            m.put("status", TechCellLicenseManager.LIVRE);
            m.put("device_uuid", "");
            m.put("uid", "");
            m.put("created_at", FieldValue.serverTimestamp());
            m.put("updated_at", FieldValue.serverTimestamp());
            criar.set(r, m);
            criados++;
        }
        if (criados > 0) Tasks.await(criar.commit(), TIMEOUT, TimeUnit.SECONDS);

        slotQs = Tasks.await(TechCellLicenseManager.slotsCaixa(app, empresaUuid)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<DocumentSnapshot> atuais = new ArrayList<>();
        if (slotQs != null) for (QueryDocumentSnapshot d : slotQs) atuais.add(d);

        // Ao reduzir, remove apenas vagas livres. Vaga em uso nunca é apagada.
        if (atuais.size() > desejados) {
            int remover = atuais.size() - desejados;
            WriteBatch del = TechCellCloudSync.firestore(app).batch();
            int removidos = 0;
            for (int i = atuais.size() - 1; i >= 0 && removidos < remover; i--) {
                DocumentSnapshot d = atuais.get(i);
                if (TechCellLicenseManager.LIVRE.equalsIgnoreCase(texto(d.get("status")))) {
                    del.delete(d.getReference());
                    removidos++;
                }
            }
            if (removidos < remover) throw new IllegalStateException("Não há vagas livres suficientes para reduzir a licença.");
            Tasks.await(del.commit(), TIMEOUT, TimeUnit.SECONDS);
        }

        // Migra aparelhos já autorizados antes do sistema de licenças para ocupar uma vaga.
        slotQs = Tasks.await(TechCellLicenseManager.slotsCaixa(app, empresaUuid)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<DocumentSnapshot> livres = new ArrayList<>();
        if (slotQs != null) {
            for (QueryDocumentSnapshot d : slotQs) {
                if (TechCellLicenseManager.LIVRE.equalsIgnoreCase(texto(d.get("status")))) livres.add(d);
            }
        }
        int livre = 0;
        for (DocumentSnapshot req : autorizados) {
            String atual = texto(req.get("license_slot_id"));
            if (!atual.isEmpty()) continue;
            if (livre >= livres.size()) throw new IllegalStateException("Faltou vaga para migrar um aparelho Caixa já autorizado.");
            DocumentSnapshot slot = livres.get(livre++);
            WriteBatch b = TechCellCloudSync.firestore(app).batch();
            Map<String,Object> sm = new HashMap<>();
            sm.put("status", TechCellLicenseManager.EM_USO);
            sm.put("device_uuid", texto(req.get("device_uuid")));
            sm.put("uid", texto(req.get("uid")));
            sm.put("updated_at", FieldValue.serverTimestamp());
            b.set(slot.getReference(), sm, SetOptions.merge());
            Map<String,Object> rm = new HashMap<>();
            rm.put("license_slot_id", slot.getId());
            rm.put("license_assigned_at", FieldValue.serverTimestamp());
            rm.put("updated_at", FieldValue.serverTimestamp());
            b.set(req.getReference(), rm, SetOptions.merge());
            Tasks.await(b.commit(), TIMEOUT, TimeUnit.SECONDS);
        }
    }

    private static List<DocumentSnapshot> listarCaixasAutorizados(Context app, String empresaUuid) throws Exception {
        QuerySnapshot qs = Tasks.await(TechCellCloudSync.firestore(app).collection(REQUESTS)
                .document(empresaUuid).collection("devices").whereEqualTo("status", TechCellDeviceAuthorization.AUTORIZADO)
                .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<DocumentSnapshot> out = new ArrayList<>();
        if (qs != null) {
            for (QueryDocumentSnapshot d : qs) {
                String perfil = texto(d.get("perfil"));
                if (perfil.isEmpty() || "CAIXA".equalsIgnoreCase(perfil)) out.add(d);
            }
        }
        return out;
    }

    private static int[] contarPedidos(Context app, String empresaUuid) throws Exception {
        QuerySnapshot qs = Tasks.await(TechCellCloudSync.firestore(app).collection(REQUESTS)
                .document(empresaUuid).collection("devices").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        int autorizados = 0, pendentes = 0;
        if (qs != null) {
            for (QueryDocumentSnapshot d : qs) {
                String perfil = texto(d.get("perfil"));
                if (!perfil.isEmpty() && !"CAIXA".equalsIgnoreCase(perfil)) continue;
                String status = texto(d.get("status"));
                if (TechCellDeviceAuthorization.AUTORIZADO.equalsIgnoreCase(status)) autorizados++;
                if (TechCellDeviceAuthorization.PENDENTE.equalsIgnoreCase(status)) pendentes++;
            }
        }
        return new int[]{autorizados, pendentes};
    }

    private static int prioridade(Empresa e) {
        if (!e.licenca.existe) return 0;
        if (TechCellLicenseManager.PENDENTE.equalsIgnoreCase(e.licenca.status)) return 1;
        if (TechCellLicenseManager.SUSPENSA.equalsIgnoreCase(e.licenca.status)) return 2;
        return 3;
    }

    private static String nomeEmpresa(DocumentSnapshot d) {
        Object cfgObj = d.get("config");
        if (cfgObj instanceof Map) {
            Map<?,?> cfg = (Map<?,?>) cfgObj;
            String f = texto(cfg.get("fantasia"));
            if (!f.isEmpty()) return f;
            String r = texto(cfg.get("razao"));
            if (!r.isEmpty()) return r;
        }
        String email = texto(d.get("owner_email"));
        return email.isEmpty() ? d.getId() : email;
    }

    public static String normalizarStatus(String valor) {
        String v = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (TechCellLicenseManager.ATIVA.equals(v) || TechCellLicenseManager.PENDENTE.equals(v)
                || TechCellLicenseManager.SUSPENSA.equals(v) || TechCellLicenseManager.CANCELADA.equals(v)) return v;
        throw new IllegalArgumentException("Status de licença inválido.");
    }

    public static String normalizarPlano(String valor) {
        String v = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if ("LOCAL".equals(v) || "HIBRIDO".equals(v) || "HÍBRIDO".equals(v) || "NUVEM".equals(v)) {
            return "HÍBRIDO".equals(v) ? "HIBRIDO" : v;
        }
        throw new IllegalArgumentException("Plano inválido.");
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
