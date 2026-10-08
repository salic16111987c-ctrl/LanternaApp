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

/** Login exclusivo do Desenvolvedor da plataforma, sem vínculo com uma empresa específica. */
public class TechCellDeveloperLoginActivity extends Activity {
    private boolean ocupado;
    private TextView status;

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);render();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(28),dp(18),dp(30));scroll.addView(root);

        TextView brand=txt("TECH CELL • PLATAFORMA",24,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView titulo=txt("Acesso do Desenvolvedor",25,true);titulo.setGravity(Gravity.CENTER);titulo.setPadding(0,dp(14),0,dp(4));root.addView(titulo);
        TextView sub=txt("Acesso global • separado das contas Master das lojas",13,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,16));
        TextView i1=txt("NÍVEL SUPREMO DA PLATAFORMA",12,true);i1.setTextColor(TechCellUi.BLUE);info.addView(i1);
        TextView i2=txt("Esta conta não precisa pertencer a nenhuma loja. Após o login, o sistema valida o UID na lista segura de Desenvolvedores e abre o painel global de empresas, planos e licenças.",11,false);i2.setTextColor(TechCellUi.MUTED);i2.setPadding(0,dp(5),0,0);info.addView(i2);root.addView(info);

        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,8));
        EditText email=new EditText(this);email.setHint("E-mail do Desenvolvedor");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);
        Button entrar=botao("🛠  ENTRAR COMO DESENVOLVEDOR");TechCellUi.stylePrimary(this,entrar,TechCellUi.BLUE);
        entrar.setOnClickListener(v->entrar(email.getText().toString().trim(),senha.getText().toString(),entrar));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));ep.setMargins(0,dp(12),0,0);card.addView(entrar,ep);
        status=txt("Aguardando login.",12,true);status.setTextColor(TechCellUi.MUTED);status.setPadding(0,dp(10),0,0);card.addView(status);root.addView(card);

        Button voltar=botao("←  Voltar ao acesso da loja");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));vp.setMargins(0,dp(10),0,0);root.addView(voltar,vp);

        TextView aviso=txt("Segurança: uma conta comum não consegue se transformar em Desenvolvedor pelo aplicativo. O primeiro Desenvolvedor é provisionado uma única vez no controle central usando o UID da conta.",11,false);aviso.setTextColor(TechCellUi.MUTED);aviso.setPadding(dp(4),dp(10),dp(4),0);root.addView(aviso);
        setContentView(scroll);
    }

    private void entrar(String email,String senha,Button botao){
        if(ocupado)return;
        if(email.isEmpty()||senha.isEmpty()){Toast.makeText(this,"Informe e-mail e senha do Desenvolvedor.",Toast.LENGTH_LONG).show();return;}
        ocupado=true;botao.setEnabled(false);botao.setText("Validando…");status("1/2 • Conferindo e-mail e senha…",TechCellUi.BLUE);
        TechCellDeveloperAccess.limpar(this);

        TechCellCloudSync.auth(this).signInWithEmailAndPassword(email,senha).addOnCompleteListener(this,t->{
            if(!t.isSuccessful()){
                ocupado=false;botao.setEnabled(true);botao.setText("🛠  ENTRAR COMO DESENVOLVEDOR");
                erro("Não foi possível entrar",TechCellCloudUsers.mensagem(t.getException()));return;
            }
            status("2/2 • Validando autorização de Desenvolvedor…",TechCellUi.BLUE);
            new Thread(()->{
                try{
                    TechCellDeveloperAccess.Estado e=TechCellDeveloperAccess.atualizar(getApplicationContext());
                    if(!e.desenvolvedor)throw new IllegalStateException("Esta conta existe, mas não está cadastrada como Desenvolvedor da plataforma.");
                    TechCellBackgroundSync.parar(getApplicationContext());
                    runOnUiThread(()->{
                        ocupado=false;status("Acesso Desenvolvedor liberado ✓",TechCellUi.GREEN);
                        Toast.makeText(this,"Modo Desenvolvedor ativo",Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);finish();
                    });
                }catch(Throwable ex){
                    try{TechCellCloudSync.auth(getApplicationContext()).signOut();}catch(Throwable ignored){}
                    TechCellDeveloperAccess.limpar(getApplicationContext());
                    runOnUiThread(()->{
                        ocupado=false;botao.setEnabled(true);botao.setText("🛠  ENTRAR COMO DESENVOLVEDOR");
                        String m=TechCellCloudUsers.mensagem(ex);status("Acesso negado: "+m,TechCellUi.RED);erro("Conta sem acesso de Desenvolvedor",m);
                    });
                }
            },"TechCell-Developer-Login").start();
        });
    }

    private void status(String s,int cor){if(status!=null){status.setText(s);status.setTextColor(cor);}}
    private void erro(String titulo,String mensagem){new AlertDialog.Builder(this).setTitle(titulo).setMessage(mensagem==null?"Erro desconhecido":mensagem).setPositiveButton("OK",null).show();}
}
