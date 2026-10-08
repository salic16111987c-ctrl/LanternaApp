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

/** Autorização de aparelhos novos pelo Master, respeitando as vagas compradas no painel do Desenvolvedor. */
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
        public String licenseSlotId = "";
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
        m.put("license_slot_id", "");
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
        Context app = context.getApplicationContext();
        OwnerSession s = owner(app);
        validarEmpresa(d, s.empresaUuid);

        if (usaLicencaCaixa(d)) {
            TechCellLicenseManager.Licenca lic = TechCellLicenseManager.lerServidor(app, s.empresaUuid);
            if (lic.existe) {
                validarLicencaParaCaixa(lic);
                autorizarComSlot(app, s, d);
                d.status = AUTORIZADO;
                return;
            }
        }

        Map<String,Object> m = new HashMap<>();
        m.put("status", AUTORIZADO);
        m.put("approved_by_uid", s.user.getUid());
        m.put("approved_at", FieldValue.serverTimestamp());
        m.put("updated_at", FieldValue.serverTimestamp());
        Tasks.await(requestRef(app, s.empresaUuid, d.deviceUuid).set(m, SetOptions.merge()), TIMEOUT, TimeUnit.SECONDS);
        d.status = AUTORIZADO;
    }

    private static void autorizarComSlot(Context app, OwnerSession s, Dispositivo d) throws Exception {
        if (d.licenseSlotId != null && !d.licenseSlotId.trim().isEmpty()) {
            DocumentSnapshot slotAtual = Tasks.await(TechCellLicenseManager.slotsCaixa(app, s.empresaUuid)
                    .document(d.licenseSlotId).get(Source.SERVER), TIMEOUT, TimeUnit.SECONDS);
            if (slotAtual.exists() && TechCellLicenseManager.EM_USO.equalsIgnoreCase(texto(slotAtual.get("status")))
                    && d.deviceUuid.equals(texto(slotAtual.get("device_uuid")))) {
                Map<String,Object> m = new HashMap<>();
                m.put("status", AUTORIZADO);m.put("approved_by_uid",s.user.getUid());m.put("approved_at",FieldValue.serverTimestamp());m.put("updated_at",FieldValue.serverTimestamp());
                Tasks.await(requestRef(app,s.empresaUuid,d.deviceUuid).set(m,SetOptions.merge()),TIMEOUT,TimeUnit.SECONDS);
                return;
            }
        }

        QueryDocumentSnapshot livre = TechCellLicenseManager.primeiroSlotLivre(app, s.empresaUuid);
        if (livre == null) {
            TechCellLicenseManager.Licenca lic = TechCellLicenseManager.lerServidor(app, s.empresaUuid);
            int usados = TechCellLicenseManager.contarSlotsEmUso(app, s.empresaUuid);
            throw new IllegalStateException("Limite de licenças Caixa atingido: " + usados + " / " + lic.caixasContratados +
                    ". O Desenvolvedor precisa aumentar a quantidade de licenças desta empresa.");
        }

        DocumentReference slotRef = livre.getReference();
        DocumentReference reqRef = requestRef(app, s.empresaUuid, d.deviceUuid);
        DocumentReference licRef = TechCellLicenseManager.ref(app, s.empresaUuid);
        Tasks.await(TechCellCloudSync.firestore(app).runTransaction(tx->{
            DocumentSnapshot lic = tx.get(licRef);
            DocumentSnapshot slot = tx.get(slotRef);
            DocumentSnapshot req = tx.get(reqRef);
            if (!lic.exists() || !TechCellLicenseManager.ATIVA.equalsIgnoreCase(texto(lic.get("status")))
                    || !bool(lic.get("master_ativo"), false)) throw new IllegalStateException("A licença da empresa não permite autorizar aparelhos.");
            if (!slot.exists() || !TechCellLicenseManager.LIVRE.equalsIgnoreCase(texto(slot.get("status")))) {
                throw new IllegalStateException("A vaga selecionada acabou de ser ocupada. Tente novamente.");
            }
            if (!req.exists() || !d.uid.equals(texto(req.get("uid")))) throw new IllegalStateException("Solicitação do aparelho não encontrada.");

            Map<String,Object> sm=new HashMap<>();sm.put("status",TechCellLicenseManager.EM_USO);sm.put("device_uuid",d.deviceUuid);sm.put("uid",d.uid);sm.put("updated_at",FieldValue.serverTimestamp());
            tx.set(slotRef,sm,SetOptions.merge());
            Map<String,Object> rm=new HashMap<>();rm.put("status",AUTORIZADO);rm.put("license_slot_id",slotRef.getId());rm.put("approved_by_uid",s.user.getUid());rm.put("approved_at",FieldValue.serverTimestamp());rm.put("updated_at",FieldValue.serverTimestamp());
            tx.set(reqRef,rm,SetOptions.merge());
            return null;
        }), TIMEOUT, TimeUnit.SECONDS);
        d.licenseSlotId = slotRef.getId();
    }

    public static void negar(Context context, Dispositivo d) throws Exception {
        Context app = context.getApplicationContext();OwnerSession s = owner(app);validarEmpresa(d, s.empresaUuid);
        liberarSlotEAlterar(app,s,d,NEGADO,"denied_by_uid","denied_at");d.status = NEGADO;
    }

    public static void bloquear(Context context, Dispositivo d) throws Exception {
        Context app = context.getApplicationContext();OwnerSession s = owner(app);validarEmpresa(d, s.empresaUuid);
        liberarSlotEAlterar(app,s,d,BLOQUEADO,"blocked_by_uid","blocked_at");d.status = BLOQUEADO;
    }

    private static void liberarSlotEAlterar(Context app, OwnerSession s, Dispositivo d, String novoStatus, String campoUid, String campoData) throws Exception {
        DocumentReference reqRef=requestRef(app,s.empresaUuid,d.deviceUuid);
        String slotId=d.licenseSlotId==null?"":d.licenseSlotId.trim();
        if(slotId.isEmpty()){
            DocumentSnapshot atual=Tasks.await(reqRef.get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);slotId=texto(atual.get("license_slot_id"));
        }
        if(slotId.isEmpty()){
            Map<String,Object> m=new HashMap<>();m.put("status",novoStatus);m.put(campoUid,s.user.getUid());m.put(campoData,FieldValue.serverTimestamp());m.put("updated_at",FieldValue.serverTimestamp());
            Tasks.await(reqRef.set(m,SetOptions.merge()),TIMEOUT,TimeUnit.SECONDS);d.licenseSlotId="";return;
        }
        DocumentReference slotRef=TechCellLicenseManager.slotsCaixa(app,s.empresaUuid).document(slotId);
        final String sid=slotId;
        Tasks.await(TechCellCloudSync.firestore(app).runTransaction(tx->{
            DocumentSnapshot req=tx.get(reqRef);DocumentSnapshot slot=tx.get(slotRef);
            Map<String,Object> rm=new HashMap<>();rm.put("status",novoStatus);rm.put("license_slot_id","");rm.put(campoUid,s.user.getUid());rm.put(campoData,FieldValue.serverTimestamp());rm.put("updated_at",FieldValue.serverTimestamp());tx.set(reqRef,rm,SetOptions.merge());
            if(slot.exists()&&d.deviceUuid.equals(texto(slot.get("device_uuid")))){
                Map<String,Object> sm=new HashMap<>();sm.put("status",TechCellLicenseManager.LIVRE);sm.put("device_uuid","");sm.put("uid","");sm.put("updated_at",FieldValue.serverTimestamp());tx.set(slotRef,sm,SetOptions.merge());
            }
            return null;
        }),TIMEOUT,TimeUnit.SECONDS);
        d.licenseSlotId="";
    }

    public static void reautorizar(Context context, Dispositivo d) throws Exception { aprovar(context, d); }

    /** Código alternativo: em empresa licenciada, a vaga é reservada antes de gerar o código. */
    public static Codigo gerarCodigo(Context context, Dispositivo d) throws Exception {
        Context app=context.getApplicationContext();OwnerSession s=owner(app);validarEmpresa(d,s.empresaUuid);
        if(usaLicencaCaixa(d)){
            TechCellLicenseManager.Licenca lic=TechCellLicenseManager.lerServidor(app,s.empresaUuid);
            if(lic.existe){validarLicencaParaCaixa(lic);reservarSlotParaCodigo(app,s,d);}
        }
        String codigo = String.format(Locale.ROOT, "%06d", RNG.nextInt(1_000_000));
        long expira = System.currentTimeMillis() + CODE_VALID_MS;
        Map<String,Object> m = new HashMap<>();m.put("empresa_uuid",s.empresaUuid);m.put("device_uuid",d.deviceUuid);m.put("code",codigo);m.put("expires_at",new Timestamp(new Date(expira)));m.put("created_by_uid",s.user.getUid());m.put("created_at",FieldValue.serverTimestamp());
        Tasks.await(codeRef(app,s.empresaUuid,d.deviceUuid).set(m),TIMEOUT,TimeUnit.SECONDS);
        Codigo c=new Codigo();c.valor=codigo;c.expiraEm=expira;return c;
    }

    private static void reservarSlotParaCodigo(Context app,OwnerSession s,Dispositivo d)throws Exception{
        if(d.licenseSlotId!=null&&!d.licenseSlotId.trim().isEmpty())return;
        QueryDocumentSnapshot livre=TechCellLicenseManager.primeiroSlotLivre(app,s.empresaUuid);
        if(livre==null){TechCellLicenseManager.Licenca lic=TechCellLicenseManager.lerServidor(app,s.empresaUuid);int usados=TechCellLicenseManager.contarSlotsEmUso(app,s.empresaUuid);throw new IllegalStateException("Limite de licenças Caixa atingido: "+usados+" / "+lic.caixasContratados+".");}
        DocumentReference slotRef=livre.getReference();DocumentReference reqRef=requestRef(app,s.empresaUuid,d.deviceUuid);
        Tasks.await(TechCellCloudSync.firestore(app).runTransaction(tx->{
            DocumentSnapshot slot=tx.get(slotRef);DocumentSnapshot req=tx.get(reqRef);
            if(!slot.exists()||!TechCellLicenseManager.LIVRE.equalsIgnoreCase(texto(slot.get("status"))))throw new IllegalStateException("A vaga acabou de ser ocupada. Tente novamente.");
            if(!req.exists())throw new IllegalStateException("Solicitação não encontrada.");
            Map<String,Object> sm=new HashMap<>();sm.put("status",TechCellLicenseManager.EM_USO);sm.put("device_uuid",d.deviceUuid);sm.put("uid",d.uid);sm.put("updated_at",FieldValue.serverTimestamp());tx.set(slotRef,sm,SetOptions.merge());
            Map<String,Object> rm=new HashMap<>();rm.put("license_slot_id",slotRef.getId());rm.put("license_reserved_at",FieldValue.serverTimestamp());rm.put("updated_at",FieldValue.serverTimestamp());tx.set(reqRef,rm,SetOptions.merge());return null;
        }),TIMEOUT,TimeUnit.SECONDS);d.licenseSlotId=slotRef.getId();
    }

    /** O próprio usuário confirma o código; a regra do Firestore valida segredo, validade e vaga. */
    public static void usarCodigo(Context context, String empresaUuid, String deviceUuid, String codigo) throws Exception {
        FirebaseUser user = TechCellCloudSync.auth(context).getCurrentUser();if(user==null)throw new IllegalStateException("Conta não conectada.");
        String c=codigo==null?"":codigo.replaceAll("[^0-9]","");if(c.length()!=6)throw new IllegalArgumentException("Informe os 6 dígitos do código.");
        Map<String,Object> m=new HashMap<>();m.put("status",AUTORIZADO);m.put("provided_code",c);m.put("code_used_at",FieldValue.serverTimestamp());m.put("updated_at",FieldValue.serverTimestamp());
        Tasks.await(requestRef(context,empresaUuid,deviceUuid).set(m,SetOptions.merge()),TIMEOUT,TimeUnit.SECONDS);
        Dispositivo d=consultar(context,empresaUuid,deviceUuid);if(d==null||!d.autorizado()||!user.getUid().equals(d.uid))throw new IllegalStateException("O código não liberou este aparelho.");
    }

    private static void validarLicencaParaCaixa(TechCellLicenseManager.Licenca lic){
        if(!TechCellLicenseManager.ATIVA.equalsIgnoreCase(lic.status))throw new IllegalStateException(TechCellLicenseManager.mensagemStatus(lic.status));
        if(!lic.masterAtivo)throw new IllegalStateException("A licença Master da empresa não está liberada pelo Desenvolvedor.");
        if(lic.caixasContratados<=0)throw new IllegalStateException("Esta empresa não possui licença Caixa contratada. O Desenvolvedor precisa liberar pelo menos 1 Caixa.");
    }
    private static boolean usaLicencaCaixa(Dispositivo d){String p=d==null?"":(d.perfil==null?"":d.perfil.trim());return p.isEmpty()||"CAIXA".equalsIgnoreCase(p);}

    private static OwnerSession owner(Context context) throws Exception {
        GestaoDbHelper db=new GestaoDbHelper(context);GestaoDbHelper.SyncContext ctx;try{ctx=db.getSyncContext();}finally{db.close();}
        if(ctx.empresaUuid==null||ctx.empresaUuid.trim().isEmpty())throw new IllegalStateException("Empresa não configurada.");
        FirebaseUser u=TechCellCloudSync.auth(context).getCurrentUser();if(u==null)throw new IllegalStateException("Entre na conta Master.");
        DocumentSnapshot empresa=Tasks.await(TechCellCloudSync.firestore(context).collection(ROOT).document(ctx.empresaUuid).get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);
        if(!empresa.exists()||!u.getUid().equals(texto(empresa.get("owner_uid"))))throw new IllegalStateException("Somente o Master proprietário pode autorizar aparelhos.");
        TechCellLicenseManager.validarAcesso(context,ctx.empresaUuid,true);
        OwnerSession s=new OwnerSession();s.empresaUuid=ctx.empresaUuid;s.user=u;return s;
    }

    static DocumentReference requestRef(Context context,String empresa,String device){return TechCellCloudSync.firestore(context).collection(REQUESTS).document(empresa).collection("devices").document(device);}
    private static DocumentReference codeRef(Context context,String empresa,String device){return TechCellCloudSync.firestore(context).collection(CODES).document(empresa).collection("devices").document(device);}

    private static void validarEmpresa(Dispositivo d,String empresa){if(d==null||d.deviceUuid==null||d.deviceUuid.trim().isEmpty())throw new IllegalArgumentException("Aparelho inválido.");if(d.empresaUuid!=null&&!d.empresaUuid.trim().isEmpty()&&!empresa.equals(d.empresaUuid))throw new IllegalStateException("Este aparelho pertence a outra empresa.");}
    private static int prioridade(String status){if(PENDENTE.equalsIgnoreCase(status))return 0;if(AUTORIZADO.equalsIgnoreCase(status))return 1;if(BLOQUEADO.equalsIgnoreCase(status))return 2;return 3;}

    private static Dispositivo fromDoc(DocumentSnapshot d){
        Dispositivo x=new Dispositivo();x.deviceUuid=texto(d.get("device_uuid"));if(x.deviceUuid.isEmpty())x.deviceUuid=d.getId();x.uid=texto(d.get("uid"));x.email=texto(d.get("email"));x.nomeUsuario=texto(d.get("nome_usuario"));x.perfil=texto(d.get("perfil"));x.nomeAparelho=texto(d.get("nome_aparelho"));x.empresaUuid=texto(d.get("empresa_uuid"));x.status=texto(d.get("status"));if(x.status.isEmpty())x.status=PENDENTE;x.licenseSlotId=texto(d.get("license_slot_id"));x.solicitadoEm=millis(d.get("requested_at"));x.atualizadoEm=millis(d.get("updated_at"));return x;
    }
    private static long millis(Object v){if(v instanceof Timestamp)return((Timestamp)v).toDate().getTime();if(v instanceof Number)return((Number)v).longValue();return 0L;}
    private static boolean bool(Object v,boolean padrao){return v instanceof Boolean?(Boolean)v:padrao;}
    private static String texto(Object v){return v==null?"":String.valueOf(v).trim();}
}
