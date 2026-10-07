package com.techcell.caixadaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Sessão e permissões dos usuários da loja.
 * O perfil fica na nuvem, mas uma cópia é mantida localmente para a operação
 * continuar funcionando quando a internet cair depois de uma autenticação válida.
 */
public final class TechCellAccess {
    private static final String ROOT = "techcell_empresas";
    private static final String PREF = "techcell_access_v1";
    private static final long READ_TIMEOUT_SECONDS = 12L;

    public enum Perfil { MASTER, GERENTE, CAIXA, NENHUM }

    public static class Sessao {
        public boolean valida;
        public boolean ativo;
        public String uid = "";
        public String nome = "";
        public String email = "";
        public String empresaUuid = "";
        public Perfil perfil = Perfil.NENHUM;
    }

    private TechCellAccess() {}

    public static boolean controleAtivo(Context context) {
        GestaoDbHelper db = new GestaoDbHelper(context.getApplicationContext());
        try { return db.getSyncContext().cloudAtiva; }
        finally { db.close(); }
    }

    public static Sessao sessao(Context context) {
        Sessao s = new Sessao();
        Context app = context.getApplicationContext();
        GestaoDbHelper db = new GestaoDbHelper(app);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }

        if (!ctx.cloudAtiva) {
            s.valida = true;
            s.ativo = true;
            s.nome = "Desenvolvimento";
            s.perfil = Perfil.MASTER;
            s.empresaUuid = ctx.empresaUuid == null ? "" : ctx.empresaUuid;
            return s;
        }

        FirebaseUser atual;
        try { atual = TechCellCloudSync.auth(app).getCurrentUser(); }
        catch (Throwable e) { atual = null; }
        if (atual == null) return s;

        SharedPreferences p = prefs(app);
        String uid = p.getString("uid", "");
        String empresa = p.getString("empresa_uuid", "");
        String empresaAtual = ctx.empresaUuid == null ? "" : ctx.empresaUuid;
        if (!atual.getUid().equals(uid) || !empresaAtual.equals(empresa)) return s;

