package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
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

import com.google.firebase.auth.FirebaseUser;

/** Primeiro acesso de um aparelho novo sem precisar estar na LAN do Master. */
public class TechCellCloudFirstLoginActivity extends Activity {
    private boolean ocupado;
    private TextView status;

    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);render();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(26),dp(18),dp(30));scroll.addView(root);

        TextView brand=txt("TECH CELL ACS",28,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView titulo=txt("Entrar pela internet",23,true);titulo.setGravity(Gravity.CENTER);titulo.setPadding(0,dp(14),0,dp(3));root.addView(titulo);
        TextView sub=txt("Primeiro acesso remoto • não precisa estar no mesmo Wi-Fi do Master",12,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,16));
        TextView i1=txt("UBÁ ↔ NUVEM ↔ DIVINÉSIA",13,true);i1.setTextColor(TechCellUi.BLUE);info.addView(i1);
        TextView i2=txt("Entre com uma conta que já foi cadastrada pelo Master. O aplicativo identifica a empresa, configura este aparelho e baixa os dados para o banco local. Depois ele continua híbrido: nuvem para o remoto e LAN quando estiver disponível.",12,false);i2.setTextColor(TechCellUi.MUTED);i2.setPadding(0,dp(5),0,0);info.addView(i2);root.addView(info);

        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,8));
        EditText email=new EditText(this);email.setHint("E-mail da conta");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);

        Button entrar=botao("☁  CONECTAR À MINHA EMPRESA");TechCellUi.stylePrimary(this,entrar,TechCellUi.BLUE);
        entrar.setOnClickListener(v->entrar(email.getText().toString().trim(),senha.getText().toString(),entrar));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));ep.setMargins(0,dp(12),0,0);card.addView(entrar,ep);

        status=txt("Aguardando login.",12,true);status.setTextColor(TechCellUi.MUTED);status.setPadding(0,dp(10),0,0);card.addView(status);root.addView(card);

        Button voltar=botao("←  Voltar / configurar localmente");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));vp.setMargins(0,dp(10),0,0);root.addView(voltar,vp);

        TextView dica=txt("Para criar uma empresa nova do zero, faça primeiro a configuração do aparelho Master. Esta tela é para entrar em uma empresa que já existe na nuvem.",11,false);dica.setTextColor(TechCellUi.MUTED);dica.setPadding(dp(4),dp(10),dp(4),0);root.addView(dica);
        setContentView(scroll);
    }

    private void entrar(String email,String senha,Button botao){
        if(ocupado)return;
        if(email.isEmpty()||senha.isEmpty()){Toast.makeText(this,"Informe e-mail e senha.",Toast.LENGTH_LONG).show();return;}
        ocupado=true;botao.setEnabled(false);botao.setText("Entrando…");status("1/4 • Validando e-mail e senha…",TechCellUi.BLUE);
        try{TechCellAccess.encerrar(this);}catch(Throwable ignored){}

        TechCellCloudSync.auth(this).signInWithEmailAndPassword(email,senha).addOnCompleteListener(this,t->{
            if(!t.isSuccessful()){
                ocupado=false;botao.setEnabled(true);botao.setText("☁  CONECTAR À MINHA EMPRESA");
                erro("Não foi possível entrar",TechCellCloudUsers.mensagem(t.getException()));return;
            }
            status("2/4 • Localizando sua empresa na nuvem…",TechCellUi.BLUE);
            new Thread(()->bootstrap(botao),"TechCell-Cloud-FirstLogin").start();
        });
    }

    private void bootstrap(Button botao){
        boolean sessaoValidada=false;
        try{
            FirebaseUser user=TechCellCloudSync.auth(getApplicationContext()).getCurrentUser();
            if(user==null)throw new IllegalStateException("A conta não permaneceu conectada.");

            GestaoDbHelper db=new GestaoDbHelper(getApplicationContext());
            try{
                GestaoDbHelper.SyncContext ctx=db.getSyncContext();
                if(!ctx.configurado||ctx.empresaUuid==null||ctx.empresaUuid.trim().isEmpty()){
                    boolean ok=TechCellCloudFirstLogin.configurarSePossivel(getApplicationContext(),user,db);
                    if(!ok){
                        // Conta proprietária de versões anteriores ainda pode ser descoberta
                        // pela rotina legada do TechCellAccess.
                    }
                }
            }finally{db.close();}

            runOnUiThread(()->status("3/4 • Validando perfil e permissões…",TechCellUi.BLUE));
            TechCellAccess.Sessao s=TechCellAccess.atualizarDaNuvem(getApplicationContext());
            sessaoValidada=true;

            runOnUiThread(()->status("4/4 • Baixando a base inicial da empresa…",TechCellUi.BLUE));
            TechCellCloudRealtimeSync.Resultado sync=TechCellCloudRealtimeSync.sincronizar(getApplicationContext());
            if(!sync.ok){
                throw new IllegalStateException("Conta vinculada, mas a carga inicial não terminou: "+(sync.erro==null?"erro de sincronização":sync.erro));
            }
            TechCellBackgroundSync.garantir(getApplicationContext());

            runOnUiThread(()->{
                ocupado=false;
                status("Conectado ✓ • "+TechCellAccess.perfilExibicao(s.perfil)+" • "+sync.recebidos+" cadastro(s) recebidos",TechCellUi.GREEN);
                new AlertDialog.Builder(this).setTitle("Empresa conectada ✓")
                        .setMessage("Este aparelho foi configurado pela internet e a base local já começou a ser preenchida.\n\nPerfil: "+TechCellAccess.perfilExibicao(s.perfil)+"\nCadastros recebidos agora: "+sync.recebidos+"\n\nA partir daqui a sincronização continua automaticamente.")
                        .setPositiveButton("Entrar no sistema",(d,w)->{setResult(RESULT_OK);finish();}).setCancelable(false).show();
            });
        }catch(Throwable e){
            final boolean manter=sessaoValidada;
            if(!manter){try{TechCellAccess.encerrar(getApplicationContext());}catch(Throwable ignored){}}
            runOnUiThread(()->{
                ocupado=false;botao.setEnabled(true);botao.setText("☁  CONECTAR À MINHA EMPRESA");
                String m=TechCellCloudUsers.mensagem(e);
                status("Falha: "+m,TechCellUi.RED);
                erro(manter?"Conta conectada — carga pendente":"Não foi possível localizar a empresa",m);
            });
        }
    }

    private void status(String s,int cor){if(status!=null){status.setText(s);status.setTextColor(cor);}}
    private void erro(String titulo,String mensagem){new AlertDialog.Builder(this).setTitle(titulo).setMessage(mensagem==null?"Erro desconhecido":mensagem).setPositiveButton("OK",null).show();}
}
