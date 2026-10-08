package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tela do Master para aprovar, negar e bloquear aparelhos. */
public class TechCellDeviceApprovalsActivity extends Activity {
    private LinearLayout lista;
    private boolean ocupado;
    private final SimpleDateFormat data = new SimpleDateFormat("dd/MM HH:mm", new Locale("pt","BR"));

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);
        if(!TechCellAccess.podeAdministrar(this)){
            new AlertDialog.Builder(this).setTitle("Acesso restrito").setMessage("Somente o Master pode autorizar aparelhos.")
                    .setPositiveButton("Voltar",(d,w)->finish()).setCancelable(false).show();return;
        }
        render();carregar();
    }
    @Override protected void onResume(){super.onResume();if(lista!=null&&!ocupado)carregar();}

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button voltar=botao("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        TextView titulo=txt("Dispositivos / Autorizações",25,true);titulo.setPadding(0,dp(16),0,dp(3));root.addView(titulo);
        TextView sub=txt("Nenhum aparelho novo recebe os dados da empresa sem sua autorização.",12,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView it=txt("SEGURANÇA DO APARELHO",12,true);it.setTextColor(TechCellUi.GREEN);info.addView(it);
        TextView iv=txt("E-mail e senha identificam a pessoa. Esta tela autoriza o aparelho. Para cada solicitação você pode Aprovar, Negar ou gerar um código temporário de 6 dígitos.",11,false);iv.setTextColor(TechCellUi.MUTED);iv.setPadding(0,dp(5),0,0);info.addView(iv);root.addView(info);

        Button atualizar=botao("↻  Atualizar solicitações");TechCellUi.stylePrimary(this,atualizar,TechCellUi.BLUE);atualizar.setOnClickListener(v->carregar());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));ap.setMargins(0,0,0,dp(8));root.addView(atualizar,ap);
        lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);setContentView(scroll);
    }

    private void carregar(){
        if(ocupado||lista==null)return;ocupado=true;lista.removeAllViews();
        TextView t=txt("Consultando a nuvem…",13,true);t.setPadding(0,dp(12),0,0);lista.addView(t);
        new Thread(()->{
            try{List<TechCellDeviceAuthorization.Dispositivo> ds=TechCellDeviceAuthorization.listar(getApplicationContext());runOnUiThread(()->{ocupado=false;mostrar(ds);});}
            catch(Throwable e){runOnUiThread(()->{ocupado=false;lista.removeAllViews();erro("Não foi possível carregar os aparelhos",e);});}
        },"TechCell-DeviceApproval-List").start();
    }

    private void mostrar(List<TechCellDeviceAuthorization.Dispositivo> ds){
        lista.removeAllViews();
        int pendentes=0;if(ds!=null)for(TechCellDeviceAuthorization.Dispositivo d:ds)if(d.pendente())pendentes++;
        TextView resumo=txt(pendentes>0?"⚠  "+pendentes+" solicitação(ões) aguardando sua decisão":"✓  Nenhuma solicitação pendente",13,true);
        resumo.setTextColor(pendentes>0?TechCellUi.ORANGE:TechCellUi.GREEN);resumo.setGravity(Gravity.CENTER);resumo.setPadding(dp(8),dp(10),dp(8),dp(10));lista.addView(resumo);
        if(ds==null||ds.isEmpty()){TextView vazio=txt("Ainda não há aparelhos registrados.",12,false);vazio.setTextColor(TechCellUi.MUTED);vazio.setPadding(0,dp(14),0,0);lista.addView(vazio);return;}
        for(TechCellDeviceAuthorization.Dispositivo d:ds)card(d);
    }

    private void card(TechCellDeviceAuthorization.Dispositivo d){
        LinearLayout c=TechCellUi.card(this);c.setLayoutParams(TechCellUi.fullCardParams(this,9));
        String ic=d.pendente()?"⚠  ":(d.autorizado()?"✓  ":"⛔  ");
        c.addView(txt(ic+(d.nomeAparelho==null||d.nomeAparelho.isEmpty()?"Aparelho Android":d.nomeAparelho),16,true));
        String nome=(d.nomeUsuario==null||d.nomeUsuario.trim().isEmpty()?d.email:d.nomeUsuario);
        String quando=d.solicitadoEm>0?data.format(new Date(d.solicitadoEm)):"—";
        TextView det=txt(nome+"\n"+d.email+" • "+(d.perfil==null?"":d.perfil)+"\nStatus: "+d.status+" • solicitado: "+quando+"\nID: "+curto(d.deviceUuid),11,false);
        det.setTextColor(TechCellUi.MUTED);det.setPadding(0,dp(4),0,dp(8));c.addView(det);

        if(d.pendente()){
            Button aprovar=botao("✓  AUTORIZAR ESTE APARELHO");TechCellUi.stylePrimary(this,aprovar,TechCellUi.GREEN);aprovar.setOnClickListener(v->confirmarAprovar(d));c.addView(aprovar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
            Button codigo=botao("🔑  Gerar código alternativo");TechCellUi.styleSecondary(this,codigo);codigo.setOnClickListener(v->gerarCodigo(d));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));cp.setMargins(0,dp(7),0,0);c.addView(codigo,cp);
            Button negar=botao("Negar solicitação");TechCellUi.styleSecondary(this,negar);negar.setOnClickListener(v->confirmarNegar(d));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));np.setMargins(0,dp(7),0,0);c.addView(negar,np);
        }else if(d.autorizado()){
            Button bloquear=botao("⛔  Bloquear este aparelho");TechCellUi.stylePrimary(this,bloquear,TechCellUi.RED);bloquear.setOnClickListener(v->confirmarBloquear(d));c.addView(bloquear,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        }else{
            Button re=botao("✓  Reautorizar aparelho");TechCellUi.stylePrimary(this,re,TechCellUi.GREEN);re.setOnClickListener(v->confirmarAprovar(d));c.addView(re,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        }
        lista.addView(c);
    }

    private void confirmarAprovar(TechCellDeviceAuthorization.Dispositivo d){
        new AlertDialog.Builder(this).setTitle("Autorizar aparelho?")
                .setMessage("Usuário: "+d.email+"\nAparelho: "+d.nomeAparelho+"\n\nApós autorizar, este aparelho poderá carregar os dados conforme as permissões do usuário.")
                .setPositiveButton("AUTORIZAR",(x,w)->executar("Aparelho autorizado",()->TechCellDeviceAuthorization.aprovar(getApplicationContext(),d)))
                .setNegativeButton("Cancelar",null).show();
    }

    private void confirmarNegar(TechCellDeviceAuthorization.Dispositivo d){
        new AlertDialog.Builder(this).setTitle("Negar solicitação?").setMessage("O aparelho não poderá entrar na empresa.")
                .setPositiveButton("Negar",(x,w)->executar("Solicitação negada",()->TechCellDeviceAuthorization.negar(getApplicationContext(),d)))
                .setNegativeButton("Cancelar",null).show();
    }

    private void confirmarBloquear(TechCellDeviceAuthorization.Dispositivo d){
        new AlertDialog.Builder(this).setTitle("Bloquear aparelho?").setMessage("A próxima validação desse aparelho será recusada. Você poderá reautorizar depois.")
                .setPositiveButton("BLOQUEAR",(x,w)->executar("Aparelho bloqueado",()->TechCellDeviceAuthorization.bloquear(getApplicationContext(),d)))
                .setNegativeButton("Cancelar",null).show();
    }

    private void gerarCodigo(TechCellDeviceAuthorization.Dispositivo d){
        if(ocupado)return;ocupado=true;
        new Thread(()->{
            try{TechCellDeviceAuthorization.Codigo c=TechCellDeviceAuthorization.gerarCodigo(getApplicationContext(),d);runOnUiThread(()->{ocupado=false;TextView code=txt(c.valor,30,true);code.setGravity(Gravity.CENTER);code.setTextIsSelectable(true);new AlertDialog.Builder(this).setTitle("Código temporário").setMessage("Passe este código somente para a pessoa que está com o aparelho solicitado.\n\nValidade: 10 minutos.").setView(code).setPositiveButton("OK",null).show();});}
            catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível gerar o código",e);});}
        },"TechCell-DeviceApproval-Code").start();
    }

    private interface Acao{void run() throws Exception;}
    private void executar(String sucesso,Acao a){
        if(ocupado)return;ocupado=true;
        new Thread(()->{try{a.run();runOnUiThread(()->{ocupado=false;Toast.makeText(this,sucesso,Toast.LENGTH_LONG).show();carregar();});}
        catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível concluir",e);});}},"TechCell-DeviceApproval-Action").start();
    }

    private void erro(String titulo,Throwable e){String m=TechCellCloudUsers.mensagem(e);new AlertDialog.Builder(this).setTitle(titulo).setMessage(m).setPositiveButton("OK",null).show();}
    private String curto(String id){if(id==null)return "—";String x=id.replace("-","");return x.substring(0,Math.min(10,x.length())).toUpperCase(Locale.ROOT);}
}