        s.uid = uid;
        s.empresaUuid = empresa;
        s.nome = p.getString("nome", "");
        s.email = p.getString("email", atual.getEmail() == null ? "" : atual.getEmail());
        s.ativo = p.getBoolean("ativo", false);
        s.perfil = normalizar(p.getString("perfil", ""));
        s.valida = s.ativo && s.perfil != Perfil.NENHUM;
        return s;
    }

    public static boolean temSessaoValida(Context context) { return sessao(context).valida; }
    public static Perfil perfilAtual(Context context) { return sessao(context).perfil; }

    public static Sessao atualizarDaNuvem(Context context) throws Exception {
        Context app = context.getApplicationContext();
        GestaoDbHelper db = new GestaoDbHelper(app);
        GestaoDbHelper.SyncContext ctx;
        try { ctx = db.getSyncContext(); }
        finally { db.close(); }

        if (!ctx.configurado) {
            throw new IllegalStateException("Este aparelho ainda não foi configurado. No primeiro uso, configure-o como Caixa e faça o pareamento com o Master na mesma rede Wi-Fi.");
        }
        if (ctx.empresaUuid == null || ctx.empresaUuid.trim().isEmpty()) {
            throw new IllegalStateException("Este aparelho ainda não possui uma empresa vinculada. Faça o pareamento com o Master antes do primeiro login.");
        }
        if (!"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
            String masterId = ctx.masterDeviceUuid == null ? "" : ctx.masterDeviceUuid.trim();
            if (masterId.isEmpty()) {
                throw new IllegalStateException("Este aparelho ainda não está vinculado ao Master. Conecte os dois aparelhos à mesma rede Wi-Fi, abra Dispositivo e rede e faça o pareamento antes de entrar com a conta do Caixa.");
            }
        }

        FirebaseUser user = TechCellCloudSync.auth(app).getCurrentUser();
        if (user == null) throw new IllegalStateException("Entre com seu e-mail e senha.");

        DocumentReference empresa = TechCellCloudSync.firestore(app)
                .collection(ROOT).document(ctx.empresaUuid);
        DocumentSnapshot emp = Tasks.await(empresa.get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!emp.exists()) throw new IllegalStateException("Esta empresa ainda não foi registrada na Nuvem Tech Cell.");

        String owner = texto(emp.get("owner_uid"));
        Sessao s = new Sessao();
        s.uid = user.getUid();
        s.email = user.getEmail() == null ? "" : user.getEmail();
        s.empresaUuid = ctx.empresaUuid;

        if (user.getUid().equals(owner)) {
            s.perfil = Perfil.MASTER;
            s.ativo = true;
            DocumentSnapshot ud = null;
            try {
                ud = Tasks.await(empresa.collection("usuarios").document(user.getUid()).get(Source.SERVER),
                        READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Throwable ignored) {}
            if (ud != null && ud.exists()) {
                s.nome = texto(ud.get("nome"));
                Object ativo = ud.get("ativo");
                if (ativo instanceof Boolean) s.ativo = (Boolean) ativo;
            }
            if (s.nome.isEmpty()) s.nome = "Master";
        } else {
            DocumentSnapshot ud = Tasks.await(empresa.collection("usuarios")
                    .document(user.getUid()).get(Source.SERVER), READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!ud.exists()) throw new IllegalStateException("Esta conta não está vinculada a esta empresa.");
            Object ativo = ud.get("ativo");
            s.ativo = !(ativo instanceof Boolean) || (Boolean) ativo;
            s.perfil = normalizar(texto(ud.get("perfil")));
            s.nome = texto(ud.get("nome"));
            if (s.nome.isEmpty()) s.nome = s.email;
        }

        if (!s.ativo) throw new IllegalStateException("Esta conta está bloqueada pelo Master.");
        if (s.perfil == Perfil.NENHUM) throw new IllegalStateException("Esta conta está sem nível de acesso válido.");
        salvar(app, s);
        s.valida = true;
        return s;
    }

    public static void encerrar(Context context) {
        try { TechCellCloudSync.auth(context).signOut(); } catch (Throwable ignored) {}
        prefs(context).edit().clear().apply();
    }

    public static boolean podeVender(Context c) { return permitido(c, Perfil.CAIXA); }
    public static boolean podeClientes(Context c) { return permitido(c, Perfil.CAIXA); }
    public static boolean podeProdutos(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeEstoque(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeFinanceiro(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeRelatorios(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeFornecedores(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeHistorico(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeResumoFinanceiro(Context c) { return permitido(c, Perfil.GERENTE); }
    public static boolean podeAdministrar(Context c) { return perfilAtual(c) == Perfil.MASTER; }

    public static String perfilExibicao(Perfil p) {
        if (p == Perfil.MASTER) return "Master";
        if (p == Perfil.GERENTE) return "Gerente";
        if (p == Perfil.CAIXA) return "Caixa";
        return "Sem acesso";
    }

    private static boolean permitido(Context c, Perfil minimo) {
        Perfil p = perfilAtual(c);
        if (p == Perfil.MASTER) return true;
        if (minimo == Perfil.CAIXA) return p == Perfil.GERENTE || p == Perfil.CAIXA;
        if (minimo == Perfil.GERENTE) return p == Perfil.GERENTE;
        return false;
    }

    private static Perfil normalizar(String valor) {
        String p = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if ("MASTER".equals(p) || "ADMINISTRADOR".equals(p) || "PROPRIETARIO".equals(p) || "PROPRIETÁRIO".equals(p)) return Perfil.MASTER;
        if ("GERENTE".equals(p)) return Perfil.GERENTE;
        if ("CAIXA".equals(p)) return Perfil.CAIXA;
        return Perfil.NENHUM;
    }

    private static void salvar(Context context, Sessao s) {
        prefs(context).edit()
                .putString("uid", s.uid)
                .putString("empresa_uuid", s.empresaUuid)
                .putString("nome", s.nome)
                .putString("email", s.email)
                .putString("perfil", s.perfil.name())
                .putBoolean("ativo", s.ativo)
                .putLong("validado_em", System.currentTimeMillis())
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v).trim(); }
}
