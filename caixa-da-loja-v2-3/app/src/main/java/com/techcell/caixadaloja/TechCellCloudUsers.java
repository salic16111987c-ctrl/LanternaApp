package com.techcell.caixadaloja;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
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

/** Gerenciamento das contas da empresa feito pelo Master proprietário. */
public final class TechCellCloudUsers {
    private static final String PROJECT_ID = "caixa-da-loja-5dd34";
    private static final String API_KEY = "AIzaSyAcMqWWeaEKfdjIST0NSwkXWWsbst6iY2k";
    private static final String APPLICATION_ID = "1:243340178302:web:09e0bc0265edc2d2cab92f";
    private static final String STORAGE_BUCKET = "caixa-da-loja-5dd34.firebasestorage.app";
    private static final String ROOT = "techcell_empresas";

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
        garantirProprietario(app, s);

        QuerySnapshot qs = Tasks.await(s.empresa.collection("usuarios").get(Source.SERVER));
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
        garantirProprietario(appContext, s);

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
            String appName = "TECHCELL_CREATE_" + UUID.randomUUID().toString();
            secundaria = FirebaseApp.initializeApp(appContext, TechCellCloudSync.app(appContext).getOptions(), appName);
            authSec = FirebaseAuth.getInstance(secundaria);
            AuthResult ar = Tasks.await(authSec.createUserWithEmailAndPassword(e, senha));
            criado = ar.getUser();
            if (criado == null || criado.getUid() == null || criado.getUid().trim().isEmpty()) {
                throw new IllegalStateException("O Firebase não devolveu a identificação da nova conta.");
            }

            Map<String,Object> dados = new HashMap<>();
            dados.put("uid", criado.getUid());
            dados.put("empresa_uuid", s.ctx.empresaUuid);
            dados.put("filial_uuid", s.ctx.filialUuid);
            dados.put("nome", n);
            dados.put("email", e);
            dados.put("perfil", p);
            dados.put("ativo", true);
            dados.put("proprietario", false);
            dados.put("created_by_uid", s.user.getUid());
            dados.put("created_at", FieldValue.serverTimestamp());
            dados.put("updated_at", FieldValue.serverTimestamp());
            Tasks.await(s.empresa.collection("usuarios").document(criado.getUid()).set(dados));

            Usuario u = new Usuario();
            u.uid = criado.getUid();
            u.nome = n;
            u.email = e;
            u.perfil = p;
            u.ativo = true;
            u.proprietario = false;
            return u;
        } catch (Throwable erro) {
            if (criado != null) {
                try { Tasks.await(criado.delete()); } catch (Throwable ignored) {}
            }
            if (erro instanceof Exception) throw (Exception) erro;
            throw new IllegalStateException(mensagem(erro), erro);
        } finally {
            try { if (authSec != null) authSec.signOut(); } catch (Throwable ignored) {}
            try { if (secundaria != null) secundaria.delete(); } catch (Throwable ignored) {}
        }
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
        Tasks.await(s.empresa.collection("usuarios").document(alvo.uid).set(v, SetOptions.merge()));
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
        Tasks.await(s.empresa.collection("usuarios").document(alvo.uid).set(v, SetOptions.merge()));
        alvo.ativo = ativo;
    }

    public static void enviarRedefinicaoSenha(Context context, String email) throws Exception {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) throw new IllegalArgumentException("Conta sem e-mail.");
        sessaoOwner(context.getApplicationContext());
        Tasks.await(TechCellCloudSync.auth(context).sendPasswordResetEmail(e));
    }

    public static void garantirProprietario(Context context) throws Exception {
        garantirProprietario(context.getApplicationContext(), sessaoOwner(context.getApplicationContext()));
    }

    private static void garantirProprietario(Context context, Sessao s) throws Exception {
        DocumentReference ref = s.empresa.collection("usuarios").document(s.user.getUid());
        DocumentSnapshot atual = Tasks.await(ref.get(Source.SERVER));
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
        Tasks.await(ref.set(v, SetOptions.merge()));
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
        if (!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            throw new IllegalStateException("Usuários da nuvem só podem ser administrados pelo aparelho Master.");
        }
        FirebaseUser user = TechCellCloudSync.auth(context).getCurrentUser();
        if (user == null) throw new IllegalStateException("Entre na conta Master da nuvem.");

        DocumentReference empresa = TechCellCloudSync.firestore(context)
                .collection(ROOT).document(ctx.empresaUuid);
        DocumentSnapshot d = Tasks.await(empresa.get(Source.SERVER));
        if (!d.exists()) throw new IllegalStateException("Primeiro registre esta empresa na Nuvem Tech Cell.");
        String owner = d.getString("owner_uid");
        if (owner == null || !owner.equals(user.getUid())) {
            throw new IllegalStateException("Somente a conta Master proprietária desta empresa pode administrar usuários.");
        }

        Sessao s = new Sessao();
        s.ctx = ctx;
        s.user = user;
        s.empresa = empresa;
        return s;
    }

    private static FirebaseOptions options() {
        return new FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApiKey(API_KEY)
                .setApplicationId(APPLICATION_ID)
                .setStorageBucket(STORAGE_BUCKET)
                .build();
    }

    public static String mensagem(Throwable e) {
        String base = TechCellCloudSync.mensagemCloud(e);
        String lower = base == null ? "" : base.toLowerCase(Locale.ROOT);
        if (lower.contains("email address is already in use") || lower.contains("email-already-in-use") || lower.contains("already in use")) {
            return "Este e-mail já possui uma conta cadastrada. Use Entrar na conta existente.";
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
        return base == null || base.trim().isEmpty() ? "Erro desconhecido." : base;
    }
}
