package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TechCellHomeActivity extends Activity {
    private boolean abrindoLogin;
    private boolean checandoAprovacoes;
    private boolean avisoAprovacoesAberto;
    private boolean checandoDeveloper;
    private static final int REQ_LOGIN=701;
    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView text(String v,int s,boolean b){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout modulo(String icon,String titulo,String detalhe,boolean destaque){
        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,12));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=text(icon,27,false);top.addView(ic,new LinearLayout.LayoutParams(dp(46),ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(titulo,19,true));TextView sub=text(detalhe,12,false);sub.setTextColor(TechCellUi.MUTED);sub.setPadding(0,dp(3),0,0);texts.addView(sub);
        top.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.addView(top);
        Button abrir=new Button(this);abrir.setText("Abrir  →");abrir.setTextSize(14);if(destaque)TechCellUi.stylePrimary(this,abrir);else TechCellUi.styleSecondary(this,abrir);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));bp.setMargins(0,dp(12),0,0);card.addView(abrir,bp);card.setTag(abrir);return card;
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);TechCellUi.applyWindowChrome(this);TechCellBackgroundSync.garantir(this);abrirOuRenderizar();
    }
    @Override protected void onResume(){super.onResume();TechCellBackgroundSync.garantir(this);abrirOuRenderizar();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_LOGIN){abrindoLogin=false;abrirOuRenderizar();}}

    private void abrirOuRenderizar(){
        if(TechCellAccess.controleAtivo(this)&&!TechCellAccess.temSessaoValida(this)){
            if(!abrindoLogin){abrindoLogin=true;startActivityForResult(new Intent(this,TechCellUserLoginActivity.class),REQ_LOGIN);}return;
        }
        abrindoLogin=false;render();
        verificarDesenvolvedor();
        if(TechCellAccess.controleAtivo(this)&&TechCellAccess.podeAdministrar(this))verificarAprovacoes();
    }

    private void verificarDesenvolvedor(){
        if(checandoDeveloper||!TechCellCloudSync.estaAutenticado(this))return;
        checandoDeveloper=true;
        final boolean antes=TechCellDeveloperAccess.ehDesenvolvedor(this);
        new Thread(()->{
            try{
                TechCellDeveloperAccess.Estado e=TechCellDeveloperAccess.atualizar(getApplicationContext());
                runOnUiThread(()->{
                    checandoDeveloper=false;
                    if(!isFinishing()&&antes!=e.desenvolvedor)render();
                });
            }catch(Throwable ignored){runOnUiThread(()->checandoDeveloper=false);}
        },"TechCell-Developer-Check").start();
    }

    private void verificarAprovacoes(){
        if(checandoAprovacoes||avisoAprovacoesAberto)return;checandoAprovacoes=true;
        new Thread(()->{
            try{
                int n=TechCellDeviceAuthorization.contarPendentes(getApplicationContext());
                runOnUiThread(()->{
                    checandoAprovacoes=false;
                    if(n<=0||isFinishing()||avisoAprovacoesAberto)return;
                    avisoAprovacoesAberto=true;
                    new AlertDialog.Builder(this).setTitle("Novo aparelho aguardando autorização")
                            .setMessage(n==1?"Há 1 aparelho pedindo acesso à empresa.":"Há "+n+" aparelhos pedindo acesso à empresa.")
                            .setPositiveButton("VER AGORA",(d,w)->{avisoAprovacoesAberto=false;startActivity(new Intent(this,TechCellDeviceApprovalsActivity.class));})
                            .setNegativeButton("Depois",(d,w)->avisoAprovacoesAberto=false)
                            .setOnCancelListener(d->avisoAprovacoesAberto=false).show();
                });
            }catch(Throwable ignored){runOnUiThread(()->checandoAprovacoes=false);}
        },"TechCell-PendingDevices").start();
    }

    private void render(){
        GestaoDbHelper confDb=new GestaoDbHelper(this);GestaoDbHelper.SyncContext ctx;
        try{ctx=confDb.getSyncContext();}finally{confDb.close();}

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(28),dp(18),dp(30));scroll.addView(root);
        TextView brand=text("TECH CELL ACS",29,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView sub=text("Operação da loja em um só lugar",14,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);sub.setPadding(0,dp(4),0,dp(12));root.addView(sub);

        boolean cloud=TechCellAccess.controleAtivo(this);
        TechCellAccess.Sessao sessao=TechCellAccess.sessao(this);
        if(cloud&&sessao.valida){
            LinearLayout user=TechCellUi.card(this);user.setLayoutParams(TechCellUi.fullCardParams(this,8));
            String sufixo=TechCellDeveloperAccess.ehDesenvolvedor(this)?"  •  DESENVOLVEDOR":"";
            TextView ut=text("👤  "+sessao.nome,14,true);user.addView(ut);
            TextView ud=text(sessao.email+"  •  "+TechCellAccess.perfilExibicao(sessao.perfil)+sufixo,11,false);ud.setTextColor(TechCellUi.MUTED);ud.setPadding(0,dp(3),0,dp(7));user.addView(ud);
            Button trocar=new Button(this);trocar.setText("Trocar usuário");trocar.setTextSize(13);TechCellUi.styleSecondary(this,trocar);trocar.setOnClickListener(v->{TechCellAccess.encerrar(this);TechCellDeveloperAccess.limpar(this);abrindoLogin=false;abrirOuRenderizar();});
            user.addView(trocar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));root.addView(user);
        }

        if(TechCellDeveloperAccess.ehDesenvolvedor(this)){
            LinearLayout dev=modulo("🛠","Painel do Desenvolvedor","Controle global de clientes, licença Master, planos e quantidade de licenças Caixa.",true);
            Button abrirD=(Button)dev.getTag();abrirD.setText("Administrar plataforma  →");abrirD.setOnClickListener(v->startActivity(new Intent(this,TechCellDeveloperActivity.class)));dev.setOnClickListener(v->abrirD.performClick());root.addView(dev);
        }

        if(!ctx.configurado){
            LinearLayout remoto=modulo("☁","Entrar pela internet","Aparelho novo: login + autorização do Master antes de baixar os dados.",true);
            Button abrir=(Button)remoto.getTag();abrir.setText("Conectar à empresa  →");abrir.setOnClickListener(v->startActivityForResult(new Intent(this,TechCellCloudFirstLoginActivity.class),REQ_LOGIN));
            remoto.setOnClickListener(v->abrir.performClick());root.addView(remoto);
        }

        LinearLayout gestao=modulo("🏪","Gestão Tech Cell","PDV e módulos liberados para o nível do usuário.",ctx.configurado);
        Button abrirGestao=(Button)gestao.getTag();abrirGestao.setOnClickListener(v->startActivity(new Intent(this,GestaoActivity.class)));gestao.setOnClickListener(v->abrirGestao.performClick());root.addView(gestao);

        if((!cloud&&ctx.configurado)||TechCellAccess.podeAdministrar(this)){
            LinearLayout nuvem=modulo("☁","Nuvem Tech Cell","Empresa, sincronização e configuração da nuvem.",false);
            Button abrirNuvem=(Button)nuvem.getTag();abrirNuvem.setOnClickListener(v->startActivity(new Intent(this,TechCellCloudActivity.class)));nuvem.setOnClickListener(v->abrirNuvem.performClick());root.addView(nuvem);
        }

        if(cloud&&TechCellAccess.podeAdministrar(this)){
            LinearLayout usuarios=modulo("👥","Usuários / Acessos","Cadastrar, bloquear e configurar permissões de Master, Gerente ou Caixa.",false);
            Button abrir=(Button)usuarios.getTag();abrir.setOnClickListener(v->startActivity(new Intent(this,TechCellUsuariosActivity.class)));usuarios.setOnClickListener(v->abrir.performClick());root.addView(usuarios);

            LinearLayout aparelhos=modulo("📱","Dispositivos / Autorizações","Aprovar, negar ou bloquear aparelhos que tentam entrar na empresa.",false);
            Button abrirA=(Button)aparelhos.getTag();abrirA.setOnClickListener(v->startActivity(new Intent(this,TechCellDeviceApprovalsActivity.class)));aparelhos.setOnClickListener(v->abrirA.performClick());root.addView(aparelhos);
        }

        LinearLayout caixa=modulo("💵","Caixa da Loja","Caixa atual preservado para lançamentos e fechamento.",false);
        Button abrirCaixa=(Button)caixa.getTag();abrirCaixa.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));caixa.setOnClickListener(v->abrirCaixa.performClick());root.addView(caixa);

        String rodape=!ctx.configurado
                ?"Aparelho novo • login + autorização do Master"
                :(TechCellDeveloperAccess.ehDesenvolvedor(this)?"Modo Desenvolvedor ativo • acesso global da plataforma":(cloud?"Controle de acesso ativo • operação local continua disponível sem internet":"Ambiente local • ative a nuvem quando desejar acesso remoto"));
        TextView safe=text(rodape,12,true);safe.setTextColor(TechCellUi.GREEN);safe.setGravity(Gravity.CENTER);safe.setBackground(TechCellUi.solid(this,TechCellUi.PALE_GREEN,12));safe.setPadding(dp(12),dp(11),dp(12),dp(11));
        root.addView(safe,TechCellUi.fullCardParams(this,16));setContentView(scroll);
    }
}
