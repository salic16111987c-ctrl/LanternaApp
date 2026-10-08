package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import java.util.List;

/** Painel global acima do Master: empresas, licenças e acesso de teste do Desenvolvedor. */
public class TechCellDeveloperActivity extends Activity {
    private LinearLayout lista;
    private boolean ocupado;

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);render();carregar();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button voltar=botao("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView titulo=txt("Painel do Desenvolvedor",27,true);titulo.setPadding(0,dp(16),0,dp(2));root.addView(titulo);
        TextView sub=txt("Nível supremo da plataforma • acima do Master de cada empresa",13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout hierarquia=TechCellUi.card(this);hierarquia.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView h=txt("DESENVOLVEDOR  →  MASTER  →  GERENTE / CAIXA",13,true);h.setTextColor(TechCellUi.BLUE);hierarquia.addView(h);
        TextView hd=txt("Além de liberar licenças, você pode entrar temporariamente no modo de teste de qualquer usuário. Ao sair do teste, volta automaticamente para o Desenvolvedor sem pedir sua senha novamente.",11,false);hd.setTextColor(TechCellUi.MUTED);hd.setPadding(0,dp(5),0,0);hierarquia.addView(hd);root.addView(hierarquia);

        LinearLayout seguro=TechCellUi.card(this);seguro.setLayoutParams(TechCellUi.fullCardParams(this,6));
        TextView st=txt("MODO DE TESTE ISOLADO",12,true);st.setTextColor(TechCellUi.GREEN);seguro.addView(st);
        TextView sd=txt("A base da empresa é copiada da nuvem para um SQLite temporário. Vendas e alterações feitas enquanto você testa ficam somente nesse banco temporário e são descartadas ao voltar para o Desenvolvedor.",11,false);sd.setTextColor(TechCellUi.MUTED);sd.setPadding(0,dp(4),0,0);seguro.addView(sd);root.addView(seguro);

        Button atualizar=botao("↻  Atualizar empresas");TechCellUi.stylePrimary(this,atualizar,TechCellUi.BLUE);atualizar.setOnClickListener(v->carregar());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));ap.setMargins(0,0,0,dp(8));root.addView(atualizar,ap);
        lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);
        setContentView(scroll);
    }

    private void carregar(){
        if(ocupado||lista==null)return;ocupado=true;lista.removeAllViews();
        TextView t=txt("Validando conta Desenvolvedor e carregando empresas…",13,true);t.setPadding(0,dp(14),0,0);lista.addView(t);
        new Thread(()->{
            try{
                TechCellDeveloperAccess.Estado dev=TechCellDeveloperAccess.atualizar(getApplicationContext());
                if(!dev.desenvolvedor)throw new IllegalStateException("Esta conta ainda não foi cadastrada como Desenvolvedor da plataforma.");
                List<TechCellDeveloper.Empresa> es=TechCellDeveloper.listarEmpresas(getApplicationContext());
                runOnUiThread(()->{ocupado=false;mostrar(es);});
            }catch(Throwable e){runOnUiThread(()->{ocupado=false;lista.removeAllViews();erro("Painel do Desenvolvedor",e);});}
        },"TechCell-Developer-Load").start();
    }

    private void mostrar(List<TechCellDeveloper.Empresa> es){
        lista.removeAllViews();
        int total=es==null?0:es.size(), ativas=0, pendentes=0, suspensas=0;
        if(es!=null)for(TechCellDeveloper.Empresa e:es){
            if(e.licenca.existe&&TechCellLicenseManager.ATIVA.equalsIgnoreCase(e.licenca.status))ativas++;
            else if(!e.licenca.existe||TechCellLicenseManager.PENDENTE.equalsIgnoreCase(e.licenca.status))pendentes++;
            else if(TechCellLicenseManager.SUSPENSA.equalsIgnoreCase(e.licenca.status))suspensas++;
        }
        LinearLayout resumo=TechCellUi.card(this);resumo.setLayoutParams(TechCellUi.fullCardParams(this,8));
        resumo.addView(txt("Empresas: "+total+"  •  Ativas: "+ativas+"  •  Pendentes/legado: "+pendentes+"  •  Suspensas: "+suspensas,12,true));
        lista.addView(resumo);

        if(es==null||es.isEmpty()){
            TextView v=txt("Nenhuma empresa registrada na nuvem.",13,false);v.setTextColor(TechCellUi.MUTED);v.setPadding(0,dp(14),0,0);lista.addView(v);return;}
        for(TechCellDeveloper.Empresa e:es)card(e);
    }

    private void card(TechCellDeveloper.Empresa e){
        LinearLayout c=TechCellUi.card(this);c.setLayoutParams(TechCellUi.fullCardParams(this,9));
        String status=e.statusExibicao();
        String ic=TechCellLicenseManager.ATIVA.equalsIgnoreCase(status)?"✓  ":(TechCellLicenseManager.SUSPENSA.equalsIgnoreCase(status)?"⛔  ":"⚠  ");
        c.addView(txt(ic+e.nome,17,true));
        int emUso=e.licenca.existe?e.caixasEmUso:e.caixasAutorizadosLegado;
        int contratado=e.licenca.existe?e.licenca.caixasContratados:0;
        String plano=e.licenca.existe?e.licenca.plano:"—";
        String master=e.licenca.existe?(e.licenca.masterAtivo?"LIBERADO":"BLOQUEADO"):"LEGADO";
        TextView d=txt("Cliente/Master: "+(e.ownerEmail.isEmpty()?"—":e.ownerEmail)+
                "\nLicença: "+status+"  •  Plano: "+plano+
                "\nMaster: "+master+"  •  Caixas: "+emUso+" / "+contratado+
                (e.solicitacoesPendentes>0?"\n⚠ Solicitações de aparelho aguardando: "+e.solicitacoesPendentes:""),12,false);
        d.setTextColor(TechCellUi.MUTED);d.setPadding(0,dp(5),0,dp(9));c.addView(d);

        Button testar=botao("▶  Testar acesso como Master / Caixa");TechCellUi.stylePrimary(this,testar,TechCellUi.GREEN);testar.setOnClickListener(v->escolherUsuarioTeste(e));
        c.addView(testar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        Button gerenciar=botao("⚙  Gerenciar licença desta empresa");TechCellUi.styleSecondary(this,gerenciar);gerenciar.setOnClickListener(v->editar(e));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));gp.setMargins(0,dp(7),0,0);c.addView(gerenciar,gp);
        lista.addView(c);
    }

    private void escolherUsuarioTeste(TechCellDeveloper.Empresa e){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando usuários da empresa…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                List<TechCellDeveloper.UsuarioTeste> usuarios=TechCellDeveloper.listarUsuariosTeste(getApplicationContext(),e);
                runOnUiThread(()->{ocupado=false;mostrarUsuariosTeste(e,usuarios);});
            }catch(Throwable ex){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar os usuários",ex);});}
        },"TechCell-Developer-TestUsers").start();
    }

    private void mostrarUsuariosTeste(TechCellDeveloper.Empresa e,List<TechCellDeveloper.UsuarioTeste> usuarios){
        if(usuarios==null||usuarios.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Sem usuários").setMessage("Esta empresa ainda não possui usuários disponíveis para simulação.").setPositiveButton("OK",null).show();return;
        }
        String[] itens=new String[usuarios.size()];
        for(int i=0;i<usuarios.size();i++){
            TechCellDeveloper.UsuarioTeste u=usuarios.get(i);
            String ic=u.proprietario?"👑 ":("GERENTE".equalsIgnoreCase(u.perfil)?"🧑‍💼 ":"💵 ");
            itens[i]=ic+u.perfilExibicao()+" • "+u.nome+(u.email==null||u.email.isEmpty()?"":"\n"+u.email)+(u.ativo?"":"  [BLOQUEADO]");
        }
        new AlertDialog.Builder(this)
                .setTitle("Testar • "+e.nome)
                .setMessage("Escolha exatamente qual acesso deseja enxergar. Não é necessário saber a senha do cliente.")
                .setItems(itens,(d,which)->confirmarTeste(e,usuarios.get(which)))
                .setNegativeButton("Cancelar",null).show();
    }

    private void confirmarTeste(TechCellDeveloper.Empresa e,TechCellDeveloper.UsuarioTeste u){
        if(!u.ativo){new AlertDialog.Builder(this).setTitle("Usuário bloqueado").setMessage("Este usuário está bloqueado pelo Master e não pode iniciar uma sessão normal.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(this).setTitle("Entrar em modo de teste?")
                .setMessage("Empresa: "+e.nome+"\nAcesso simulado: "+u.perfilExibicao()+"\nUsuário: "+u.nome+"\n\nSua conta continuará sendo Desenvolvedor. Ao sair do teste, você voltará direto ao Painel do Desenvolvedor. Alterações do teste não serão enviadas à empresa.")
                .setPositiveButton("ENTRAR NO TESTE",(d,w)->iniciarTeste(e,u))
                .setNegativeButton("Cancelar",null).show();
    }

    private void iniciarTeste(TechCellDeveloper.Empresa e,TechCellDeveloper.UsuarioTeste u){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Preparando ambiente isolado de teste…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            try{
                TechCellDeveloperTestMode.iniciar(getApplicationContext(),e,u);
                runOnUiThread(()->{
                    ocupado=false;
                    Intent i=new Intent(this,TechCellHomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);finish();
                });
            }catch(Throwable ex){runOnUiThread(()->{ocupado=false;erro("Não foi possível iniciar o modo de teste",ex);});}
        },"TechCell-Developer-TestStart").start();
    }

    private void editar(TechCellDeveloper.Empresa e){
        ScrollView sv=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(4),dp(20),0);sv.addView(form);
        TextView nome=txt(e.nome+"\n"+e.ownerEmail,13,true);nome.setPadding(0,0,0,dp(8));form.addView(nome);

        TextView ls=txt("Status da licença",11,true);form.addView(ls);
        Spinner status=new Spinner(this);String[] sts={"PENDENTE","ATIVA","SUSPENSA","CANCELADA"};status.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sts));form.addView(status);
        String statusAtual=e.licenca.existe?e.licenca.status:"ATIVA";selecionar(status,sts,statusAtual);

        TextView lp=txt("Plano",11,true);lp.setPadding(0,dp(8),0,0);form.addView(lp);
        Spinner plano=new Spinner(this);String[] ps={"LOCAL","HIBRIDO","NUVEM"};plano.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ps));form.addView(plano);
        String planoAtual=e.licenca.existe?e.licenca.plano:"HIBRIDO";selecionar(plano,ps,planoAtual);

        CheckBox master=new CheckBox(this);master.setText("Licença Master liberada");master.setChecked(!e.licenca.existe||e.licenca.masterAtivo);form.addView(master);

        EditText caixas=new EditText(this);caixas.setHint("Quantidade de licenças Caixa");caixas.setInputType(InputType.TYPE_CLASS_NUMBER);
        int atual=e.licenca.existe?e.licenca.caixasContratados:Math.max(1,e.caixasAutorizadosLegado);
        caixas.setText(String.valueOf(atual));form.addView(caixas);

        TextView dica=txt("Cada licença Caixa corresponde a 1 aparelho Caixa autorizado. Ex.: 5 licenças = no máximo 5 aparelhos Caixa ativos. O Master escolhe quais aparelhos ocuparão essas vagas.",11,false);dica.setTextColor(TechCellUi.MUTED);dica.setPadding(0,dp(8),0,0);form.addView(dica);

        new AlertDialog.Builder(this).setTitle("Licença • "+e.nome).setView(sv)
                .setPositiveButton("SALVAR",(d,w)->{
                    int qtd;
                    try{qtd=Integer.parseInt(caixas.getText().toString().trim());}catch(Throwable x){qtd=0;}
                    salvar(e,String.valueOf(status.getSelectedItem()),String.valueOf(plano.getSelectedItem()),master.isChecked(),qtd);
                }).setNegativeButton("Cancelar",null).show();
    }

    private void salvar(TechCellDeveloper.Empresa e,String status,String plano,boolean master,int caixas){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Salvando licença…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                TechCellDeveloper.salvarLicenca(getApplicationContext(),e,status,plano,master,caixas);
                runOnUiThread(()->{ocupado=false;Toast.makeText(this,"Licença atualizada ✓",Toast.LENGTH_LONG).show();carregar();});
            }catch(Throwable ex){runOnUiThread(()->{ocupado=false;erro("Não foi possível salvar a licença",ex);});}
        },"TechCell-Developer-Save").start();
    }

    private void selecionar(Spinner s,String[] itens,String valor){
        for(int i=0;i<itens.length;i++)if(itens[i].equalsIgnoreCase(valor)){s.setSelection(i);return;}
    }
    private void erro(String titulo,Throwable e){String m=TechCellCloudUsers.mensagem(e);new AlertDialog.Builder(this).setTitle(titulo).setMessage(m).setPositiveButton("OK",null).show();}
}
