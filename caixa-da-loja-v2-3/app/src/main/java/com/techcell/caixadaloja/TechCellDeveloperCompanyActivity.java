package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Central estrutural do cliente: cadastro, Master, plano e quantidade de licenças.
 * Somente a conta Desenvolvedor consegue abrir esta tela.
 */
public class TechCellDeveloperCompanyActivity extends Activity {
    public static final String EXTRA_EMPRESA_UUID = "empresa_uuid";

    private String empresaUuid = "";
    private boolean nova;
    private boolean ocupado;
    private TechCellDeveloperCompanyManager.Dados dados;

    private EditText fantasia;
    private EditText razao;
    private EditText cnpj;
    private EditText telefone;
    private EditText cidade;
    private EditText masterNome;
    private EditText masterEmail;
    private EditText masterSenha;
    private EditText masterSenhaConfirmar;
    private Spinner status;
    private Spinner plano;
    private CheckBox masterAtivo;
    private EditText caixas;

    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView txt(String s,int z,boolean b){ TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t; }
    private Button botao(String s){ Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b; }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        TechCellUi.applyWindowChrome(this);
        try { TechCellDeveloperAccess.exigir(this); }
        catch(Throwable e){ erro("Acesso negado",e,true); return; }
        String id=getIntent().getStringExtra(EXTRA_EMPRESA_UUID);
        empresaUuid=id==null?"":id.trim();
        nova=empresaUuid.isEmpty();
        if(nova) render(null); else carregar();
    }

    private void carregar(){
        if(ocupado)return;ocupado=true;
        telaCarregando("Carregando cadastro da empresa…");
        new Thread(()->{
            try{
                TechCellDeveloperCompanyManager.Dados d=TechCellDeveloperCompanyManager.ler(getApplicationContext(),empresaUuid);
                runOnUiThread(()->{ocupado=false;dados=d;render(d);});
            }catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar a empresa",e,true);});}
        },"TechCell-Company-Load").start();
    }

    private void telaCarregando(String mensagem){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(40),dp(20),dp(20));
        TextView t=txt(mensagem,16,true);t.setPadding(0,dp(30),0,0);root.addView(t);setContentView(root);
    }

    private void render(TechCellDeveloperCompanyManager.Dados d){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(34));scroll.addView(root);

        Button voltar=botao("←  Voltar à Central da Plataforma");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        TextView titulo=txt(nova?"＋ Nova empresa":"🏢 Cadastro e contrato",28,true);titulo.setPadding(0,dp(16),0,dp(3));root.addView(titulo);
        TextView sub=txt(nova?"Crie o cliente do zero: empresa → Master → plano → licenças → ativação.":"Somente o Desenvolvedor altera dados comerciais, Master, plano e quantidade contratada.",12,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout empresa=TechCellUi.card(this);empresa.setLayoutParams(TechCellUi.fullCardParams(this,14));
        empresa.addView(txt("1. Dados da empresa",18,true));
        fantasia=campo("Nome fantasia",false);fantasia.setText(d==null?"":d.fantasia);addCampo(empresa,"Nome fantasia *",fantasia,10);
        razao=campo("Razão social (opcional)",false);razao.setText(d==null?"":d.razao);addCampo(empresa,"Razão social",razao,9);
        cnpj=campo("CNPJ (opcional)",false);cnpj.setText(d==null?"":d.cnpj);addCampo(empresa,"CNPJ",cnpj,9);
        telefone=campo("Telefone / WhatsApp (opcional)",false);telefone.setText(d==null?"":d.telefone);telefone.setInputType(InputType.TYPE_CLASS_PHONE);addCampo(empresa,"Telefone",telefone,9);
        cidade=campo("Cidade / UF (opcional)",false);cidade.setText(d==null?"":d.cidade);addCampo(empresa,"Cidade / UF",cidade,9);
        root.addView(empresa);

        LinearLayout master=TechCellUi.card(this);master.setLayoutParams(TechCellUi.fullCardParams(this,9));
        master.addView(txt("2. Master da empresa",18,true));
        if(nova){
            masterNome=campo("Nome do responsável",false);addCampo(master,"Nome do Master *",masterNome,10);
            masterEmail=campo("E-mail do Master",false);masterEmail.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);addCampo(master,"E-mail de login *",masterEmail,9);
            masterSenha=campo("Senha inicial do Master",true);addCampo(master,"Senha inicial *",masterSenha,9);
            masterSenhaConfirmar=campo("Repita a senha inicial",true);addCampo(master,"Confirmar senha *",masterSenhaConfirmar,9);
            TextView info=txt("O Desenvolvedor define uma senha inicial de pelo menos 6 caracteres. O Master já poderá entrar imediatamente com e-mail + essa senha. A senha não é gravada no banco e não ficará visível depois.",11,false);info.setTextColor(TechCellUi.GREEN);info.setPadding(0,dp(9),0,0);master.addView(info);
        }else{
            TextView atual=txt("Master atual\n"+(d.ownerNome==null?"Master":d.ownerNome)+"\n"+(d.ownerEmail==null?"":d.ownerEmail),13,true);atual.setPadding(0,dp(10),0,dp(10));master.addView(atual);

            Button definirSenha=botao("🔐  Definir nova senha do Master");TechCellUi.stylePrimary(this,definirSenha,TechCellUi.BLUE);definirSenha.setOnClickListener(v->dialogNovaSenhaMaster());master.addView(definirSenha,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

            Button trocar=botao("⇄  Substituir Master");TechCellUi.styleSecondary(this,trocar);trocar.setOnClickListener(v->dialogTrocarMaster());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));tp.setMargins(0,dp(7),0,0);master.addView(trocar,tp);

            Button senhaEmail=botao("✉  Enviar redefinição por e-mail");TechCellUi.styleSecondary(this,senhaEmail);senhaEmail.setOnClickListener(v->reenviarSenha());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));sp.setMargins(0,dp(7),0,0);master.addView(senhaEmail,sp);

            TextView info=txt("Definir nova senha mantém o mesmo Master e o mesmo e-mail. A senha atual nunca é exibida: ela é substituída por uma nova senha escolhida pelo Desenvolvedor. A senha não é salva no Firestore nem na auditoria.",11,false);info.setTextColor(TechCellUi.MUTED);info.setPadding(0,dp(9),0,0);master.addView(info);
        }
        root.addView(master);

        LinearLayout lic=TechCellUi.card(this);lic.setLayoutParams(TechCellUi.fullCardParams(this,9));
        lic.addView(txt("3. Contrato e licença",18,true));
        TextView s1=txt("Status",11,true);s1.setPadding(0,dp(10),0,0);lic.addView(s1);
        status=new Spinner(this);String[] sts={"ATIVA","PENDENTE","SUSPENSA","CANCELADA"};status.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sts));selecionar(status,sts,d==null?"ATIVA":(d.licenca.existe?d.licenca.status:"ATIVA"));lic.addView(status);
        TextView s2=txt("Plano",11,true);s2.setPadding(0,dp(9),0,0);lic.addView(s2);
        plano=new Spinner(this);String[] ps={"LOCAL","HIBRIDO","NUVEM"};plano.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ps));selecionar(plano,ps,d==null?"HIBRIDO":(d.licenca.existe?d.licenca.plano:"HIBRIDO"));lic.addView(plano);
        masterAtivo=new CheckBox(this);masterAtivo.setText("Master liberado para usar o sistema");masterAtivo.setChecked(d==null||!d.licenca.existe||d.licenca.masterAtivo);lic.addView(masterAtivo);
        caixas=campo("Quantidade de licenças Caixa",false);caixas.setInputType(InputType.TYPE_CLASS_NUMBER);int qtd=d==null?1:(d.licenca.existe?d.licenca.caixasContratados:Math.max(1,d.caixasEmUso));caixas.setText(String.valueOf(qtd));addCampo(lic,"Licenças Caixa contratadas",caixas,8);
        if(d!=null){TextView uso=txt("Em uso agora: "+d.caixasEmUso+" / "+qtd,11,true);uso.setTextColor(TechCellUi.BLUE);uso.setPadding(0,dp(7),0,0);lic.addView(uso);}
        TextView detalhe=txt("Plano, status, liberação do Master e quantidade de Caixas são comerciais. O Master da loja pode visualizar, mas não aumentar nem reduzir o contrato.",11,false);detalhe.setTextColor(TechCellUi.MUTED);detalhe.setPadding(0,dp(9),0,0);lic.addView(detalhe);
        root.addView(lic);

        Button salvar=botao(nova?"CRIAR EMPRESA E MASTER":"SALVAR CADASTRO E CONTRATO");TechCellUi.stylePrimary(this,salvar,TechCellUi.BLUE);salvar.setOnClickListener(v->confirmarSalvar());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));bp.setMargins(0,dp(14),0,0);root.addView(salvar,bp);

        if(!nova){
            LinearLayout audit=TechCellUi.card(this);audit.setLayoutParams(TechCellUi.fullCardParams(this,10));audit.addView(txt("Auditoria",13,true));TextView a=txt("Criação, troca de Master, redefinição de senha e alterações de plano/licenças são registradas com a identidade do Desenvolvedor. Senhas nunca entram na auditoria.",11,false);a.setTextColor(TechCellUi.MUTED);a.setPadding(0,dp(5),0,0);audit.addView(a);root.addView(audit);
        }
        setContentView(scroll);
    }

    private EditText campo(String hint,boolean senha){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextSize(16);if(senha)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);TechCellUi.styleSearch(this,e);return e;}
    private void addCampo(LinearLayout box,String titulo,EditText e,int top){TextView l=txt(titulo,12,true);l.setPadding(0,dp(top),0,dp(4));box.addView(l);box.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));}
    private void selecionar(Spinner s,String[] itens,String valor){if(valor==null)return;for(int i=0;i<itens.length;i++)if(itens[i].equalsIgnoreCase(valor)){s.setSelection(i);return;}}
    private int caixas(){try{return Math.max(0,Integer.parseInt(caixas.getText().toString().trim()));}catch(Throwable e){return 0;}}
    private boolean senhaValida(String a,String b){if(a==null||a.length()<6){Toast.makeText(this,"A senha do Master deve ter pelo menos 6 caracteres.",Toast.LENGTH_LONG).show();return false;}if(!a.equals(b)){Toast.makeText(this,"As duas senhas do Master não são iguais.",Toast.LENGTH_LONG).show();return false;}return true;}

    private void confirmarSalvar(){
        String nome=fantasia.getText().toString().trim();
        if(nome.isEmpty()){Toast.makeText(this,"Informe o nome fantasia.",Toast.LENGTH_LONG).show();return;}
        if(nova&&!senhaValida(masterSenha.getText().toString(),masterSenhaConfirmar.getText().toString()))return;
        String st=String.valueOf(status.getSelectedItem());String pl=String.valueOf(plano.getSelectedItem());int q=caixas();
        String msg=(nova?"Criar nova empresa":"Alterar empresa")+"\n\nNome: "+nome+"\nPlano: "+pl+"\nStatus: "+st+"\nMaster: "+(masterAtivo.isChecked()?"LIBERADO":"BLOQUEADO")+"\nCaixas contratados: "+q+(nova?"\n\nA conta Master será criada com a senha inicial que você informou e poderá entrar imediatamente.":"\n\nEsta alteração é REAL e será auditada.");
        new AlertDialog.Builder(this).setTitle(nova?"Confirmar criação":"Confirmar alteração").setMessage(msg).setPositiveButton(nova?"CRIAR EMPRESA E MASTER":"SALVAR REAL",(d,w)->salvar()).setNegativeButton("Cancelar",null).show();
    }

    private void salvar(){
        if(ocupado)return;ocupado=true;Toast.makeText(this,nova?"Criando empresa e Master…":"Salvando empresa…",Toast.LENGTH_LONG).show();
        final String f=fantasia.getText().toString(),r=razao.getText().toString(),cj=cnpj.getText().toString(),tel=telefone.getText().toString(),cid=cidade.getText().toString();
        final String st=String.valueOf(status.getSelectedItem()),pl=String.valueOf(plano.getSelectedItem());final boolean ma=masterAtivo.isChecked();final int q=caixas();final String senhaInicial=nova?masterSenha.getText().toString():"";
        new Thread(()->{
            try{
                TechCellDeveloperCompanyManager.Dados out;
                if(nova){out=TechCellDeveloperCompanyManager.criar(getApplicationContext(),f,r,cj,tel,cid,masterNome.getText().toString(),masterEmail.getText().toString(),senhaInicial,st,pl,ma,q);}
                else{out=TechCellDeveloperCompanyManager.atualizar(getApplicationContext(),dados,f,r,cj,tel,cid,st,pl,ma,q);}
                TechCellDeveloperCompanyManager.Dados finalOut=out;
                runOnUiThread(()->{ocupado=false;dados=finalOut;empresaUuid=finalOut.empresaUuid;nova=false;new AlertDialog.Builder(this).setTitle("Empresa pronta").setMessage("Empresa: "+finalOut.fantasia+"\nMaster: "+finalOut.ownerEmail+"\nPlano: "+finalOut.licenca.plano+"\nCaixas: "+finalOut.licenca.caixasContratados+"\n\nO Master já pode entrar usando o e-mail cadastrado e a senha inicial que você definiu.").setPositiveButton("OK",(x,y)->render(finalOut)).show();});
            }catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível salvar a empresa",e,false);});}
        },"TechCell-Company-Save").start();
    }

    private void dialogTrocarMaster(){
        if(dados==null)return;
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(2),dp(20),0);
        EditText nome=campo("Nome do novo Master",false);f.addView(nome);
        EditText email=campo("E-mail do novo Master",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));ep.setMargins(0,dp(8),0,0);f.addView(email,ep);
        EditText senha=campo("Senha inicial do novo Master",true);LinearLayout.LayoutParams sp1=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));sp1.setMargins(0,dp(8),0,0);f.addView(senha,sp1);
        EditText confirmar=campo("Repita a senha inicial",true);LinearLayout.LayoutParams sp2=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));sp2.setMargins(0,dp(8),0,0);f.addView(confirmar,sp2);
        TextView info=txt("O novo Master já poderá entrar imediatamente com o e-mail e a senha inicial informados. O Master anterior perde o vínculo com esta empresa.",11,false);info.setTextColor(TechCellUi.RED);info.setPadding(0,dp(8),0,0);f.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Substituir Master • REAL").setView(f).setPositiveButton("CONTINUAR",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String s=senha.getText().toString();if(!senhaValida(s,confirmar.getText().toString()))return;dialog.dismiss();confirmarTroca(nome.getText().toString(),email.getText().toString(),s);}));
        dialog.show();
    }

    private void confirmarTroca(String nome,String email,String senha){
        new AlertDialog.Builder(this).setTitle("Confirmar novo Master?").setMessage("Empresa: "+dados.fantasia+"\nMaster atual: "+dados.ownerEmail+"\nNovo Master: "+email+"\n\nO novo Master será criado com a senha inicial informada. A conta anterior perderá acesso a esta empresa. A ação será auditada.").setPositiveButton("SUBSTITUIR MASTER",(d,w)->trocarMaster(nome,email,senha)).setNegativeButton("Cancelar",null).show();
    }

    private void trocarMaster(String nome,String email,String senha){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Substituindo Master…",Toast.LENGTH_LONG).show();
        new Thread(()->{try{TechCellDeveloperCompanyManager.Dados out=TechCellDeveloperCompanyManager.substituirMaster(getApplicationContext(),dados,nome,email,senha);runOnUiThread(()->{ocupado=false;dados=out;new AlertDialog.Builder(this).setTitle("Master substituído").setMessage("Novo Master: "+out.ownerEmail+"\n\nEle já pode entrar com o e-mail e a senha inicial que você definiu.").setPositiveButton("OK",(x,y)->render(out)).show();});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível substituir o Master",e,false);});}},"TechCell-Replace-Master").start();
    }

    private void dialogNovaSenhaMaster(){
        if(dados==null)return;
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(2),dp(20),0);
        TextView conta=txt("Master atual\n"+dados.ownerEmail,12,true);conta.setPadding(0,0,0,dp(8));f.addView(conta);
        EditText senha=campo("Nova senha do Master",true);f.addView(senha,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        EditText confirmar=campo("Repita a nova senha",true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));cp.setMargins(0,dp(8),0,0);f.addView(confirmar,cp);
        TextView info=txt("A senha atual não pode ser exibida. Esta operação substitui a senha do mesmo Master por uma nova. A senha não será armazenada no banco.",11,false);info.setTextColor(TechCellUi.MUTED);info.setPadding(0,dp(9),0,0);f.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Definir nova senha • REAL").setView(f).setPositiveButton("CONTINUAR",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String s=senha.getText().toString();if(!senhaValida(s,confirmar.getText().toString()))return;dialog.dismiss();confirmarNovaSenhaMaster(s);}));
        dialog.show();
    }

    private void confirmarNovaSenhaMaster(String senha){
        new AlertDialog.Builder(this).setTitle("Alterar a senha deste Master?").setMessage("Empresa: "+dados.fantasia+"\nMaster: "+dados.ownerEmail+"\n\nA senha anterior deixará de funcionar imediatamente. O Master continuará sendo a mesma conta e a ação será auditada.").setPositiveButton("ALTERAR SENHA",(d,w)->alterarSenhaMaster(senha)).setNegativeButton("Cancelar",null).show();
    }

    private void alterarSenhaMaster(String senha){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Alterando senha do Master…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            try{
                TechCellDeveloperPasswordAdmin.definirSenhaMaster(getApplicationContext(),dados,senha);
                runOnUiThread(()->{ocupado=false;new AlertDialog.Builder(this).setTitle("Senha alterada").setMessage("A nova senha do Master foi definida.\n\nEle já pode entrar com:\n"+dados.ownerEmail+"\n+ a nova senha escolhida.").setPositiveButton("OK",null).show();});
            }catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível alterar a senha do Master",e,false);});}
        },"TechCell-Master-Password").start();
    }

    private void reenviarSenha(){
        if(dados==null||dados.ownerEmail==null||dados.ownerEmail.trim().isEmpty())return;if(ocupado)return;ocupado=true;Toast.makeText(this,"Enviando e-mail…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{TechCellDeveloperCompanyManager.reenviarDefinicaoSenha(getApplicationContext(),dados.empresaUuid,dados.ownerEmail);runOnUiThread(()->{ocupado=false;Toast.makeText(this,"E-mail de redefinição enviado ao Master.",Toast.LENGTH_LONG).show();});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível enviar o e-mail",e,false);});}},"TechCell-Master-Reset").start();
    }

    private void erro(String titulo,Throwable e,boolean fechar){
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(titulo).setMessage(TechCellCloudUsers.mensagem(e));
        if(fechar)b.setPositiveButton("Voltar",(d,w)->finish()).setCancelable(false);else b.setPositiveButton("OK",null);b.show();
    }
}
