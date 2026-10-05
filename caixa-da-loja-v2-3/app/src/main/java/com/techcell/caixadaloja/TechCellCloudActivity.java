package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TechCellCloudActivity extends Activity {
    private boolean ocupado;
    private int dp(int v){ return TechCellUi.dp(this,v); }

    private TextView txt(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(TechCellUi.TEXT);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;
    }
    private Button action(String s){
        Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(0);return b;
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{ TechCellCloudSync.app(this); }
        catch(Throwable e){ mostrarErro("Firebase não iniciou",TechCellCloudSync.mensagemCloud(e)); }
        render();
    }

    @Override protected void onResume(){ super.onResume(); if(!ocupado)render(); }

    private void render(){
        TechCellUi.applyWindowChrome(this);
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx;
        int pendentes;
        try{ctx=db.getSyncContext();pendentes=db.countSyncPendentes();}finally{db.close();}
        TechCellCloudSync.Estado estado=TechCellCloudSync.estado(this);
        boolean master=ctx.configurado&&"MASTER".equalsIgnoreCase(ctx.papelDispositivo);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(30));scroll.addView(root);

        Button voltar=action("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());
        root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));
        TextView titulo=txt("Nuvem Tech Cell",27,true);titulo.setPadding(0,dp(15),0,dp(2));root.addView(titulo);
        TextView sub=txt("Fase 1 • empresa, Master e cadastros",12,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,11));
        TextView it=txt("LOCAL CONTINUA FUNCIONANDO",12,true);it.setTextColor(TechCellUi.GREEN);info.addView(it);
        TextView id=txt("A nuvem é uma camada adicional. O PDV e o Master continuam usando o banco local mesmo sem internet. Nesta fase não mexemos nas coleções antigas do Firebase.",12,false);
        id.setTextColor(Color.parseColor("#475467"));id.setPadding(0,dp(5),0,0);info.addView(id);root.addView(info);

        LinearLayout status=TechCellUi.card(this);status.setLayoutParams(TechCellUi.fullCardParams(this,8));
        status.addView(txt("Status",15,true));
        String papel=ctx.configurado?ctx.papelDispositivo:"NÃO CONFIGURADO";
        TextView sv=txt(
                "Aparelho: "+papel+"\n"+
                "Empresa: "+curto(ctx.empresaUuid)+"\n"+
                "Conta: "+(estado.autenticado?estado.email:"não conectada")+"\n"+
                "Nuvem local: "+(estado.cloudAtiva?"ATIVA ✓":"ainda não ativada")+"\n"+
                "Alterações locais pendentes: "+pendentes+"\n"+
                "Último sucesso: "+data(estado.ultimoSucesso)+
                (estado.ultimaCargaCadastros>0?"\nÚltima carga: "+estado.ultimaCargaCadastros+" cadastro(s)":"")+
                (estado.ultimoErro==null||estado.ultimoErro.trim().isEmpty()?"":"\nÚltimo erro: "+estado.ultimoErro),
                12,false);
        sv.setTextColor(TechCellUi.NAVY);sv.setPadding(0,dp(7),0,0);status.addView(sv);root.addView(status);

        if(!master){
            LinearLayout alerta=TechCellUi.card(this);alerta.setBackground(TechCellUi.solid(this,Color.parseColor("#FFF1F0"),14));
            TextView a=txt("A configuração inicial da nuvem deve ser feita no MASTER.",12,true);a.setTextColor(TechCellUi.RED);alerta.addView(a);
            root.addView(alerta,TechCellUi.fullCardParams(this,8));
        }

        FirebaseUser user=null;
        try{user=TechCellCloudSync.auth(this).getCurrentUser();}catch(Throwable ignored){}
        if(user==null){
            adicionarLogin(root,master);
        }else{
            adicionarOperacoes(root,master,estado);
        }

        setContentView(scroll);
    }

    private void adicionarLogin(LinearLayout root,boolean master){
        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
        card.addView(txt("Entrar no Firebase",16,true));
        TextView obs=txt("Use a conta autorizada para esta loja. A senha não é salva pelo aplicativo.",11,false);obs.setTextColor(TechCellUi.MUTED);obs.setPadding(0,dp(3),0,dp(8));card.addView(obs);
        EditText email=new EditText(this);email.setHint("E-mail");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);
        Button entrar=action("☁  Entrar na nuvem");TechCellUi.stylePrimary(this,entrar,TechCellUi.BLUE);entrar.setEnabled(master&&!ocupado);entrar.setAlpha(master&&!ocupado?1f:0.55f);
        entrar.setOnClickListener(v->{
            String e=email.getText().toString().trim(),s=senha.getText().toString();
            if(e.isEmpty()||s.isEmpty()){Toast.makeText(this,"Informe e-mail e senha.",Toast.LENGTH_LONG).show();return;}
            ocupado=true;entrar.setEnabled(false);entrar.setText("Entrando…");
            FirebaseAuth auth=TechCellCloudSync.auth(this);
            auth.signInWithEmailAndPassword(e,s).addOnCompleteListener(this,t->{
                ocupado=false;
                if(t.isSuccessful()){Toast.makeText(this,"Conta conectada.",Toast.LENGTH_SHORT).show();render();}
                else{mostrarErro("Não foi possível entrar",TechCellCloudSync.mensagemCloud(t.getException()));render();}
            });
        });
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));bp.setMargins(0,dp(10),0,0);card.addView(entrar,bp);root.addView(card);
    }

    private void adicionarOperacoes(LinearLayout root,boolean master,TechCellCloudSync.Estado estado){
        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
        card.addView(txt("Configuração da empresa",16,true));
        TextView aviso=txt("Primeiro confirme empresa + Master. Depois envie a carga inicial de produtos, clientes, fornecedores e despesas.",11,false);aviso.setTextColor(TechCellUi.MUTED);aviso.setPadding(0,dp(4),0,dp(8));card.addView(aviso);

        Button testar=action("1. Testar conexão / registrar empresa");TechCellUi.styleSecondary(this,testar);habilitar(testar,master);
        testar.setOnClickListener(v->executar("Conexão com a nuvem",()->TechCellCloudSync.testarRegistrarEmpresa(getApplicationContext())));
        card.addView(testar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        Button carga=action("2. Enviar carga inicial de cadastros");TechCellUi.stylePrimary(this,carga,TechCellUi.BLUE);habilitar(carga,master);
        carga.setOnClickListener(v->confirmarCarga());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));cp.setMargins(0,dp(8),0,0);card.addView(carga,cp);

        Button pend=action("Sincronizar cadastros pendentes agora");TechCellUi.styleSecondary(this,pend);habilitar(pend,master&&estado.cloudAtiva);
        pend.setOnClickListener(v->executar("Sincronização manual",()->TechCellCloudSync.sincronizarCadastrosPendentes(getApplicationContext())));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));pp.setMargins(0,dp(8),0,0);card.addView(pend,pp);
        root.addView(card);

        LinearLayout prox=TechCellUi.card(this);prox.setLayoutParams(TechCellUi.fullCardParams(this,8));
        TextView pt=txt("Próxima etapa da nuvem",13,true);pt.setTextColor(TechCellUi.NAVY);prox.addView(pt);
        TextView pv=txt("Depois de validarmos esta conexão e a carga de cadastros no Firebase, conectaremos vendas/histórico e a sincronização automática em segundo plano. Isso evita colocar milhares de vendas na nuvem antes de confirmar a fundação.",11,false);pv.setTextColor(TechCellUi.MUTED);pv.setPadding(0,dp(4),0,0);prox.addView(pv);root.addView(prox);

        Button sair=action("Sair da conta da nuvem");TechCellUi.styleSecondary(this,sair);sair.setOnClickListener(v->{TechCellCloudSync.auth(this).signOut();render();});
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));sp.setMargins(0,dp(9),0,0);root.addView(sair,sp);
    }

    private void confirmarCarga(){
        new AlertDialog.Builder(this).setTitle("Enviar cadastros para a nuvem?")
                .setMessage("Serão enviados produtos, clientes, fornecedores, despesas e a configuração da empresa desta base de teste.\n\nAs coleções antigas do Firebase não serão alteradas. Vendas entram na próxima etapa.")
                .setPositiveButton("Enviar carga",(d,w)->executar("Carga inicial",()->TechCellCloudSync.enviarCargaInicialCadastros(getApplicationContext())))
                .setNegativeButton("Cancelar",null).show();
    }

    private interface Operacao { TechCellCloudSync.Resultado rodar(); }
    private void executar(String titulo,Operacao op){
        if(ocupado)return;ocupado=true;render();
        new Thread(()->{
            TechCellCloudSync.Resultado r=op.rodar();
            runOnUiThread(()->{
                ocupado=false;
                String detalhe=r.mensagem;
                if(r.ok&&r.total>0)detalhe+="\n\nProdutos: "+r.produtos+"\nClientes: "+r.clientes+"\nFornecedores: "+r.fornecedores+"\nDespesas: "+r.despesas;
                new AlertDialog.Builder(this).setTitle(r.ok?titulo+" ✓":titulo+" — atenção")
                        .setMessage(detalhe).setPositiveButton("OK",(d,w)->render()).setCancelable(false).show();
            });
        },"TechCell-Cloud-Manual").start();
    }

    private void habilitar(Button b,boolean permitido){boolean ok=permitido&&!ocupado;b.setEnabled(ok);b.setAlpha(ok?1f:0.55f);}
    private String curto(String id){if(id==null||id.trim().isEmpty())return "—";String x=id.replace("-","");return x.substring(0,Math.min(8,x.length())).toUpperCase(Locale.ROOT);}
    private String data(long millis){if(millis<=0)return "ainda não realizado";return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",new Locale("pt","BR")).format(new Date(millis));}
    private void mostrarErro(String titulo,String msg){new AlertDialog.Builder(this).setTitle(titulo).setMessage(msg==null?"Erro desconhecido":msg).setPositiveButton("OK",null).show();}
}
