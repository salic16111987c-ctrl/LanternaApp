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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Administração REAL de uma empresa pela conta global do Desenvolvedor.
 * Diferente do modo de teste: tudo aqui grava na empresa e gera auditoria.
 */
public final class TechCellDeveloperAdmin {
    private static final String EMPRESAS = "techcell_empresas";
    private static final String INDEX = "techcell_user_empresas";
    private static final String REQUESTS = "techcell_device_requests";
    private static final String AUDITORIA = "auditoria";
    private static final long TIMEOUT = 20L;

    public static class Usuario {
        public String uid = "";
        public String nome = "";
        public String email = "";
        public String perfil = "CAIXA";
        public boolean ativo = true;
        public boolean proprietario;
        public Set<String> permissoes = new LinkedHashSet<>();

        public String perfilExibicao() {
            if (proprietario) return "Master proprietário";
            if ("GERENTE".equalsIgnoreCase(perfil)) return "Gerente";
            return "Caixa";
        }
    }

    public static class Dispositivo {
        public String deviceUuid = "";
        public String uid = "";
        public String email = "";
        public String nomeUsuario = "";
        public String perfil = "";
        public String nomeAparelho = "";
        public String status = TechCellDeviceAuthorization.PENDENTE;
        public String licenseSlotId = "";
        public long solicitadoEm;
    }

    public static class EventoAuditoria {
        public String acao = "";
        public String alvoTipo = "";
        public String alvoId = "";
        public String descricao = "";
        public String atorNome = "";
        public String atorEmail = "";
        public long criadoEm;
    }

    private TechCellDeveloperAdmin() {}

    public static List<Usuario> listarUsuarios(Context context, String empresaUuid) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        DocumentReference empresa = empresaRef(app, empresaUuid);
        DocumentSnapshot ed = Tasks.await(empresa.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!ed.exists()) throw new IllegalStateException("Empresa não encontrada.");
        String ownerUid = texto(ed.get("owner_uid"));
        String ownerEmail = texto(ed.get("owner_email"));

        QuerySnapshot qs = Tasks.await(empresa.collection("usuarios").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<Usuario> out = new ArrayList<>();
        boolean ownerEncontrado = false;
        if (qs != null) {
            for (QueryDocumentSnapshot d : qs) {
                Usuario u = fromUsuario(d, ownerUid);
                if (u.proprietario) ownerEncontrado = true;
                if (u.email.isEmpty() && u.proprietario) u.email = ownerEmail;
                out.add(u);
            }
        }
        if (!ownerEncontrado && !ownerUid.isEmpty()) {
            Usuario u = new Usuario();
            u.uid = ownerUid;
            u.nome = "Master";
            u.email = ownerEmail;
            u.perfil = "MASTER";
            u.proprietario = true;
            u.ativo = true;
            u.permissoes = TechCellPermissions.padrao("MASTER");
            out.add(u);
        }
        Collections.sort(out, (a,b)->{
            if (a.proprietario != b.proprietario) return a.proprietario ? -1 : 1;
            int pa = "GERENTE".equalsIgnoreCase(a.perfil) ? 0 : 1;
            int pb = "GERENTE".equalsIgnoreCase(b.perfil) ? 0 : 1;
            if (pa != pb) return Integer.compare(pa, pb);
            return a.nome.compareToIgnoreCase(b.nome);
        });
        return out;
    }

    /** Cria uma nova credencial Firebase sem derrubar a sessão do Desenvolvedor. */
    public static Usuario criarUsuario(Context context, String empresaUuid, String filialUuid,
                                       String nome, String email, String senha, String perfil) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        String n = nome == null ? "" : nome.trim();
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String p = normalizarPerfil(perfil);
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do usuário.");
        if (e.isEmpty() || !e.contains("@")) throw new IllegalArgumentException("Informe um e-mail válido.");
        if (senha == null || senha.length() < 6) throw new IllegalArgumentException("A senha inicial deve ter pelo menos 6 caracteres.");

