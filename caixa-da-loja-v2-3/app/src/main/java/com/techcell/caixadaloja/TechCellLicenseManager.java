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
        public boolean existe; public String empresaUuid=""; public String status="LEGADO"; public String plano="LEGADO";
        public boolean masterAtivo=true; public int caixasContratados; public int gerentesContratados; public long atualizadoEm;
        public boolean ativa(){return !existe||ATIVA.equalsIgnoreCase(status);}
    }
    private TechCellLicenseManager(){}
    public static DocumentReference ref(Context c,String e){return TechCellCloudSync.firestore(c).collection(LICENCAS).document(e);}
    public static CollectionReference slotsCaixa(Context c,String e){return ref(c,e).collection(SLOTS_CAIXA);}

    public static Licenca lerServidor(Context c,String e)throws Exception{
        if(e==null||e.trim().isEmpty())throw new IllegalArgumentException("Empresa sem identificação.");
        DocumentSnapshot d=Tasks.await(ref(c,e).get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);Licenca l=fromDoc(e,d);salvarCache(c,l);return l;
    }
    public static Licenca lerLocal(Context c,String e){
        Licenca l=new Licenca();l.empresaUuid=e==null?"":e;SharedPreferences p=prefs(c);if(!l.empresaUuid.equals(p.getString("empresa_uuid","")))return l;
        if(!p.getBoolean("conhecida",false))return l;l.existe=p.getBoolean("existe",false);l.status=p.getString("status",l.existe?PENDENTE:"LEGADO");l.plano=p.getString("plano",l.existe?"LOCAL":"LEGADO");l.masterAtivo=p.getBoolean("master_ativo",!l.existe);l.caixasContratados=p.getInt("caixas_contratados",0);l.gerentesContratados=p.getInt("gerentes_contratados",0);l.atualizadoEm=p.getLong("updated_at",0L);return l;
    }
    public static Licenca validarAcesso(Context c,String e,boolean proprietario)throws Exception{
        if(TechCellDeveloperAccess.ehDesenvolvedor(c))return lerServidor(c,e);
        Licenca l=lerServidor(c,e);if(!l.existe)return l;if(!ATIVA.equalsIgnoreCase(l.status))throw new IllegalStateException(mensagemStatus(l.status));if(proprietario&&!l.masterAtivo)throw new IllegalStateException("A licença Master desta empresa ainda não foi liberada pelo Desenvolvedor.");return l;
    }
    public static boolean permiteSessaoLocal(Context c,String e,boolean proprietario){
        if(TechCellDeveloperAccess.ehDesenvolvedor(c))return true;SharedPreferences p=prefs(c);if(!e.equals(p.getString("empresa_uuid",""))||!p.getBoolean("conhecida",false))return true;if(!p.getBoolean("existe",false))return true;if(!ATIVA.equalsIgnoreCase(p.getString("status",PENDENTE)))return false;return !proprietario||p.getBoolean("master_ativo",false);
    }
    public static void criarPendenteSeAusente(Context c,String e)throws Exception{
        FirebaseUser u=TechCellCloudSync.auth(c).getCurrentUser();if(u==null)throw new IllegalStateException("Conta Master não conectada.");DocumentReference r=ref(c,e);DocumentSnapshot d=Tasks.await(r.get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);if(d.exists()){salvarCache(c,fromDoc(e,d));return;}
        Map<String,Object> m=new HashMap<>();m.put("empresa_uuid",e);m.put("status",PENDENTE);m.put("plano","LOCAL");m.put("master_ativo",false);m.put("caixas_contratados",0);m.put("gerentes_contratados",0);m.put("requested_by_uid",u.getUid());m.put("created_at",FieldValue.serverTimestamp());m.put("updated_at",FieldValue.serverTimestamp());Tasks.await(r.set(m,SetOptions.merge()),TIMEOUT,TimeUnit.SECONDS);
        Licenca l=new Licenca();l.existe=true;l.empresaUuid=e;l.status=PENDENTE;l.plano="LOCAL";l.masterAtivo=false;salvarCache(c,l);
    }
    public static int contarSlotsEmUso(Context c,String e)throws Exception{QuerySnapshot qs=Tasks.await(slotsCaixa(c,e).whereEqualTo("status",EM_USO).get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);return qs==null?0:qs.size();}
    public static QueryDocumentSnapshot primeiroSlotLivre(Context c,String e)throws Exception{QuerySnapshot qs=Tasks.await(slotsCaixa(c,e).whereEqualTo("status",LIVRE).limit(1).get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);return qs==null||qs.isEmpty()?null:qs.iterator().next();}
    public static boolean slotValidoParaDispositivo(Context c,String e,String slotId,String device,String uid)throws Exception{
        if(slotId==null||slotId.trim().isEmpty())return false;DocumentSnapshot s=Tasks.await(slotsCaixa(c,e).document(slotId).get(Source.SERVER),TIMEOUT,TimeUnit.SECONDS);
        return s.exists()&&EM_USO.equalsIgnoreCase(texto(s.get("status")))&&device.equals(texto(s.get("device_uuid")))&&uid.equals(texto(s.get("uid")));
    }
    public static String mensagemStatus(String s){String v=s==null?"":s.trim().toUpperCase();if(PENDENTE.equals(v))return"Esta empresa está aguardando liberação da licença pelo Desenvolvedor.";if(SUSPENSA.equals(v))return"A licença desta empresa está suspensa. Procure o responsável pelo sistema.";if(CANCELADA.equals(v))return"A licença desta empresa foi cancelada.";return"A licença desta empresa não está ativa.";}
    private static Licenca fromDoc(String e,DocumentSnapshot d){Licenca l=new Licenca();l.empresaUuid=e;l.existe=d!=null&&d.exists();if(!l.existe)return l;l.status=texto(d.get("status"));if(l.status.isEmpty())l.status=PENDENTE;l.plano=texto(d.get("plano"));if(l.plano.isEmpty())l.plano="LOCAL";l.masterAtivo=bool(d.get("master_ativo"),false);l.caixasContratados=inteiro(d.get("caixas_contratados"));l.gerentesContratados=inteiro(d.get("gerentes_contratados"));Object t=d.get("updated_at");if(t instanceof com.google.firebase.Timestamp)l.atualizadoEm=((com.google.firebase.Timestamp)t).toDate().getTime();return l;}
    private static void salvarCache(Context c,Licenca l){prefs(c).edit().putString("empresa_uuid",l.empresaUuid).putBoolean("conhecida",true).putBoolean("existe",l.existe).putString("status",l.status).putString("plano",l.plano).putBoolean("master_ativo",l.masterAtivo).putInt("caixas_contratados",l.caixasContratados).putInt("gerentes_contratados",l.gerentesContratados).putLong("updated_at",System.currentTimeMillis()).apply();}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}private static boolean bool(Object v,boolean p){return v instanceof Boolean?(Boolean)v:p;}private static int inteiro(Object v){return v instanceof Number?Math.max(0,((Number)v).intValue()):0;}private static String texto(Object v){return v==null?"":String.valueOf(v).trim();}
}
