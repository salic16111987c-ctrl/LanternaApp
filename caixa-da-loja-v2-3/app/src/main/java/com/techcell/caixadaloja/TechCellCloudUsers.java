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
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Gerenciamento das contas da empresa feito pela conta Administrador proprietária. */
public final class TechCellCloudUsers {
    private static final String ROOT = "techcell_empresas";
    private static final long READ_TIMEOUT_SECONDS = 6L;
    private static final long WRITE_TIMEOUT_SECONDS = 20L;

    public static class Usuario {
        public String uid = "";
        public String nome = "";
        public String email = "";
        public String perfil = "CAIXA";
        public boolean ativo = true;
        public boolean proprietario;

        public String perfilExibicao() {
            if (proprietario) return "Master proprietário";
            if ("MASTER".equalsIgnoreCase(perfil) || "ADMINISTRADOR".equalsIgnoreCase(perfil)) return "Master";
            if ("GERENTE".equalsIgnoreCase(perfil)) return "Gerente";
            return "Caixa";
        }
    }

    private TechCellCloudUsers() {}

    public static List<Usuario> listar(Context context) throws Exception {
        Context app = context.getApplicationContext();
        Sessao s = sessaoOwner(app);

        QuerySnapshot qs = listarUsuarios(s.empresa);
        List<Usuario> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : qs) out.add(fromDoc(d, s.user.getUid()));
        Collections.sort(out, new Comparator<Usuario>() {
            @Override public int compare(Usuario a, Usuario b) {
                if (a.proprietario != b.proprietario) return a.proprietario ? -1 : 1;
                String na = a.nome == null ? "" : a.nome;
                String nb = b.nome == null ? "" : b.nome;
                return na.compareToIgnoreCase(nb);
            }
        });
        return out;
    }

    public static Usuario criar(Context context, String nome, String email, String senha, String perfil) throws Exception {
        Context appContext = context.getApplicationContext();
        Sessao s = sessaoOwner(appContext);

        String n = nome == null ? "" : nome.trim();
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String p = normalizarPerfil(perfil);
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do usuário.");
        if (e.isEmpty() || !e.contains("@")) throw new IllegalArgumentException("Informe um e-mail válido.");
        if (senha == null || senha.length() < 6) throw new IllegalArgumentException("A senha inicial deve ter pelo menos 6 caracteres.");

        FirebaseApp secundaria = null;
        FirebaseAuth authSec = null;
        FirebaseUser criado = null;
        try {
            String appName = "TECHCELL_CREATE_" + UUID.randomUUID();
            secundaria = FirebaseApp.initializeApp(appContext, TechCellCloudSync.app(appContext).getOptions(), appName);
            authSec = FirebaseAuth.getInstance(secundaria);
            AuthResult ar = Tasks.await(authSec.createUserWithEmailAndPassword(e, senha), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            criado = ar.getUser();
            if (criado == null || criado.getUid() == null || criado.getUid().trim().isEmpty()) {
                throw new IllegalStateException("O Firebase não devolveu a identificação da nova conta.");
            }

            return gravarUsuario(s, criado.getUid(), n, e, p, true, "created_by_uid", "created_at");
        } catch (Throwable erro) {
            if (criado != null) {
                try { Tasks.await(criado.delete(), 10L, TimeUnit.SECONDS); } catch (Throwable ignored) {}
            }
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(mensagem(erro), erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundaria != null) secundaria.delete(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Vincula uma conta que já existe no Firebase Authentication a esta empresa.
     * A senha é usada somente para confirmar a identidade na instância secundária e nunca é salva.
     */
    public static Usuario vincularExistente(Context context, String nome, String email, String senha, String perfil) throws Exception {
        Context appContext = context.getApplicationContext();
        Sessao s = sessaoOwner(appContext);

        String n = nome == null ? "" : nome.trim();
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String p = normalizarPerfil(perfil);
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do usuário.");
        if (e.isEmpty() || !e.contains("@")) throw new IllegalArgumentException("Informe um e-mail válido.");
        if (senha == null || senha.isEmpty()) throw new IllegalArgumentException("Informe a senha atual desta conta para vinculá-la.");

        FirebaseApp secundaria = null;
        FirebaseAuth authSec = null;
        try {
            String appName = "TECHCELL_LINK_" + UUID.randomUUID();
            secundaria = FirebaseApp.initializeApp(appContext, TechCellCloudSync.app(appContext).getOptions(), appName);
            authSec = FirebaseAuth.getInstance(secundaria);
            AuthResult ar = Tasks.await(authSec.signInWithEmailAndPassword(e, senha), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            FirebaseUser existente = ar.getUser();
            if (existente == null || existente.getUid() == null || existente.getUid().trim().isEmpty()) {
                throw new IllegalStateException("Não foi possível identificar a conta existente.");
            }
            if (existente.getUid().equals(s.user.getUid())) {
                throw new IllegalStateException("Esta é a conta Master proprietária da loja e já está vinculada.");
            }
            String emailReal = existente.getEmail() == null ? e : existente.getEmail().trim().toLowerCase(Locale.ROOT);
            return gravarUsuario(s, existente.getUid(), n, emailReal, p, true, "linked_by_uid", "linked_at");
        } catch (Throwable erro) {
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(mensagem(erro), erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundaria != null) secundaria.delete(); } catch (Throwable ignored) {}
        }
    }

    private static Usuario gravarUsuario(Sessao s, String uid, String nome, String email, String perfil,
                                         boolean ativo, String campoAutor, String campoData) throws Exception {
        DocumentReference ref = s.empresa.collection("usuarios").document(uid);
        DocumentSnapshot atual = lerDocumento(ref);
        Map<String,Object> dados = new HashMap<>();
        dados.put("uid", uid);
        dados.put("empresa_uuid", s.ctx.empresaUuid);
        dados.put("filial_uuid", s.ctx.filialUuid);
        dados.put("nome", nome);
        dados.put("email", email);
        dados.put("perfil", perfil);
        dados.put("ativo", ativo);
        dados.put("proprietario", false);
        dados.put(campoAutor, s.user.getUid());
        if (!atual.exists()) dados.put(campoData, FieldValue.serverTimestamp());
        dados.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(dados, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Usuario u = new Usuario();
        u.uid = uid;
        u.nome = nome;
        u.email = email;
        u.perfil = perfil;
        u.ativo = ativo;
        u.proprietario = false;
        return u;
    }

    /** Edita nome, nível e status de uma conta secundária. */
    public static void editar(Context context, Usuario alvo, String nome, String perfil, boolean ativo) throws Exception {
        if (alvo == null || alvo.uid == null || alvo.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        Sessao s = sessaoOwner(context.getApplicationContext());
        if (alvo.uid.equals(s.user.getUid()) || alvo.proprietario) {
            throw new IllegalStateException("Use a opção Editar nome do Master para alterar a conta proprietária.");
        }
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do usuário.");
        String p = normalizarPerfil(perfil);
        DocumentReference ref = s.empresa.collection("usuarios").document(alvo.uid);
        DocumentSnapshot atual = lerDocumento(ref);
        if (!atual.exists()) throw new IllegalStateException("Este usuário não está mais vinculado à empresa. Atualize a lista.");

        Map<String,Object> v = new HashMap<>();
        v.put("nome", n);
        v.put("perfil", p);
        v.put("ativo", ativo);
        v.put("updated_at", FieldValue.serverTimestamp());
        v.put("updated_by_uid", s.user.getUid());
        Tasks.await(ref.set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        DocumentSnapshot confirmado = Tasks.await(ref.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirmado.exists()) throw new IllegalStateException("A atualização não foi confirmada pela nuvem.");
        alvo.nome = string(confirmado.get("nome"));
        alvo.perfil = normalizarPerfil(string(confirmado.get("perfil")));
        Object valorAtivo = confirmado.get("ativo");
        alvo.ativo = !(valorAtivo instanceof Boolean) || (Boolean) valorAtivo;
    }

    /** Permite ao proprietário editar apenas o nome de exibição, preservando MASTER e ativo. */
    public static void editarNomeProprietario(Context context, String nome) throws Exception {
        Sessao s = sessaoOwner(context.getApplicationContext());
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do Master.");

        DocumentReference ref = s.empresa.collection("usuarios").document(s.user.getUid());
        Map<String,Object> v = new HashMap<>();
        v.put("uid", s.user.getUid());
        v.put("empresa_uuid", s.ctx.empresaUuid);
        v.put("filial_uuid", s.ctx.filialUuid);
        v.put("nome", n);
        v.put("email", s.user.getEmail() == null ? "" : s.user.getEmail());
        v.put("perfil", "MASTER");
        v.put("ativo", true);
        v.put("proprietario", true);
        v.put("updated_at", FieldValue.serverTimestamp());
        v.put("updated_by_uid", s.user.getUid());
        Tasks.await(ref.set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Remove o usuário desta empresa. O Firebase Authentication não permite que um
     * cliente Master apague arbitrariamente a credencial de outra pessoa; portanto
     * removemos o vínculo da loja. Sem o documento em usuarios/{uid}, a conta deixa
     * de validar acesso à empresa e pode ser vinculada novamente no futuro.
     */
    public static void excluirDaEmpresa(Context context, Usuario alvo) throws Exception {
        if (alvo == null || alvo.uid == null || alvo.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        Sessao s = sessaoOwner(context.getApplicationContext());
        if (alvo.uid.equals(s.user.getUid()) || alvo.proprietario) {
            throw new IllegalStateException("A conta Master proprietária não pode ser excluída.");
        }
        DocumentReference ref = s.empresa.collection("usuarios").document(alvo.uid);
        DocumentSnapshot atual = lerDocumento(ref);
        if (!atual.exists()) return;
        Tasks.await(ref.delete(), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        DocumentSnapshot confirmado = Tasks.await(ref.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (confirmado.exists()) throw new IllegalStateException("A exclusão não foi confirmada pela nuvem.");
        alvo.ativo = false;
    }

    public static void alterarPerfil(Context context, Usuario alvo, String perfil) throws Exception {
        if (alvo == null || alvo.uid == null || alvo.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        Sessao s = sessaoOwner(context.getApplicationContext());
        if (alvo.uid.equals(s.user.getUid()) || alvo.proprietario) {
            throw new IllegalStateException("O nível da conta Master proprietária não pode ser alterado.");
        }
        String p = normalizarPerfil(perfil);
        Map<String,Object> v = new HashMap<>();
        v.put("perfil", p);
        v.put("updated_at", FieldValue.serverTimestamp());
        v.put("updated_by_uid", s.user.getUid());
        Tasks.await(s.empresa.collection("usuarios").document(alvo.uid).set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        alvo.perfil = p;
    }

    public static void alterarAtivo(Context context, Usuario alvo, boolean ativo) throws Exception {
        if (alvo == null || alvo.uid == null || alvo.uid.trim().isEmpty()) throw new IllegalArgumentException("Usuário inválido.");
        Sessao s = sessaoOwner(context.getApplicationContext());
        if (alvo.uid.equals(s.user.getUid()) || alvo.proprietario) {
            throw new IllegalStateException("A conta Master proprietária não pode ser bloqueada pelo próprio Master.");
        }
        Map<String,Object> v = new HashMap<>();
        v.put("ativo", ativo);
        v.put("updated_at", FieldValue.serverTimestamp());
        v.put("updated_by_uid", s.user.getUid());
        Tasks.await(s.empresa.collection("usuarios").document(alvo.uid).set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        alvo.ativo = ativo;
    }

    public static void enviarRedefinicaoSenha(Context context, String email) throws Exception {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) throw new IllegalArgumentException("Conta sem e-mail.");
        sessaoOwner(context.getApplicationContext());
        Tasks.await(TechCellCloudSync.auth(context).sendPasswordResetEmail(e), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void garantirProprietario(Context context) throws Exception {
        garantirProprietario(context.getApplicationContext(), sessaoOwner(context.getApplicationContext()));
    }

    private static void garantirProprietario(Context context, Sessao s) throws Exception {
        DocumentReference ref = s.empresa.collection("usuarios").document(s.user.getUid());
        DocumentSnapshot atual = lerDocumento(ref);
        Map<String,Object> v = new HashMap<>();
        v.put("uid", s.user.getUid());
        v.put("empresa_uuid", s.ctx.empresaUuid);
        v.put("filial_uuid", s.ctx.filialUuid);
        v.put("email", s.user.getEmail() == null ? "" : s.user.getEmail());
        v.put("perfil", "MASTER");
        v.put("ativo", true);
        v.put("proprietario", true);
        if (!atual.exists()) {
            v.put("nome", "Master");
            v.put("created_at", FieldValue.serverTimestamp());
        }
        v.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(ref.set(v, SetOptions.merge()), WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static Usuario fromDoc(DocumentSnapshot d, String ownerUid) {
        Usuario u = new Usuario();
        u.uid = string(d.get("uid"));
        if (u.uid.isEmpty()) u.uid = d.getId();
        u.nome = string(d.get("nome"));
        u.email = string(d.get("email"));
        u.perfil = normalizarPerfil(string(d.get("perfil")));
        Object ativo = d.get("ativo");
        u.ativo = !(ativo instanceof Boolean) || (Boolean) ativo;
        Object prop = d.get("proprietario");
        u.proprietario = (prop instanceof Boolean && (Boolean) prop) || u.uid.equals(ownerUid);
        if (u.proprietario) u.perfil = "MASTER";
        if (u.nome.isEmpty()) u.nome = u.email.isEmpty() ? "Usuário" : u.email;
        return u;
    }

    private static String normalizarPerfil(String perfil) {
        String p = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        if ("MASTER".equals(p) || "ADMINISTRADOR".equals(p)) return "MASTER";
        if ("GERENTE".equals(p)) return "GERENTE";
        return "CAIXA";
    }

    private static String string(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private static class Sessao {
        GestaoDbHelper.SyncContext ctx;
        FirebaseUser user;
        DocumentReference empresa;
    }

    private static Sessao sessaoOwner(Context context) throws Exception {
        GestaoDbHelper db = new GestaoDbHelper(context);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }
        if (!ctx.configurado || ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
            throw new IllegalStateException("Este aparelho ainda não está vinculado a uma empresa.");
        }
        FirebaseUser user = TechCellCloudSync.auth(context).getCurrentUser();
        if (user == null) throw new IllegalStateException("Entre na conta Administrador da nuvem.");

        DocumentReference empresa = TechCellCloudSync.firestore(context)
                .collection(ROOT).document(ctx.empresaUuid);
        DocumentSnapshot d = lerDocumento(empresa);
        if (!d.exists()) throw new IllegalStateException("Primeiro registre esta empresa na Nuvem Tech Cell.");
        String owner = d.getString("owner_uid");
        if (owner == null || !owner.equals(user.getUid())) {
            throw new IllegalStateException("Somente a conta Administrador proprietária desta empresa pode administrar usuários.");
        }

        Sessao s = new Sessao();
        s.ctx = ctx;
        s.user = user;
        s.empresa = empresa;
        return s;
    }

    private static DocumentSnapshot lerDocumento(DocumentReference ref) throws Exception {
        Throwable servidor = null;
        try {
            return Tasks.await(ref.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable e) {
            servidor = e;
        }
        try {
            DocumentSnapshot cache = Tasks.await(ref.get(Source.CACHE), 2L, TimeUnit.SECONDS);
            if (cache != null && cache.exists()) return cache;
        } catch (Throwable ignored) {}
        if (servidor instanceof Exception) throw (Exception) servidor;
        throw new IllegalStateException("Não foi possível consultar a nuvem.", servidor);
    }

    private static QuerySnapshot listarUsuarios(DocumentReference empresa) throws Exception {
        Throwable servidor = null;
        try {
            return Tasks.await(empresa.collection("usuarios").get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable e) {
            servidor = e;
        }
        try {
            return Tasks.await(empresa.collection("usuarios").get(Source.CACHE), 2L, TimeUnit.SECONDS);
        } catch (Throwable ignored) {}
        if (servidor instanceof Exception) throw (Exception) servidor;
        throw new IllegalStateException("Não foi possível carregar as contas da nuvem.", servidor);
    }

    public static String mensagem(Throwable e) {
        String base = TechCellCloudSync.mensagemCloud(e);
        String lower = base == null ? "" : base.toLowerCase(Locale.ROOT);
        if (lower.contains("email address is already in use") || lower.contains("email-already-in-use") || lower.contains("already in use")) {
            return "Este e-mail já existe no Firebase. Use a opção Vincular conta existente.";
        }
        if (lower.contains("invalid-credential") || lower.contains("wrong-password") || lower.contains("invalid password") || lower.contains("password is invalid")) {
            return "E-mail ou senha atual da conta existente estão incorretos.";
        }
        if (lower.contains("user-not-found")) {
            return "Esta conta ainda não existe no Firebase. Use Cadastrar usuário.";
        }
        if (lower.contains("password") && lower.contains("6")) {
            return "A senha inicial deve ter pelo menos 6 caracteres.";
        }
        if (lower.contains("badly formatted") || lower.contains("invalid-email")) {
            return "O e-mail informado não é válido.";
        }
        if (lower.contains("api key not valid")) {
            return "A configuração do Firebase deste APK está inválida. Instale a versão mais recente do Tech Cell.";
        }
        if (lower.contains("failed to get document from server") || lower.contains("unavailable") || lower.contains("timeout")) {
            return "A nuvem demorou para responder. O app continua localmente; verifique a internet e tente novamente.";
        }
        return base == null || base.trim().isEmpty() ? "Erro desconhecido." : base;
    }
}