        FirebaseApp secundario = null;
        FirebaseAuth authSec = null;
        FirebaseUser criado = null;
        try {
            String appName = "TECHCELL_DEV_CREATE_" + UUID.randomUUID();
            secundario = FirebaseApp.initializeApp(app, TechCellCloudSync.app(app).getOptions(), appName);
            authSec = FirebaseAuth.getInstance(secundario);
            AuthResult ar = Tasks.await(authSec.createUserWithEmailAndPassword(e, senha), TIMEOUT, TimeUnit.SECONDS);
            criado = ar.getUser();
            if (criado == null || criado.getUid() == null || criado.getUid().trim().isEmpty()) {
                throw new IllegalStateException("O Firebase não devolveu a identificação da nova conta.");
            }
            Usuario u = new Usuario();
            u.uid = criado.getUid(); u.nome = n; u.email = e; u.perfil = p; u.ativo = true; u.proprietario = false;
            u.permissoes = TechCellPermissions.padrao(p);
            gravarUsuario(app, empresaUuid, filialUuid, u, true);
            registrarAuditoria(app, empresaUuid, "USUARIO_CRIADO", "USUARIO", u.uid,
                    n + " • " + e + " • " + u.perfilExibicao());
            return u;
        } catch (Throwable erro) {
            if (criado != null) {
                try { Tasks.await(criado.delete(), 10L, TimeUnit.SECONDS); } catch (Throwable ignored) {}
            }
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundario != null) secundario.delete(); } catch (Throwable ignored) {}
        }
    }

    public static void editarUsuario(Context context, String empresaUuid, String filialUuid,
                                     Usuario u, String nome, String perfil, boolean ativo) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (u == null || u.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome.");
        String anterior = u.nome + " • " + u.perfilExibicao() + " • " + (u.ativo ? "ATIVO" : "BLOQUEADO");
        if (u.proprietario) {
            u.nome = n;
            u.perfil = "MASTER";
            u.ativo = true;
        } else {
            u.nome = n;
            u.perfil = normalizarPerfil(perfil);
            u.ativo = ativo;
        }
        gravarUsuario(app, empresaUuid, filialUuid, u, false);
        registrarAuditoria(app, empresaUuid, u.proprietario ? "MASTER_EDITADO" : "USUARIO_EDITADO", "USUARIO", u.uid,
                "Antes: " + anterior + " | Depois: " + u.nome + " • " + u.perfilExibicao() + " • " + (u.ativo ? "ATIVO" : "BLOQUEADO"));
    }

    public static void salvarPermissoes(Context context, String empresaUuid, Usuario u, Set<String> permissoes) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (u == null || u.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        if (u.proprietario) throw new IllegalStateException("O Master proprietário mantém acesso total.");
        DocumentReference ref = empresaRef(app, empresaUuid).collection("usuarios").document(u.uid);
        DocumentSnapshot d = Tasks.await(ref.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!d.exists()) throw new IllegalStateException("Usuário não encontrado.");
        Set<String> novo = permissoes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissoes);
        Map<String,Object> m = new HashMap<>();
        m.put("permissoes", TechCellPermissions.lista(novo));
        m.put("permissoes_modelo", u.perfil);
        m.put("permissions_updated_by_uid", developerUid(app));
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        u.permissoes = novo;
        registrarAuditoria(app, empresaUuid, "PERMISSOES_ALTERADAS", "USUARIO", u.uid,
                u.nome + " • " + TechCellPermissions.resumo(novo));
    }

    public static void enviarRedefinicaoSenha(Context context, String empresaUuid, Usuario u) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (u == null || u.email == null || u.email.trim().isEmpty()) throw new IllegalStateException("Conta sem e-mail.");
        Tasks.await(TechCellCloudSync.auth(app).sendPasswordResetEmail(u.email.trim()), TIMEOUT, TimeUnit.SECONDS);
        registrarAuditoria(app, empresaUuid, "REDEFINICAO_SENHA_ENVIADA", "USUARIO", u.uid,
                "Redefinição enviada para " + u.email);
    }

    public static void excluirUsuario(Context context, String empresaUuid, Usuario u) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        if (u == null || u.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        if (u.proprietario) throw new IllegalStateException("O Master proprietário não pode ser excluído. Use a gestão da licença ou uma futura transferência de propriedade.");
        WriteBatch b = TechCellCloudSync.firestore(app).batch();
        b.delete(empresaRef(app, empresaUuid).collection("usuarios").document(u.uid));
        b.delete(TechCellCloudSync.firestore(app).collection(INDEX).document(u.uid).collection("empresas").document(empresaUuid));
        Tasks.await(b.commit(), TIMEOUT, TimeUnit.SECONDS);
        registrarAuditoria(app, empresaUuid, "USUARIO_REMOVIDO", "USUARIO", u.uid,
                u.nome + " • " + u.email);
    }

    public static List<Dispositivo> listarDispositivos(Context context, String empresaUuid) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        QuerySnapshot qs = Tasks.await(TechCellCloudSync.firestore(app).collection(REQUESTS)
                .document(empresaUuid).collection("devices").get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<Dispositivo> out = new ArrayList<>();
        if (qs != null) for (QueryDocumentSnapshot d : qs) out.add(fromDevice(d));
        Collections.sort(out, new Comparator<Dispositivo>() {
            @Override public int compare(Dispositivo a, Dispositivo b) {
                int pa = prioridadeStatus(a.status), pb = prioridadeStatus(b.status);
                if (pa != pb) return Integer.compare(pa, pb);
                return Long.compare(b.solicitadoEm, a.solicitadoEm);
            }
        });
        return out;
    }

    public static void autorizarDispositivo(Context context, String empresaUuid, Dispositivo d) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        validarDevice(d);
        DocumentReference req = requestRef(app, empresaUuid, d.deviceUuid);
        boolean caixa = d.perfil == null || d.perfil.trim().isEmpty() || "CAIXA".equalsIgnoreCase(d.perfil);
        TechCellLicenseManager.Licenca lic = TechCellLicenseManager.lerServidor(app, empresaUuid);
        if (caixa && lic.existe) {
            if (!TechCellLicenseManager.ATIVA.equalsIgnoreCase(lic.status) || !lic.masterAtivo) {
                throw new IllegalStateException("Ative a licença e o Master antes de autorizar aparelhos Caixa.");
            }
            String slotId = d.licenseSlotId == null ? "" : d.licenseSlotId.trim();
            if (!slotId.isEmpty()) {
                DocumentSnapshot slot = Tasks.await(TechCellLicenseManager.slotsCaixa(app, empresaUuid).document(slotId)
                        .get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
                if (slot.exists() && TechCellLicenseManager.EM_USO.equalsIgnoreCase(texto(slot.get("status")))
                        && d.deviceUuid.equals(texto(slot.get("device_uuid")))) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("status", TechCellDeviceAuthorization.AUTORIZADO);
                    m.put("approved_by_uid", developerUid(app));
                    m.put("approved_at", FieldValue.serverTimestamp());
                    m.put("updated_at", FieldValue.serverTimestamp());
                    Tasks.await(req.set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
                    d.status = TechCellDeviceAuthorization.AUTORIZADO;
                    registrarAuditoria(app, empresaUuid, "DISPOSITIVO_AUTORIZADO", "DISPOSITIVO", d.deviceUuid, descricaoDevice(d));
                    return;
                }
            }
            QueryDocumentSnapshot livre = TechCellLicenseManager.primeiroSlotLivre(app, empresaUuid);
            if (livre == null) {
                int usados = TechCellLicenseManager.contarSlotsEmUso(app, empresaUuid);
                throw new IllegalStateException("Limite de licenças Caixa atingido: " + usados + " / " + lic.caixasContratados + ".");
            }
            DocumentReference slotRef = livre.getReference();
            Tasks.await(TechCellCloudSync.firestore(app).runTransaction(tx -> {
                DocumentSnapshot sr = tx.get(slotRef);
                DocumentSnapshot rr = tx.get(req);
                if (!sr.exists() || !TechCellLicenseManager.LIVRE.equalsIgnoreCase(texto(sr.get("status")))) {
                    throw new IllegalStateException("A vaga acabou de ser ocupada. Tente novamente.");
                }
                if (!rr.exists()) throw new IllegalStateException("Solicitação não encontrada.");
                Map<String,Object> sm = new HashMap<>();
                sm.put("status", TechCellLicenseManager.EM_USO);
                sm.put("device_uuid", d.deviceUuid);
                sm.put("uid", d.uid);
                sm.put("updated_at", FieldValue.serverTimestamp());
                tx.set(slotRef, sm, SetOptions.merge());
                Map<String,Object> rm = new HashMap<>();
                rm.put("status", TechCellDeviceAuthorization.AUTORIZADO);
                rm.put("license_slot_id", slotRef.getId());
                rm.put("approved_by_uid", developerUid(app));
                rm.put("approved_at", FieldValue.serverTimestamp());
                rm.put("updated_at", FieldValue.serverTimestamp());
                tx.set(req, rm, SetOptions.merge());
                return null;
            }), TIMEOUT, TimeUnit.SECONDS);
            d.licenseSlotId = slotRef.getId();
        } else {
            Map<String,Object> m = new HashMap<>();
            m.put("status", TechCellDeviceAuthorization.AUTORIZADO);
            m.put("approved_by_uid", developerUid(app));
            m.put("approved_at", FieldValue.serverTimestamp());
            m.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(req.set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        }
        d.status = TechCellDeviceAuthorization.AUTORIZADO;
        registrarAuditoria(app, empresaUuid, "DISPOSITIVO_AUTORIZADO", "DISPOSITIVO", d.deviceUuid, descricaoDevice(d));
    }

    public static void bloquearDispositivo(Context context, String empresaUuid, Dispositivo d) throws Exception {
        alterarStatusComLiberacao(context, empresaUuid, d, TechCellDeviceAuthorization.BLOQUEADO, "DISPOSITIVO_BLOQUEADO");
    }

    public static void negarDispositivo(Context context, String empresaUuid, Dispositivo d) throws Exception {
        alterarStatusComLiberacao(context, empresaUuid, d, TechCellDeviceAuthorization.NEGADO, "DISPOSITIVO_NEGADO");
    }

    private static void alterarStatusComLiberacao(Context context, String empresaUuid, Dispositivo d, String status, String acao) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        validarDevice(d);
        DocumentReference req = requestRef(app, empresaUuid, d.deviceUuid);
        DocumentSnapshot atual = Tasks.await(req.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        if (!atual.exists()) throw new IllegalStateException("Solicitação do aparelho não encontrada.");
        String slotId = texto(atual.get("license_slot_id"));
        WriteBatch b = TechCellCloudSync.firestore(app).batch();
        Map<String,Object> rm = new HashMap<>();
        rm.put("status", status);
        rm.put("license_slot_id", "");
        rm.put("updated_at", FieldValue.serverTimestamp());
        rm.put("updated_by_uid", developerUid(app));
        b.set(req, rm, SetOptions.merge());
        if (!slotId.isEmpty()) {
            DocumentReference sr = TechCellLicenseManager.slotsCaixa(app, empresaUuid).document(slotId);
            DocumentSnapshot sd = Tasks.await(sr.get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
            if (sd.exists() && d.deviceUuid.equals(texto(sd.get("device_uuid")))) {
                Map<String,Object> sm = new HashMap<>();
                sm.put("status", TechCellLicenseManager.LIVRE);
                sm.put("device_uuid", "");
                sm.put("uid", "");
                sm.put("updated_at", FieldValue.serverTimestamp());
                b.set(sr, sm, SetOptions.merge());
            }
        }
        Tasks.await(b.commit(), TIMEOUT, TimeUnit.SECONDS);
        d.status = status; d.licenseSlotId = "";
        registrarAuditoria(app, empresaUuid, acao, "DISPOSITIVO", d.deviceUuid, descricaoDevice(d));
    }

    public static void registrarAuditoria(Context context, String empresaUuid, String acao,
                                           String alvoTipo, String alvoId, String descricao) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        FirebaseUser autor = TechCellCloudSync.auth(app).getCurrentUser();
        if (autor == null) throw new IllegalStateException("Conta Desenvolvedor não conectada.");
        TechCellDeveloperAccess.Estado dev = TechCellDeveloperAccess.local(app);
        Map<String,Object> m = new HashMap<>();
        m.put("actor_uid", autor.getUid());
        m.put("actor_email", autor.getEmail() == null ? "" : autor.getEmail());
        m.put("actor_nome", dev.nome == null || dev.nome.trim().isEmpty() ? "Desenvolvedor" : dev.nome);
        m.put("actor_tipo", "DEVELOPER");
        m.put("acao", acao == null ? "" : acao);
        m.put("alvo_tipo", alvoTipo == null ? "" : alvoTipo);
        m.put("alvo_id", alvoId == null ? "" : alvoId);
        m.put("descricao", descricao == null ? "" : descricao);
        m.put("created_at", FieldValue.serverTimestamp());
        Tasks.await(empresaRef(app, empresaUuid).collection(AUDITORIA).add(m), TIMEOUT, TimeUnit.SECONDS);
    }

    public static List<EventoAuditoria> listarAuditoria(Context context, String empresaUuid) throws Exception {
        Context app = context.getApplicationContext();
        TechCellDeveloperAccess.exigir(app);
        QuerySnapshot qs = Tasks.await(empresaRef(app, empresaUuid).collection(AUDITORIA)
                .orderBy("created_at", Query.Direction.DESCENDING).limit(100).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
        List<EventoAuditoria> out = new ArrayList<>();
        if (qs != null) for (QueryDocumentSnapshot d : qs) {
            EventoAuditoria e = new EventoAuditoria();
            e.acao = texto(d.get("acao"));
            e.alvoTipo = texto(d.get("alvo_tipo"));
            e.alvoId = texto(d.get("alvo_id"));
            e.descricao = texto(d.get("descricao"));
            e.atorNome = texto(d.get("actor_nome"));
            e.atorEmail = texto(d.get("actor_email"));
            Object ts = d.get("created_at");
            if (ts instanceof com.google.firebase.Timestamp) e.criadoEm = ((com.google.firebase.Timestamp) ts).toDate().getTime();
            out.add(e);
        }
        return out;
    }

    private static void gravarUsuario(Context app, String empresaUuid, String filialUuid, Usuario u, boolean novo) throws Exception {
        DocumentReference ref = empresaRef(app, empresaUuid).collection("usuarios").document(u.uid);
        Map<String,Object> m = new HashMap<>();
        m.put("uid", u.uid);
        m.put("empresa_uuid", empresaUuid);
        m.put("filial_uuid", filialUuid == null || filialUuid.trim().isEmpty() ? empresaUuid : filialUuid);
        m.put("nome", u.nome);
        m.put("email", u.email == null ? "" : u.email);
        m.put("perfil", u.proprietario ? "MASTER" : normalizarPerfil(u.perfil));
        m.put("ativo", u.proprietario || u.ativo);
        m.put("proprietario", u.proprietario);
        if (!u.proprietario) m.put("permissoes", TechCellPermissions.lista(u.permissoes));
        m.put("updated_by_uid", developerUid(app));
        m.put("updated_at", FieldValue.serverTimestamp());
        if (novo) m.put("created_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);

        Map<String,Object> idx = new HashMap<>();
        idx.put("uid", u.uid);
        idx.put("empresa_uuid", empresaUuid);
        idx.put("filial_uuid", filialUuid == null || filialUuid.trim().isEmpty() ? empresaUuid : filialUuid);
        idx.put("nome", u.nome);
        idx.put("email", u.email == null ? "" : u.email);
        idx.put("perfil", u.proprietario ? "MASTER" : normalizarPerfil(u.perfil));
        idx.put("proprietario", u.proprietario);
        idx.put("ativo", u.proprietario || u.ativo);
        idx.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(TechCellCloudSync.firestore(app).collection(INDEX).document(u.uid)
                .collection("empresas").document(empresaUuid).set(idx, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
    }

    private static Usuario fromUsuario(DocumentSnapshot d, String ownerUid) {
        Usuario u = new Usuario();
        u.uid = texto(d.get("uid")); if (u.uid.isEmpty()) u.uid = d.getId();
        u.nome = texto(d.get("nome"));
        u.email = texto(d.get("email"));
        u.proprietario = u.uid.equals(ownerUid) || Boolean.TRUE.equals(d.get("proprietario"));
        u.perfil = u.proprietario ? "MASTER" : normalizarPerfil(texto(d.get("perfil")));
        Object a = d.get("ativo"); u.ativo = !(a instanceof Boolean) || (Boolean)a;
        u.permissoes = u.proprietario ? TechCellPermissions.padrao("MASTER") : TechCellPermissions.ler(d.get("permissoes"), u.perfil);
        if (u.nome.isEmpty()) u.nome = u.proprietario ? "Master" : (u.email.isEmpty() ? u.perfilExibicao() : u.email);
        return u;
    }

    private static Dispositivo fromDevice(DocumentSnapshot d) {
        Dispositivo x = new Dispositivo();
        x.deviceUuid = texto(d.get("device_uuid")); if (x.deviceUuid.isEmpty()) x.deviceUuid = d.getId();
        x.uid = texto(d.get("uid")); x.email = texto(d.get("email")); x.nomeUsuario = texto(d.get("nome_usuario"));
        x.perfil = texto(d.get("perfil")); x.nomeAparelho = texto(d.get("nome_aparelho")); x.status = texto(d.get("status"));
        if (x.status.isEmpty()) x.status = TechCellDeviceAuthorization.PENDENTE;
        x.licenseSlotId = texto(d.get("license_slot_id"));
        Object ts = d.get("requested_at"); if (ts instanceof com.google.firebase.Timestamp) x.solicitadoEm = ((com.google.firebase.Timestamp) ts).toDate().getTime();
        return x;
    }

    private static int prioridadeStatus(String s) {
        if (TechCellDeviceAuthorization.PENDENTE.equalsIgnoreCase(s)) return 0;
        if (TechCellDeviceAuthorization.AUTORIZADO.equalsIgnoreCase(s)) return 1;
        if (TechCellDeviceAuthorization.BLOQUEADO.equalsIgnoreCase(s)) return 2;
        return 3;
    }

    private static void validarDevice(Dispositivo d) {
        if (d == null || d.deviceUuid == null || d.deviceUuid.trim().isEmpty()) throw new IllegalArgumentException("Aparelho inválido.");
    }

    private static String descricaoDevice(Dispositivo d) {
        return (d.nomeAparelho == null || d.nomeAparelho.isEmpty() ? "Aparelho" : d.nomeAparelho)
                + " • " + (d.email == null ? "" : d.email) + " • " + d.status;
    }

    private static DocumentReference empresaRef(Context app, String empresaUuid) {
        if (empresaUuid == null || empresaUuid.trim().isEmpty()) throw new IllegalArgumentException("Empresa inválida.");
        return TechCellCloudSync.firestore(app).collection(EMPRESAS).document(empresaUuid.trim());
    }

    private static DocumentReference requestRef(Context app, String empresaUuid, String deviceUuid) {
        return TechCellCloudSync.firestore(app).collection(REQUESTS).document(empresaUuid).collection("devices").document(deviceUuid);
    }

    private static String developerUid(Context app) {
        FirebaseUser u = TechCellCloudSync.auth(app).getCurrentUser();
        if (u == null) throw new IllegalStateException("Conta Desenvolvedor não conectada.");
        return u.getUid();
    }

    private static String normalizarPerfil(String perfil) {
        String p = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        return "GERENTE".equals(p) ? "GERENTE" : "CAIXA";
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
