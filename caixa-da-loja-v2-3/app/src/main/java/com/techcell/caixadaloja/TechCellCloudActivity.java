package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
        boolean masterFisico=ctx.configurado&&"MASTER".equalsIgnoreCase(ctx.papelDispositivo);
        boolean administrador=TechCellAccess.temSessaoValida(this)&&TechCellAccess.podeAdministrar(this);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(30));scroll.addView(root);

        Button voltar=action("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());
        root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));
        TextView titulo=txt("Nuvem Tech Cell",27,true);titulo.setPadding(0,dp(15),0,dp(2));root.addView(titulo);
        TextView sub=txt("Empresa, dados compartilhados e contas de usuário",12,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,11));
        TextView it=txt("LOCAL + NUVEM",12,true);it.setTextColor(TechCellUi.GREEN);info.addView(it);
        TextView id=txt("Durante os testes, os aparelhos vinculados trabalham com a mesma empresa pela internet. O banco local continua disponível para manter o PDV funcionando quando a conexão cair.",12,false);
        id.setTextColor(Color.parseColor("#475467"));id.setPadding(0,dp(5),0,0);info.addView(id);root.addView(info);

        LinearLayout status=TechCellUi.card(this);status.setLayoutParams(TechCellUi.fullCardParams(this,8));
        status.addView(txt("Status",15,true));
        String papel=ctx.configurado?ctx.papelDispositivo:"NÃO CONFIGURADO";
        TextView sv=txt(
                "Função deste aparelho: "+papel+"\n"+
                "Empresa: "+curto(ctx.empresaUuid)+"\n"+
                "Conta: "+(estado.autenticado?estado.email:"não conectada")+"\n"+
                "Nuvem: "+(estado.cloudAtiva?"ATIVA ✓":"ainda não ativada")+"\n"+
                "Alterações locais pendentes: "+pendentes+"\n"+
                "Último sucesso: "+data(estado.ultimoSucesso)+
                (estado.ultimaCargaCadastros>0?"\nÚltima carga: "+estado.ultimaCargaCadastros+" cadastro(s)":"")+
                (estado.ultimoErro==null||estado.ultimoErro.trim().isEmpty()?"":"\nÚltimo erro: "+estado.ultimoErro),
                12,false);
        sv.setTextColor(TechCellUi.NAVY);sv.setPadding(0,dp(7),0,0);status.addView(sv);root.addView(status);

        if(!masterFisico){
            LinearLayout alerta=TechCellUi.card(this);alerta.setBackground(TechCellUi.solid(this,Color.parseColor("#EEF4FF"),14));
            TextView a=txt("Neste aparelho você entra com uma conta já cadastrada. A criação da primeira conta da empresa continua exclusiva do Master. A conta Administrador pode trabalhar remotamente pela nuvem.",12,true);
            a.setTextColor(TechCellUi.BLUE);alerta.addView(a);
            root.addView(alerta,TechCellUi.fullCardParams(this,8));
        }

        FirebaseUser user=null;
        try{user=TechCellCloudSync.auth(this).getCurrentUser();}catch(Throwable ignored){}
        if(user==null) adicionarLogin(root,masterFisico);
        else adicionarOperacoes(root,masterFisico,administrador,estado);

        setContentView(scroll);
    }

    private void adicionarLogin(LinearLayout root,boolean masterFisico){
        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
        card.addView(txt(masterFisico?"Conta principal da loja":"Entrar na conta da loja",16,true));
        TextView obs=txt(masterFisico
                ?"No primeiro cadastro da empresa, o Master pode criar a conta Administrador. Depois, cada funcionário usa sua própria conta (Administrador, Gerente ou Caixa)."
                :"Use o e-mail e a senha de uma conta já cadastrada. O nível de acesso vem da própria conta e não da função física deste aparelho.",11,false);
        obs.setTextColor(TechCellUi.MUTED);obs.setPadding(0,dp(3),0,dp(8));card.addView(obs);
        EditText email=new EditText(this);email.setHint("E-mail");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);card.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);card.addView(senha);

        Button entrar=action("☁  Entrar na conta existente");TechCellUi.stylePrimary(this,entrar,TechCellUi.BLUE);entrar.setEnabled(!ocupado);entrar.setAlpha(!ocupado?1f:0.55f);
        entrar.setOnClickListener(v->{
            String e=email.getText().toString().trim(),s=senha.getText().toString();
            if(e.isEmpty()||s.isEmpty()){Toast.makeText(this,"Informe e-mail e senha.",Toast.LENGTH_LONG).show();return;}
            ocupado=true;entrar.setEnabled(false);entrar.setText("Entrando…");
            FirebaseAuth auth=TechCellCloudSync.auth(this);
            auth.signInWithEmailAndPassword(e,s).addOnCompleteListener(this,t->{
                if(!t.isSuccessful()){
                    ocupado=false;
                    mostrarErro("Não foi possível entrar",TechCellCloudUsers.mensagem(t.getException()));
                    render();
                    return;
                }
                entrar.setText("Validando empresa e acesso…");
                new Thread(()->{
                    try{
                        TechCellAccess.Sessao sessao=TechCellAccess.atualizarDaNuvem(getApplicationContext());
                        runOnUiThread(()->{
                            ocupado=false;
                            TechCellBackgroundSync.garantir(getApplicationContext());
                            Toast.makeText(this,"Conta conectada • "+TechCellAccess.perfilExibicao(sessao.perfil),Toast.LENGTH_SHORT).show();
                            render();
                        });
                    }catch(Throwable erro){
                        TechCellAccess.encerrar(getApplicationContext());
                        runOnUiThread(()->{
                            ocupado=false;
                            mostrarErro("Acesso não autorizado",TechCellCloudUsers.mensagem(erro));
                            render();
                        });
                    }
                },"TechCell-Cloud-ValidateLogin").start();
            });
        });
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));ep.setMargins(0,dp(10),0,0);card.addView(entrar,ep);

        if(masterFisico){
            Button criar=action("＋  Criar primeira conta Administrador");TechCellUi.styleSecondary(this,criar);criar.setEnabled(!ocupado);criar.setAlpha(!ocupado?1f:0.55f);
            criar.setOnClickListener(v->{
                String e=email.getText().toString().trim(),s=senha.getText().toString();
                if(e.isEmpty()||!e.contains("@")){Toast.makeText(this,"Informe um e-mail válido.",Toast.LENGTH_LONG).show();return;}
                if(s.length()<6){Toast.makeText(this,"A senha deve ter pelo menos 6 caracteres.",Toast.LENGTH_LONG).show();return;}
                new AlertDialog.Builder(this)
                        .setTitle("Criar conta proprietária?")
                        .setMessage("Esta será a conta Administrador principal desta loja. Use este botão somente no primeiro cadastro da empresa.\n\nE-mail: "+e)
                        .setPositiveButton("Criar Administrador",(d,w)->criarPrimeiraConta(e,s,criar,entrar))
                        .setNegativeButton("Cancelar",null).show();
            });
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));cp.setMargins(0,dp(8),0,0);card.addView(criar,cp);
        }
        root.addView(card);
    }

    private void criarPrimeiraConta(String email,String senha,Button criar,Button entrar){
        if(ocupado)return;
        ocupado=true;criar.setEnabled(false);entrar.setEnabled(false);criar.setText("Criando conta…");
        TechCellCloudSync.auth(this).createUserWithEmailAndPassword(email,senha).addOnCompleteListener(this,t->{
            if(!t.isSuccessful()){
                ocupado=false;
                mostrarErro("Não foi possível criar a conta",TechCellCloudUsers.mensagem(t.getException()));
                render();return;
            }
            criar.setText("Registrando empresa…");
            new Thread(()->{
                TechCellCloudSync.Resultado r=TechCellCloudSync.testarRegistrarEmpresa(getApplicationContext());
                if(r.ok){
                    try{TechCellCloudUsers.garantirProprietario(getApplicationContext());}
                    catch(Throwable e){r.ok=false;r.mensagem="Conta criada, mas o perfil Administrador não foi confirmado: "+TechCellCloudUsers.mensagem(e);}
                }
                if(r.ok){
                    try{TechCellAccess.atualizarDaNuvem(getApplicationContext());}catch(Throwable ignored){}
                }
                TechCellCloudSync.Resultado fim=r;
                runOnUiThread(()->{
                    ocupado=false;
                    String msg=fim.ok
                            ? "Conta Administrador criada e empresa registrada na nuvem. Agora você pode cadastrar as outras contas."
                            : "A conta foi criada e ficou conectada, mas a empresa ainda não foi registrada.\n\n"+fim.mensagem+"\n\nVocê poderá usar o botão de registrar empresa para tentar novamente.";
                    new AlertDialog.Builder(this).setTitle(fim.ok?"Administrador criado ✓":"Conta criada — falta registrar empresa")
                            .setMessage(msg).setPositiveButton("OK",(d,w)->render()).setCancelable(false).show();
                });
            },"TechCell-Cloud-FirstAdmin").start();
        });
    }

    private void adicionarOperacoes(LinearLayout root,boolean masterFisico,boolean administrador,TechCellCloudSync.Estado estado){
        TechCellAccess.Sessao sessao=TechCellAccess.sessao(this);

        if(masterFisico && administrador){
            LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
            card.addView(txt("Configuração da empresa",16,true));
            TextView aviso=txt("O Master registra a empresa e faz a carga inicial. Depois disso, as alterações passam a circular automaticamente pela nuvem.",11,false);aviso.setTextColor(TechCellUi.MUTED);aviso.setPadding(0,dp(4),0,dp(8));card.addView(aviso);

            Button testar=action("1. Testar conexão / registrar empresa");TechCellUi.styleSecondary(this,testar);habilitar(testar,true);
            testar.setOnClickListener(v->executar("Conexão com a nuvem",()->TechCellCloudSync.testarRegistrarEmpresa(getApplicationContext())));
            card.addView(testar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

            Button carga=action("2. Enviar carga inicial de cadastros");TechCellUi.stylePrimary(this,carga,TechCellUi.BLUE);habilitar(carga,true);
            carga.setOnClickListener(v->confirmarCarga());
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));cp.setMargins(0,dp(8),0,0);card.addView(carga,cp);

            Button pend=action("Sincronizar nuvem agora");TechCellUi.styleSecondary(this,pend);habilitar(pend,estado.cloudAtiva);
            pend.setOnClickListener(v->sincronizarNuvemAgora());
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));pp.setMargins(0,dp(8),0,0);card.addView(pend,pp);
            root.addView(card);
        }else{
            LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
            card.addView(txt("Conta conectada",16,true));
            TextView perfil=txt("Usuário: "+(sessao.nome==null||sessao.nome.trim().isEmpty()?sessao.email:sessao.nome)+"\nNível: "+TechCellAccess.perfilExibicao(sessao.perfil)+"\nOs dados desta empresa são recebidos pela internet sem exigir o mesmo roteador do Master.",12,false);
            perfil.setTextColor(TechCellUi.NAVY);perfil.setPadding(0,dp(5),0,dp(8));card.addView(perfil);
            Button agora=action("↻  Sincronizar nuvem agora");TechCellUi.stylePrimary(this,agora,TechCellUi.BLUE);habilitar(agora,estado.cloudAtiva);
            agora.setOnClickListener(v->sincronizarNuvemAgora());
            card.addView(agora,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
            root.addView(card);
        }

        if(administrador){
            LinearLayout contas=TechCellUi.card(this);contas.setLayoutParams(TechCellUi.fullCardParams(this,9));
            contas.addView(txt("👥 Usuários / Contas",16,true));
            TextView co=txt("A conta Administrador gerencia as contas da empresa mesmo em acesso remoto. Perfis disponíveis: Administrador, Gerente e Caixa. Nenhuma senha é armazenada no aparelho.",11,false);co.setTextColor(TechCellUi.MUTED);co.setPadding(0,dp(4),0,dp(8));contas.addView(co);

            Button novo=action("＋  Cadastrar nova conta");TechCellUi.stylePrimary(this,novo,TechCellUi.GREEN);habilitar(novo,true);
            novo.setOnClickListener(v->mostrarNovoUsuario());
            contas.addView(novo,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

            Button listar=action("Ver / gerenciar contas cadastradas");TechCellUi.styleSecondary(this,listar);habilitar(listar,true);
            listar.setOnClickListener(v->mostrarUsuarios());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));lp.setMargins(0,dp(8),0,0);contas.addView(listar,lp);
            root.addView(contas);
        }

        LinearLayout nota=TechCellUi.card(this);nota.setLayoutParams(TechCellUi.fullCardParams(this,8));
        TextView nt=txt("Conta ≠ função do aparelho",13,true);nt.setTextColor(TechCellUi.NAVY);nota.addView(nt);
        TextView nv=txt("O aparelho pode ser Master, Caixa ou acesso remoto. Separadamente, a pessoa entra como Administrador, Gerente ou Caixa e recebe as permissões da própria conta.",11,false);nv.setTextColor(TechCellUi.MUTED);nv.setPadding(0,dp(4),0,0);nota.addView(nv);root.addView(nota);

        Button sair=action("Sair da conta da nuvem");TechCellUi.styleSecondary(this,sair);sair.setOnClickListener(v->{TechCellAccess.encerrar(this);TechCellCloudAutoSync.parar();render();});
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));sp.setMargins(0,dp(9),0,0);root.addView(sair,sp);
    }

    private void sincronizarNuvemAgora(){
        if(ocupado)return;ocupado=true;
        Toast.makeText(this,"Sincronizando pela internet…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            TechCellCloudRealtimeSync.Resultado r=TechCellCloudRealtimeSync.sincronizar(getApplicationContext());
            runOnUiThread(()->{
                ocupado=false;
                if(r.ok){
                    String msg="Enviados: "+r.enviados+"\nRecebidos: "+r.recebidos+"\nVendas recebidas: "+r.vendasRecebidas+"\nExclusões: "+r.exclusoes;
                    new AlertDialog.Builder(this).setTitle("Nuvem sincronizada ✓").setMessage(msg).setPositiveButton("OK",(d,w)->render()).show();
                }else{
                    mostrarErro("Sincronização da nuvem",r.erro);
                    render();
                }
            });
        },"TechCell-Cloud-Now").start();
    }

    private void mostrarNovoUsuario(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(4),dp(20),0);
        EditText nome=new EditText(this);nome.setHint("Nome do usuário");nome.setSingleLine(true);form.addView(nome);
        EditText email=new EditText(this);email.setHint("E-mail");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);form.addView(email);
        EditText senha=new EditText(this);senha.setHint("Senha inicial (mínimo 6 caracteres)");senha.setSingleLine(true);senha.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(senha);
        Spinner perfil=new Spinner(this);
        String[] perfis={"Caixa","Gerente","Administrador"};
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,perfis);perfil.setAdapter(ad);form.addView(perfil);

        new AlertDialog.Builder(this).setTitle("Cadastrar nova conta")
                .setView(form)
                .setPositiveButton("Cadastrar",(d,w)->{
                    String n=nome.getText().toString().trim();
                    String e=email.getText().toString().trim();
                    String s=senha.getText().toString();
                    String p=String.valueOf(perfil.getSelectedItem()).toUpperCase(Locale.ROOT);
                    cadastrarUsuario(n,e,s,p);
                })
                .setNegativeButton("Cancelar",null).show();
    }

    private void cadastrarUsuario(String nome,String email,String senha,String perfil){
        if(ocupado)return;ocupado=true;
        new Thread(()->{
            try{
                TechCellCloudUsers.Usuario u=TechCellCloudUsers.criar(getApplicationContext(),nome,email,senha,perfil);
                runOnUiThread(()->{
                    ocupado=false;
                    new AlertDialog.Builder(this).setTitle("Conta cadastrada ✓")
                            .setMessage(u.nome+"\n"+u.email+"\nPerfil: "+u.perfilExibicao()+"\n\nA conta já está vinculada a esta empresa.")
                            .setPositiveButton("OK",null).show();
                });
            }catch(Throwable e){
                runOnUiThread(()->{ocupado=false;mostrarErro("Não foi possível cadastrar",TechCellCloudUsers.mensagem(e));});
            }
        },"TechCell-Cloud-NewUser").start();
    }

    private void mostrarUsuarios(){
        if(ocupado)return;ocupado=true;
        Toast.makeText(this,"Buscando contas…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                List<TechCellCloudUsers.Usuario> lista=TechCellCloudUsers.listar(getApplicationContext());
                runOnUiThread(()->{
                    ocupado=false;
                    String[] itens=new String[lista.size()];
                    for(int i=0;i<lista.size();i++){
                        TechCellCloudUsers.Usuario u=lista.get(i);
                        itens[i]=(u.ativo?"✓ ":"⛔ ")+u.nome+"\n"+u.email+" • "+u.perfilExibicao();
                    }
                    new AlertDialog.Builder(this).setTitle("Contas da empresa")
                            .setItems(itens,(d,which)->acoesUsuario(lista.get(which)))
                            .setNegativeButton("Fechar",null).show();
                });
            }catch(Throwable e){
                runOnUiThread(()->{ocupado=false;mostrarErro("Não foi possível listar as contas",TechCellCloudUsers.mensagem(e));});
            }
        },"TechCell-Cloud-ListUsers").start();
    }

    private void acoesUsuario(TechCellCloudUsers.Usuario u){
        if(u==null)return;
        if(u.proprietario){
            new AlertDialog.Builder(this).setTitle(u.nome)
                    .setMessage(u.email+"\nPerfil: PROPRIETÁRIO / Administrador\nStatus: ativo")
                    .setItems(new String[]{"Enviar redefinição de senha"},(d,w)->enviarReset(u))
                    .setNegativeButton("Fechar",null).show();
            return;
        }
        String alternar=u.ativo?"Bloquear acesso à nuvem":"Reativar acesso à nuvem";
        new AlertDialog.Builder(this).setTitle(u.nome)
                .setMessage(u.email+"\nPerfil: "+u.perfilExibicao()+"\nStatus: "+(u.ativo?"ativo":"bloqueado"))
                .setItems(new String[]{alternar,"Enviar redefinição de senha"},(d,w)->{
                    if(w==0)alterarAtivo(u,!u.ativo);else enviarReset(u);
                })
                .setNegativeButton("Fechar",null).show();
    }

    private void alterarAtivo(TechCellCloudUsers.Usuario u,boolean ativo){
        executarConta(ativo?"Reativar conta":"Bloquear conta",()->{
            TechCellCloudUsers.alterarAtivo(getApplicationContext(),u,ativo);
            return ativo?"Conta reativada.":"Acesso desta conta à empresa foi bloqueado.";
        });
    }

    private void enviarReset(TechCellCloudUsers.Usuario u){
        executarConta("Redefinição de senha",()->{
            TechCellCloudUsers.enviarRedefinicaoSenha(getApplicationContext(),u.email);
            return "E-mail de redefinição de senha solicitado para "+u.email+".";
        });
    }

    private interface ContaOp { String rodar() throws Exception; }
    private void executarConta(String titulo,ContaOp op){
        if(ocupado)return;ocupado=true;
        new Thread(()->{
            try{
                String msg=op.rodar();
                runOnUiThread(()->{ocupado=false;new AlertDialog.Builder(this).setTitle(titulo+" ✓").setMessage(msg).setPositiveButton("OK",null).show();});
            }catch(Throwable e){
                runOnUiThread(()->{ocupado=false;mostrarErro(titulo,TechCellCloudUsers.mensagem(e));});
            }
        },"TechCell-Cloud-UserAction").start();
    }

    private void confirmarCarga(){
        new AlertDialog.Builder(this).setTitle("Enviar cadastros para a nuvem?")
                .setMessage("Serão enviados produtos, clientes, fornecedores, despesas e a configuração da empresa desta base de teste.\n\nDepois da carga, as alterações passam a sincronizar automaticamente entre os aparelhos conectados à mesma empresa.")
                .setPositiveButton("Enviar carga",(d,w)->executar("Carga inicial",()->TechCellCloudSync.enviarCargaInicialCadastros(getApplicationContext())))
                .setNegativeButton("Cancelar",null).show();
    }

    private interface Operacao { TechCellCloudSync.Resultado rodar(); }
    private void executar(String titulo,Operacao op){
        if(ocupado)return;ocupado=true;render();
        new Thread(()->{
            TechCellCloudSync.Resultado r=op.rodar();
            if(r.ok && titulo.toLowerCase(Locale.ROOT).contains("conexão")){
                try{TechCellCloudUsers.garantirProprietario(getApplicationContext());}catch(Throwable ignored){}
            }
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
