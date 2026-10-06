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

public class TechCellUserLoginActivity extends Activity {
    private boolean ocupado;
    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView txt(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(TechCellUi.TEXT);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;
    }
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        TechCellUi.applyWindowChrome(this);
        render();
        FirebaseUser atual=null;
        try{atual=TechCellCloudSync.auth(this).getCurrentUser();}catch(Throwable ignored){}
        if(atual!=null && !TechCellAccess.temSessaoValida(this)) validarContaAtual();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(30),dp(18),dp(30));scroll.addView(root);

        TextView brand=txt("TECH CELL ACS",27,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView titulo=txt("Entrar no sistema",23,true);titulo.setGravity(Gravity.CENTER);titulo.setPadding(0,dp(18),0,dp(3));root.addView(titulo);
        TextView sub=txt("Acesso da loja • Master, Gerente ou Caixa",13,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,18));
        TextView it=txt("FUNCIONA OFFLINE DEPOIS DO PRIMEIRO LOGIN",12,true);it.setTextColor(TechCellUi.GREEN);info.addView(it);
        TextView iv=txt("A conta é validada na nuvem e o nível de acesso fica guardado neste aparelho para a operação continuar se a internet cair.",11,false);iv.setTextColor(TechCellUi.MUTED);iv.setPadding(0,dp(4),0,0);info.addView(iv);root.addView(info);

        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,10));
        EditText email=new EditText(this);email.setHint("E-mail");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);
        Button entrar=botao("Entrar");TechCellUi.stylePrimary(this,entrar,TechCellUi.BLUE);
        entrar.setOnClickListener(v->entrar(email.getText().toString().trim(),senha.getText().toString(),entrar));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));ep.setMargins(0,dp(12),0,0);card.addView(entrar,ep);
        root.addView(card);

        TechCellAccess.Sessao s=TechCellAccess.sessao(this);
        if(s.valida){
            LinearLayout atual=TechCellUi.card(this);atual.setLayoutParams(TechCellUi.fullCardParams(this,8));
            atual.addView(txt("Conta atual",13,true));
            atual.addView(txt(s.nome+"\n"+s.email+" • "+TechCellAccess.perfilExibicao(s.perfil),12,false));
            Button continuar=botao("Continuar com esta conta");TechCellUi.stylePrimary(this,continuar,TechCellUi.GREEN);continuar.setOnClickListener(v->{setResult(RESULT_OK);finish();});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));cp.setMargins(0,dp(8),0,0);atual.addView(continuar,cp);root.addView(atual);
        }

        setContentView(scroll);
    }

    private void validarContaAtual(){
        if(ocupado)return;ocupado=true;
        Toast.makeText(this,"Validando conta conectada…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                TechCellAccess.Sessao s=TechCellAccess.atualizarDaNuvem(getApplicationContext());
                runOnUiThread(()->{ocupado=false;Toast.makeText(this,"Acesso: "+TechCellAccess.perfilExibicao(s.perfil),Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();});
            }catch(Throwable e){
                runOnUiThread(()->{ocupado=false;mostrarErro("Não foi possível validar a conta",TechCellCloudUsers.mensagem(e));});
            }
        },"TechCell-Access-Validate").start();
    }

    private void entrar(String email,String senha,Button entrar){
        if(ocupado)return;
        if(email.isEmpty()||senha.isEmpty()){Toast.makeText(this,"Informe e-mail e senha.",Toast.LENGTH_LONG).show();return;}
        ocupado=true;entrar.setEnabled(false);entrar.setText("Entrando…");
        TechCellAccess.encerrar(this);
        TechCellCloudSync.auth(this).signInWithEmailAndPassword(email,senha).addOnCompleteListener(this,t->{
            if(!t.isSuccessful()){
                ocupado=false;entrar.setEnabled(true);entrar.setText("Entrar");
                mostrarErro("Não foi possível entrar",TechCellCloudUsers.mensagem(t.getException()));return;
            }
            new Thread(()->{
                try{
                    TechCellAccess.Sessao s=TechCellAccess.atualizarDaNuvem(getApplicationContext());
                    runOnUiThread(()->{ocupado=false;Toast.makeText(this,"Bem-vindo • "+TechCellAccess.perfilExibicao(s.perfil),Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();});
                }catch(Throwable e){
                    TechCellAccess.encerrar(getApplicationContext());
                    runOnUiThread(()->{ocupado=false;entrar.setEnabled(true);entrar.setText("Entrar");mostrarErro("Acesso não autorizado",TechCellCloudUsers.mensagem(e));});
                }
            },"TechCell-Access-Login").start();
        });
    }

    @Override public void onBackPressed(){
        if(TechCellAccess.controleAtivo(this) && !TechCellAccess.temSessaoValida(this)){
            new AlertDialog.Builder(this).setTitle("Login necessário")
                    .setMessage("A nuvem está ativa nesta loja. Entre com uma conta autorizada para continuar.")
                    .setPositiveButton("OK",null).show();
            return;
        }
        super.onBackPressed();
    }

    private void mostrarErro(String titulo,String msg){
        new AlertDialog.Builder(this).setTitle(titulo).setMessage(msg==null?"Erro desconhecido":msg).setPositiveButton("OK",null).show();
    }
}
