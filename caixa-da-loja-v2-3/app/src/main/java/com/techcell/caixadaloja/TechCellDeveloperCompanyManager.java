package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Cadastro comercial/estrutural exclusivo do Desenvolvedor.
 * Cria a empresa, Master, índice de login, licença e vagas Caixa.
 */
public final class TechCellDeveloperCompanyManager {
    private static final String EMPRESAS = "techcell_empresas";
    private static final String INDEX = "techcell_user_empresas";
    private static final long TIMEOUT = 25L;

    public static class Dados {
        public String empresaUuid = "";
        public String filialUuid = "";
        public String fantasia = "";
        public String razao = "";
        public String cnpj = "";
        public String telefone = "";
        public String cidade = "";
        public String ownerUid = "";
        public String ownerEmail = "";
        public String ownerNome = "Master";
        public TechCellLicenseManager.Licenca licenca = new TechCellLicenseManager.Licenca();
        public int caixasEmUso;
        public boolean emailDefinicaoSenhaEnviado = true;
    }

    private TechCellDeveloperCompanyManager() {}

    public static Dados ler(Context context, String empresaUuid) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        String id = obrigatorio(empresaUuid, "Empresa inválida.");
        DocumentReference ref = TechCellCloudSync.firestore(app).collection(EMPRESAS).document(id);
        DocumentSnapshot d = Tasks.await(ref.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!d.exists()) throw new IllegalStateException("Empresa não encontrada.");

        Dados out = new Dados();
        out.empresaUuid = id;
        out.filialUuid = texto(d.get("filial_uuid"));
        if (out.filialUuid.isEmpty()) out.filialUuid = id;
        out.ownerUid = texto(d.get("owner_uid"));
        out.ownerEmail = texto(d.get("owner_email"));

        Object cfgObj = d.get("config");
        if (cfgObj instanceof Map) {
            Map<?,?> cfg = (Map<?,?>) cfgObj;
            out.fantasia = texto(cfg.get("fantasia"));
            out.razao = texto(cfg.get("razao"));
            out.cnpj = texto(cfg.get("cnpj"));
            out.telefone = texto(cfg.get("telefone"));
            out.cidade = texto(cfg.get("cidade"));
        }
        if (out.fantasia.isEmpty()) out.fantasia = texto(d.get("nome"));
        if (out.fantasia.isEmpty()) out.fantasia = out.ownerEmail.isEmpty() ? id : out.ownerEmail;

        if (!out.ownerUid.isEmpty()) {
            try {
                DocumentSnapshot u = Tasks.await(ref.collection("usuarios").document(out.ownerUid).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
                if (u.exists()) {
                    out.ownerNome = texto(u.get("nome"));
                    if (out.ownerNome.isEmpty()) out.ownerNome = "Master";
                    if (out.ownerEmail.isEmpty()) out.ownerEmail = texto(u.get("email"));
                }
            } catch (Throwable ignored) {}
        }
        out.licenca = TechCellLicenseManager.lerServidor(app, id);
        out.caixasEmUso = out.licenca.existe ? TechCellLicenseManager.contarSlotsEmUso(app, id) : 0;
        return out;
    }

