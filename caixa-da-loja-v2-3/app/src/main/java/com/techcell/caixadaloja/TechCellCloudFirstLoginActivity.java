package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/** Primeiro acesso remoto: login + autorização do aparelho pelo Master. */
public class TechCellCloudFirstLoginActivity extends Activity {
    private boolean ocupado;
    private boolean aguardando;
    private boolean finalizando;
    private TextView status;
    private Button entrarBtn;
    private Button codigoBtn;
    private TechCellUserCompanyIndex.Vinculo vinculo;
    private String deviceUuid = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!aguardando || finalizando || vinculo == null || deviceUuid.isEmpty()) return;
            new Thread(() -> {
                try {
                    TechCellDeviceAuthorization.Dispositivo d = TechCellDeviceAuthorization.consultar(
                            getApplicationContext(), vinculo.empresaUuid, deviceUuid);
                    runOnUiThread(() -> tratarStatus(d));
                } catch (Throwable e) {
                    runOnUiThread(() -> {
                        if (aguardando) status("Aguardando Master • conexão será tentada novamente…", TechCellUi.ORANGE);
                    });
                }
            }, "TechCell-DeviceApproval-Poll").start();
            handler.postDelayed(this, 3000L);
        }
    };

    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){ super.onCreate(b);TechCellUi.applyWindowChrome(this);render(); }
    @Override protected void onDestroy(){ aguardando=false;handler.removeCallbacksAndMessages(null);super.onDestroy(); }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(26),dp(18),dp(30));scroll.addView(root);

        TextView brand=txt("TECH CELL ACS",28,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView titulo=txt("Entrar pela internet",23,true);titulo.setGravity(Gravity.CENTER);titulo.setPadding(0,dp(14),0,dp(3));root.addView(titulo);
        TextView sub=txt("Primeiro acesso remoto • protegido por autorização do Master",12,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,16));
        TextView i1=txt("MASTER ↔ NUVEM ↔ CAIXA",13,true);i1.setTextColor(TechCellUi.BLUE);info.addView(i1);
        TextView i2=txt("Depois do e-mail e senha, este aparelho solicita autorização ao Master. Só após a aprovação ele baixa os dados da empresa. Um código temporário pode ser usado como alternativa.",12,false);i2.setTextColor(TechCellUi.MUTED);i2.setPadding(0,dp(5),0,0);info.addView(i2);root.addView(info);

        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,8));
        EditText email=new EditText(this);email.setHint("E-mail da conta");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);

        entrarBtn=botao("☁  ENTRAR E SOLICITAR AUTORIZAÇÃO");TechCellUi.stylePrimary(this,entrarBtn,TechCellUi.BLUE);
        entrarBtn.setOnClickListener(v->entrar(email.getText().toString().trim(),senha.getText().toString()));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));ep.setMargins(0,dp(12),0,0);card.addView(entrarBtn,ep);

        codigoBtn=botao("🔑  Já tenho um código do Master");TechCellUi.styleSecondary(this,codigoBtn);codigoBtn.setVisibility(android.view.View.GONE);codigoBtn.setOnClickListener(v->usarCodigo());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));cp.setMargins(0,dp(8),0,0);card.addView(codigoBtn,cp);

        status=txt("Aguardando login.",12,true);status.setTextColor(TechCellUi.MUTED);status.setPadding(0,dp(10),0,0);card.addView(status);root.addView(card);

        Button voltar=botao("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));vp.setMargins(0,dp(10),0,0);root.addView(voltar,vp);

        TextView dica=txt("Mesmo conhecendo o e-mail e a senha, um aparelho novo não recebe os dados até ser autorizado pelo Master.",11,false);dica.setTextColor(TechCellUi.MUTED);dica.setPadding(dp(4),dp(10),dp(4),0);root.addView(dica);
        setContentView(scroll);
    }

    private void entrar(String email,String senha){
        if(ocupado||aguardando)return;
        if(email.isEmpty()||senha.isEmpty()){Toast.makeText(this,"Informe e-mail e senha.",Toast.LENGTH_LONG).show();return;}
        ocupado=true;entrarBtn.setEnabled(false);entrarBtn.setText("Entrando…");status("1/3 • Validando e-mail e senha…",TechCellUi.BLUE);
        try{TechCellAccess.encerrar(this);}catch(Throwable ignored){}

        TechCellCloudSync.auth(this).signInWithEmailAndPassword(email,senha).addOnCompleteListener(this,t->{
            if(!t.isSuccessful()){
                ocupado=false;entrarBtn.setEnabled(true);entrarBtn.setText("☁  ENTRAR E SOLICITAR AUTORIZAÇÃO");
                erro("Não foi possível entrar",TechCellCloudUsers.mensagem(t.getException()));return;
            }
            status("2/3 • Localizando a empresa desta conta…",TechCellUi.BLUE);
            new Thread(this::solicitarAutorizacao,"TechCell-DeviceApproval-Request").start();
        });
    }

    private void solicitarAutorizacao(){
        try{
            FirebaseUser user=TechCellCloudSync.auth(getApplicationContext()).getCurrentUser();
            if(user==null)throw new IllegalStateException("A conta não permaneceu conectada.");
            vinculo=TechCellUserCompanyIndex.descobrir(getApplicationContext(),user);
            if(vinculo==null)throw new IllegalStateException("A conta ainda não possui vínculo de empresa disponível na nuvem. Abra o Master conectado à internet por alguns segundos e tente novamente.");
            deviceUuid=TechCellDeviceAuthorization.deviceUuid(getApplicationContext());
            TechCellDeviceAuthorization.Dispositivo d=TechCellDeviceAuthorization.solicitar(getApplicationContext(),vinculo,user);
            runOnUiThread(()->{
                ocupado=false;
                codigoBtn.setVisibility(android.view.View.VISIBLE);
                tratarStatus(d);
            });
        }catch(Throwable e){
            try{TechCellAccess.encerrar(getApplicationContext());}catch(Throwable ignored){}
            runOnUiThread(()->{
                ocupado=false;entrarBtn.setEnabled(true);entrarBtn.setText("☁  ENTRAR E SOLICITAR AUTORIZAÇÃO");
                String m=TechCellCloudUsers.mensagem(e);status("Falha: "+m,TechCellUi.RED);erro("Não foi possível solicitar autorização",m);
            });
        }
    }

    private void tratarStatus(TechCellDeviceAuthorization.Dispositivo d){
        if(d==null){status("Solicitação não encontrada. Tente novamente.",TechCellUi.RED);return;}
        if(d.autorizado()){
            aguardando=false;handler.removeCallbacks(poll);finalizarAcesso();return;
        }
        if(TechCellDeviceAuthorization.NEGADO.equalsIgnoreCase(d.status)){
            aguardando=false;handler.removeCallbacks(poll);entrarBtn.setEnabled(false);codigoBtn.setVisibility(android.view.View.VISIBLE);
            status("Acesso negado pelo Master.",TechCellUi.RED);
            erro("Aparelho não autorizado","O Master negou esta solicitação. Ele poderá reautorizar o aparelho na tela Dispositivos / Autorizações.");return;
        }
        if(d.bloqueado()){
            aguardando=false;handler.removeCallbacks(poll);entrarBtn.setEnabled(false);codigoBtn.setVisibility(android.view.View.GONE);
            status("Este aparelho está bloqueado pelo Master.",TechCellUi.RED);return;
        }
        aguardando=true;entrarBtn.setEnabled(false);codigoBtn.setVisibility(android.view.View.VISIBLE);
        status("3/3 • Solicitação enviada ✓  Aguardando aprovação do Master…",TechCellUi.ORANGE);
        handler.removeCallbacks(poll);handler.postDelayed(poll,2500L);
    }

    private void usarCodigo(){
        if(vinculo==null||deviceUuid.isEmpty()){Toast.makeText(this,"Faça o login primeiro.",Toast.LENGTH_LONG).show();return;}
        EditText campo=new EditText(this);campo.setHint("Código de 6 dígitos");campo.setSingleLine(true);campo.setInputType(InputType.TYPE_CLASS_NUMBER);campo.setGravity(Gravity.CENTER);campo.setTextSize(22);
        new AlertDialog.Builder(this).setTitle("Código do Master").setMessage("Digite o código temporário gerado pelo Master para este aparelho.")
                .setView(campo).setPositiveButton("Autorizar",(d,w)->{
                    String c=campo.getText().toString();status("Conferindo código…",TechCellUi.BLUE);
                    new Thread(()->{
                        try{TechCellDeviceAuthorization.usarCodigo(getApplicationContext(),vinculo.empresaUuid,deviceUuid,c);runOnUiThread(this::finalizarAcesso);}
                        catch(Throwable e){runOnUiThread(()->{String m=TechCellCloudUsers.mensagem(e);status("Código não aceito.",TechCellUi.RED);erro("Código inválido ou expirado",m);});}
                    },"TechCell-DeviceApproval-Code").start();
                }).setNegativeButton("Cancelar",null).show();
    }

    private void finalizarAcesso(){
        if(finalizando)return;finalizando=true;aguardando=false;handler.removeCallbacks(poll);
        codigoBtn.setVisibility(android.view.View.GONE);status("Autorizado ✓ • configurando e baixando a base…",TechCellUi.BLUE);
        new Thread(()->{
            try{
                FirebaseUser user=TechCellCloudSync.auth(getApplicationContext()).getCurrentUser();
                if(user==null)throw new IllegalStateException("Conta não conectada.");
                GestaoDbHelper db=new GestaoDbHelper(getApplicationContext());
                try{
                    GestaoDbHelper.SyncContext ctx=db.getSyncContext();
                    if(!ctx.configurado||ctx.empresaUuid==null||ctx.empresaUuid.trim().isEmpty()){
                        if(!TechCellCloudFirstLogin.configurarSePossivel(getApplicationContext(),user,db)) throw new IllegalStateException("Não foi possível configurar a empresa neste aparelho.");
                    }
                }finally{db.close();}
                TechCellAccess.Sessao s=TechCellAccess.atualizarDaNuvem(getApplicationContext());
                TechCellCloudRealtimeSync.Resultado sync=TechCellCloudRealtimeSync.sincronizar(getApplicationContext());
                if(!sync.ok)throw new IllegalStateException("Aparelho autorizado, mas a carga inicial não terminou: "+(sync.erro==null?"erro de sincronização":sync.erro));
                TechCellBackgroundSync.garantir(getApplicationContext());
                runOnUiThread(()->{
                    finalizando=false;ocupado=false;status("Conectado ✓ • "+TechCellAccess.perfilExibicao(s.perfil)+" • "+sync.recebidos+" cadastro(s) recebidos",TechCellUi.GREEN);
                    new AlertDialog.Builder(this).setTitle("Aparelho autorizado ✓")
                            .setMessage("O Master autorizou este aparelho e a base da empresa foi carregada.\n\nPerfil: "+TechCellAccess.perfilExibicao(s.perfil)+"\nCadastros recebidos agora: "+sync.recebidos)
                            .setPositiveButton("Entrar no sistema",(d,w)->{setResult(RESULT_OK);finish();}).setCancelable(false).show();
                });
            }catch(Throwable e){
                runOnUiThread(()->{finalizando=false;String m=TechCellCloudUsers.mensagem(e);status("Autorizado, mas falta concluir a carga: "+m,TechCellUi.RED);erro("Acesso autorizado — carga pendente",m);});
            }
        },"TechCell-DeviceApproval-Finalize").start();
    }

    private void status(String s,int cor){if(status!=null){status.setText(s);status.setTextColor(cor);}}
    private void erro(String titulo,String mensagem){new AlertDialog.Builder(this).setTitle(titulo).setMessage(mensagem==null?"Erro desconhecido":mensagem).setPositiveButton("OK",null).show();}
}