    public static Dados criar(Context context,
                              String fantasia, String razao, String cnpj, String telefone, String cidade,
                              String masterNome, String masterEmail,
                              String status, String plano, boolean masterAtivo, int caixasContratados) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);

        String f = obrigatorio(fantasia, "Informe o nome fantasia da empresa.");
        String mn = obrigatorio(masterNome, "Informe o nome do Master.");
        String me = email(masterEmail);
        String st = TechCellDeveloper.normalizarStatus(status);
        String pl = TechCellDeveloper.normalizarPlano(plano);
        int caixas = Math.max(0, caixasContratados);

        FirebaseUser dev = TechCellCloudSync.auth(app).getCurrentUser();
        if (dev == null) throw new IllegalStateException("Conta Desenvolvedor não conectada.");

        FirebaseApp secundario = null;
        FirebaseAuth authSec = null;
        FirebaseUser master = null;
        boolean firestoreCriado = false;
        try {
            String appName = "TECHCELL_NEW_COMPANY_" + UUID.randomUUID();
            secundario = FirebaseApp.initializeApp(app, TechCellCloudSync.app(app).getOptions(), appName);
            authSec = FirebaseAuth.getInstance(secundario);
            String senhaTemporaria = "Tc!" + UUID.randomUUID().toString().replace("-", "") + "9a";
            AuthResult ar = Tasks.await(authSec.createUserWithEmailAndPassword(me, senhaTemporaria), TIMEOUT, TimeUnit.SECONDS);
            master = ar.getUser();
            if (master == null || master.getUid() == null || master.getUid().trim().isEmpty()) {
                throw new IllegalStateException("Não foi possível criar a conta Master.");
            }

            String empresaUuid = UUID.randomUUID().toString();
            String filialUuid = empresaUuid;
            DocumentReference empresaRef = TechCellCloudSync.firestore(app).collection(EMPRESAS).document(empresaUuid);
            DocumentReference usuarioRef = empresaRef.collection("usuarios").document(master.getUid());
            DocumentReference indexRef = TechCellCloudSync.firestore(app).collection(INDEX).document(master.getUid())
                    .collection("empresas").document(empresaUuid);
            DocumentReference licRef = TechCellLicenseManager.ref(app, empresaUuid);

            Map<String,Object> cfg = config(f, razao, cnpj, telefone, cidade);
            Map<String,Object> empresa = new HashMap<>();
            empresa.put("empresa_uuid", empresaUuid);
            empresa.put("filial_uuid", filialUuid);
            empresa.put("nome", f);
            empresa.put("owner_uid", master.getUid());
            empresa.put("owner_email", me);
            empresa.put("config", cfg);
            empresa.put("created_by_uid", dev.getUid());
            empresa.put("created_at", FieldValue.serverTimestamp());
            empresa.put("updated_at", FieldValue.serverTimestamp());

            Map<String,Object> usuario = masterMap(master.getUid(), empresaUuid, filialUuid, mn, me);
            Map<String,Object> idx = indexMap(master.getUid(), empresaUuid, filialUuid, mn, me);

            Map<String,Object> lic = new HashMap<>();
            lic.put("empresa_uuid", empresaUuid);
            lic.put("status", st);
            lic.put("plano", pl);
            lic.put("master_ativo", masterAtivo);
            lic.put("caixas_contratados", caixas);
            lic.put("gerentes_contratados", 0);
            lic.put("created_by_uid", dev.getUid());
            lic.put("updated_by_uid", dev.getUid());
            lic.put("created_at", FieldValue.serverTimestamp());
            lic.put("updated_at", FieldValue.serverTimestamp());

            WriteBatch batch = TechCellCloudSync.firestore(app).batch();
            batch.set(empresaRef, empresa);
            batch.set(usuarioRef, usuario);
            batch.set(indexRef, idx);
            batch.set(licRef, lic);
            for (int i = 1; i <= caixas; i++) {
                String slotId = String.format(Locale.ROOT, "C%03d", i);
                DocumentReference slot = licRef.collection(TechCellLicenseManager.SLOTS_CAIXA).document(slotId);
                Map<String,Object> sm = new HashMap<>();
                sm.put("empresa_uuid", empresaUuid);
                sm.put("tipo", "CAIXA");
                sm.put("status", TechCellLicenseManager.LIVRE);
                sm.put("device_uuid", "");
                sm.put("uid", "");
                sm.put("created_at", FieldValue.serverTimestamp());
                sm.put("updated_at", FieldValue.serverTimestamp());
                batch.set(slot, sm);
            }
            DocumentReference audit = empresaRef.collection("auditoria").document();
            Map<String,Object> am = auditMap(app, "EMPRESA_CRIADA", "EMPRESA", empresaUuid,
                    f + " • Master: " + me + " • Plano: " + pl + " • Caixas: " + caixas + " • Status: " + st);
            batch.set(audit, am);
            Tasks.await(batch.commit(), TIMEOUT, TimeUnit.SECONDS);
            firestoreCriado = true;

            Dados out = new Dados();
            out.empresaUuid = empresaUuid;
            out.filialUuid = filialUuid;
            out.fantasia = f;
            out.razao = texto(razao);
            out.cnpj = texto(cnpj);
            out.telefone = texto(telefone);
            out.cidade = texto(cidade);
            out.ownerUid = master.getUid();
            out.ownerEmail = me;
            out.ownerNome = mn;
            out.licenca = TechCellLicenseManager.lerServidor(app, empresaUuid);
            out.caixasEmUso = 0;
            try {
                Tasks.await(TechCellCloudSync.auth(app).sendPasswordResetEmail(me), TIMEOUT, TimeUnit.SECONDS);
                out.emailDefinicaoSenhaEnviado = true;
            } catch (Throwable ignored) {
                out.emailDefinicaoSenhaEnviado = false;
            }
            return out;
        } catch (Throwable erro) {
            if (!firestoreCriado && master != null) {
                try { Tasks.await(master.delete(), 10L, TimeUnit.SECONDS); } catch (Throwable ignored) {}
            }
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundario != null) secundario.delete(); } catch (Throwable ignored) {}
        }
    }

    public static Dados atualizar(Context context, Dados atual,
                                  String fantasia, String razao, String cnpj, String telefone, String cidade,
                                  String status, String plano, boolean masterAtivo, int caixasContratados) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (atual == null || atual.empresaUuid.trim().isEmpty()) throw new IllegalArgumentException("Empresa inválida.");
        String f = obrigatorio(fantasia, "Informe o nome fantasia da empresa.");
        String st = TechCellDeveloper.normalizarStatus(status);
        String pl = TechCellDeveloper.normalizarPlano(plano);
        int caixas = Math.max(0, caixasContratados);

        TechCellDeveloper.Empresa emp = new TechCellDeveloper.Empresa();
        emp.empresaUuid = atual.empresaUuid;
        emp.filialUuid = atual.filialUuid;
        emp.nome = f;
        emp.ownerUid = atual.ownerUid;
        emp.ownerEmail = atual.ownerEmail;
        emp.licenca = atual.licenca;
        emp.caixasEmUso = atual.caixasEmUso;
        TechCellDeveloper.salvarLicenca(app, emp, st, pl, masterAtivo, caixas);

        Map<String,Object> m = new HashMap<>();
        m.put("nome", f);
        m.put("config", config(f, razao, cnpj, telefone, cidade));
        m.put("updated_by_uid", developerUid(app));
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(TechCellCloudSync.firestore(app).collection(EMPRESAS).document(atual.empresaUuid)
                .set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);

        TechCellDeveloperAdmin.registrarAuditoria(app, atual.empresaUuid, "EMPRESA_E_LICENCA_EDITADAS", "EMPRESA", atual.empresaUuid,
                "Nome: " + f + " • Plano: " + pl + " • Status: " + st + " • Master: " + (masterAtivo ? "LIBERADO" : "BLOQUEADO") + " • Caixas: " + caixas);
        return ler(app, atual.empresaUuid);
    }

    public static Dados substituirMaster(Context context, Dados atual, String novoNome, String novoEmail) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (atual == null || atual.empresaUuid.trim().isEmpty()) throw new IllegalArgumentException("Empresa inválida.");
        String nome = obrigatorio(novoNome, "Informe o nome do novo Master.");
        String mail = email(novoEmail);
        if (mail.equalsIgnoreCase(atual.ownerEmail)) throw new IllegalArgumentException("Informe um e-mail diferente do Master atual.");

        FirebaseApp secundario = null;
        FirebaseAuth authSec = null;
        FirebaseUser novo = null;
        boolean vinculoCriado = false;
        try {
            secundario = FirebaseApp.initializeApp(app, TechCellCloudSync.app(app).getOptions(), "TECHCELL_REPLACE_MASTER_" + UUID.randomUUID());
            authSec = FirebaseAuth.getInstance(secundario);
            String senhaTemporaria = "Tc!" + UUID.randomUUID().toString().replace("-", "") + "7b";
            AuthResult ar = Tasks.await(authSec.createUserWithEmailAndPassword(mail, senhaTemporaria), TIMEOUT, TimeUnit.SECONDS);
            novo = ar.getUser();
            if (novo == null || novo.getUid() == null || novo.getUid().trim().isEmpty()) throw new IllegalStateException("Não foi possível criar o novo Master.");

            DocumentReference empresaRef = TechCellCloudSync.firestore(app).collection(EMPRESAS).document(atual.empresaUuid);
            WriteBatch batch = TechCellCloudSync.firestore(app).batch();
            Map<String,Object> em = new HashMap<>();
            em.put("owner_uid", novo.getUid());
            em.put("owner_email", mail);
            em.put("updated_by_uid", developerUid(app));
            em.put("updated_at", FieldValue.serverTimestamp());
            batch.set(empresaRef, em, SetOptions.merge());
            batch.set(empresaRef.collection("usuarios").document(novo.getUid()),
                    masterMap(novo.getUid(), atual.empresaUuid, atual.filialUuid, nome, mail));
            batch.set(TechCellCloudSync.firestore(app).collection(INDEX).document(novo.getUid())
                    .collection("empresas").document(atual.empresaUuid),
                    indexMap(novo.getUid(), atual.empresaUuid, atual.filialUuid, nome, mail));
            if (atual.ownerUid != null && !atual.ownerUid.trim().isEmpty()) {
                batch.delete(empresaRef.collection("usuarios").document(atual.ownerUid));
                batch.delete(TechCellCloudSync.firestore(app).collection(INDEX).document(atual.ownerUid)
                        .collection("empresas").document(atual.empresaUuid));
            }
            DocumentReference audit = empresaRef.collection("auditoria").document();
            batch.set(audit, auditMap(app, "MASTER_SUBSTITUIDO", "MASTER", novo.getUid(),
                    "Anterior: " + atual.ownerEmail + " • Novo: " + mail));
            Tasks.await(batch.commit(), TIMEOUT, TimeUnit.SECONDS);
            vinculoCriado = true;

            Dados out = ler(app, atual.empresaUuid);
            try {
                Tasks.await(TechCellCloudSync.auth(app).sendPasswordResetEmail(mail), TIMEOUT, TimeUnit.SECONDS);
                out.emailDefinicaoSenhaEnviado = true;
            } catch (Throwable ignored) {
                out.emailDefinicaoSenhaEnviado = false;
            }
            return out;
        } catch (Throwable erro) {
            if (!vinculoCriado && novo != null) {
                try { Tasks.await(novo.delete(), 10L, TimeUnit.SECONDS); } catch (Throwable ignored) {}
            }
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundario != null) secundario.delete(); } catch (Throwable ignored) {}
        }
    }

    public static void reenviarDefinicaoSenha(Context context, String empresaUuid, String email) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        String e = email(email);
        Tasks.await(TechCellCloudSync.auth(app).sendPasswordResetEmail(e), TIMEOUT, TimeUnit.SECONDS);
        TechCellDeveloperAdmin.registrarAuditoria(app, empresaUuid, "DEFINICAO_SENHA_MASTER_REENVIADA", "MASTER", e,
                "E-mail de definição/redefinição de senha enviado para " + e);
    }

    private static Map<String,Object> config(String fantasia, String razao, String cnpj, String telefone, String cidade) {
        Map<String,Object> cfg = new HashMap<>();
        cfg.put("fantasia", texto(fantasia));
        cfg.put("razao", texto(razao));
        cfg.put("cnpj", texto(cnpj));
        cfg.put("telefone", texto(telefone));
        cfg.put("cidade", texto(cidade));
        return cfg;
    }

    private static Map<String,Object> masterMap(String uid, String empresaUuid, String filialUuid, String nome, String email) {
        Map<String,Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("empresa_uuid", empresaUuid);
        m.put("filial_uuid", filialUuid == null || filialUuid.trim().isEmpty() ? empresaUuid : filialUuid);
        m.put("nome", nome);
        m.put("email", email);
        m.put("perfil", "MASTER");
        m.put("ativo", true);
        m.put("proprietario", true);
        m.put("created_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        return m;
    }

    private static Map<String,Object> indexMap(String uid, String empresaUuid, String filialUuid, String nome, String email) {
        Map<String,Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("empresa_uuid", empresaUuid);
        m.put("filial_uuid", filialUuid == null || filialUuid.trim().isEmpty() ? empresaUuid : filialUuid);
        m.put("nome", nome);
        m.put("email", email);
        m.put("perfil", "MASTER");
        m.put("proprietario", true);
        m.put("ativo", true);
        m.put("updated_at", FieldValue.serverTimestamp());
        return m;
    }

    private static Map<String,Object> auditMap(Context app, String acao, String alvoTipo, String alvoId, String descricao) {
        FirebaseUser dev = TechCellCloudSync.auth(app).getCurrentUser();
        TechCellDeveloperAccess.Estado de = TechCellDeveloperAccess.local(app);
        Map<String,Object> m = new HashMap<>();
        m.put("actor_uid", dev == null ? "" : dev.getUid());
        m.put("actor_email", dev == null || dev.getEmail() == null ? "" : dev.getEmail());
        m.put("actor_nome", de.nome == null || de.nome.trim().isEmpty() ? "Desenvolvedor" : de.nome);
        m.put("actor_tipo", "DEVELOPER");
        m.put("acao", acao);
        m.put("alvo_tipo", alvoTipo);
        m.put("alvo_id", alvoId);
        m.put("descricao", descricao);
        m.put("created_at", FieldValue.serverTimestamp());
        return m;
    }

    private static String developerUid(Context app) {
        FirebaseUser u = TechCellCloudSync.auth(app).getCurrentUser();
        if (u == null) throw new IllegalStateException("Conta Desenvolvedor não conectada.");
        return u.getUid();
    }

    private static String obrigatorio(String v, String mensagem) {
        String s = texto(v);
        if (s.isEmpty()) throw new IllegalArgumentException(mensagem);
        return s;
    }

    private static String email(String v) {
        String e = texto(v).toLowerCase(Locale.ROOT);
        if (e.isEmpty() || !e.contains("@") || e.startsWith("@") || e.endsWith("@")) {
            throw new IllegalArgumentException("Informe um e-mail válido para o Master.");
        }
        return e;
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
