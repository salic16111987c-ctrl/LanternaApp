package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Central global da plataforma: teste isolado e administração real ficam explicitamente separados. */
public class TechCellDeveloperActivity extends Activity {
    private LinearLayout lista;
    private boolean ocupado;

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){super.onCreate(b);TechCellUi.applyWindowChrome(this);render();carregar();}

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button voltar=botao("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView titulo=txt("Central da Plataforma",27,true);titulo.setPadding(0,dp(16),0,dp(2));root.addView(titulo);
        TextView sub=txt("🛠 Desenvolvedor • controle global acima do Master",13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout hierarquia=TechCellUi.card(this);hierarquia.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView h=txt("DESENVOLVEDOR  →  MASTER  →  GERENTE / CAIXA",13,true);h.setTextColor(TechCellUi.BLUE);hierarquia.addView(h);
        TextView hd=txt("O Master continua administrando sua própria empresa. O Desenvolvedor pode administrar todas as empresas e também reproduzir o acesso de qualquer usuário sem alterar os dados reais.",11,false);hd.setTextColor(TechCellUi.MUTED);hd.setPadding(0,dp(5),0,0);hierarquia.addView(hd);root.addView(hierarquia);

        LinearLayout modos=TechCellUi.card(this);modos.setLayoutParams(TechCellUi.fullCardParams(this,7));
        TextView mt=txt("DOIS MODOS — NÃO CONFUNDIR",12,true);mt.setTextColor(TechCellUi.ORANGE);modos.addView(mt);
        TextView md=txt("🧪 Testar como usuário: ambiente isolado; não grava na empresa.\n\n🛠 Administrar empresa: alterações REAIS em usuários, permissões, dispositivos e licença, com auditoria.",11,false);md.setTextColor(TechCellUi.MUTED);md.setPadding(0,dp(5),0,0);modos.addView(md);root.addView(modos);

        Button atualizar=botao("↻  Atualizar empresas");TechCellUi.stylePrimary(this,atualizar,TechCellUi.BLUE);atualizar.setOnClickListener(v->carregar());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));ap.setMargins(0,dp(10),0,dp(8));root.addView(atualizar,ap);
        lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);setContentView(scroll);
    }

    private void carregar(){
        if(ocupado||lista==null)return;ocupado=true;lista.removeAllViews();
        TextView t=txt("Validando Desenvolvedor e carregando empresas…",13,true);t.setPadding(0,dp(14),0,0);lista.addView(t);
        new Thread(()->{try{TechCellDeveloperAccess.Estado dev=TechCellDeveloperAccess.atualizar(getApplicationContext());if(!dev.desenvolvedor)throw new IllegalStateException("Esta conta não possui acesso de Desenvolvedor.");List<TechCellDeveloper.Empresa> es=TechCellDeveloper.listarEmpresas(getApplicationContext());runOnUiThread(()->{ocupado=false;mostrar(es);});}catch(Throwable e){runOnUiThread(()->{ocupado=false;lista.removeAllViews();erro("Central da Plataforma",e);});}},"TechCell-Developer-Load").start();
    }

    private void mostrar(List<TechCellDeveloper.Empresa> es){
        lista.removeAllViews();int total=es==null?0:es.size(),ativas=0,pendentes=0,suspensas=0;
        if(es!=null)for(TechCellDeveloper.Empresa e:es){if(e.licenca.existe&&TechCellLicenseManager.ATIVA.equalsIgnoreCase(e.licenca.status))ativas++;else if(!e.licenca.existe||TechCellLicenseManager.PENDENTE.equalsIgnoreCase(e.licenca.status))pendentes++;else if(TechCellLicenseManager.SUSPENSA.equalsIgnoreCase(e.licenca.status))suspensas++;}
        LinearLayout resumo=TechCellUi.card(this);resumo.setLayoutParams(TechCellUi.fullCardParams(this,8));resumo.addView(txt("Empresas: "+total+"  •  Ativas: "+ativas+"  •  Pendentes/legado: "+pendentes+"  •  Suspensas: "+suspensas,12,true));lista.addView(resumo);
        if(es==null||es.isEmpty()){TextView v=txt("Nenhuma empresa registrada na nuvem.",13,false);v.setTextColor(TechCellUi.MUTED);v.setPadding(0,dp(14),0,0);lista.addView(v);return;}
        for(TechCellDeveloper.Empresa e:es)card(e);
    }

    private void card(TechCellDeveloper.Empresa e){
        LinearLayout c=TechCellUi.card(this);c.setLayoutParams(TechCellUi.fullCardParams(this,9));String status=e.statusExibicao();String ic=TechCellLicenseManager.ATIVA.equalsIgnoreCase(status)?"✓  ":(TechCellLicenseManager.SUSPENSA.equalsIgnoreCase(status)?"⛔  ":"⚠  ");c.addView(txt(ic+e.nome,17,true));
        int emUso=e.licenca.existe?e.caixasEmUso:e.caixasAutorizadosLegado;int contratado=e.licenca.existe?e.licenca.caixasContratados:0;String plano=e.licenca.existe?e.licenca.plano:"—";String master=e.licenca.existe?(e.licenca.masterAtivo?"LIBERADO":"BLOQUEADO"):"LEGADO";
        TextView d=txt("Cliente/Master: "+(e.ownerEmail.isEmpty()?"—":e.ownerEmail)+"\nLicença: "+status+"  •  Plano: "+plano+"\nMaster: "+master+"  •  Caixas: "+emUso+" / "+contratado+(e.solicitacoesPendentes>0?"\n⚠ Solicitações de aparelho: "+e.solicitacoesPendentes:""),12,false);d.setTextColor(TechCellUi.MUTED);d.setPadding(0,dp(5),0,dp(9));c.addView(d);

        Button testar=botao("🧪  Testar como usuário — NÃO GRAVA");TechCellUi.stylePrimary(this,testar,TechCellUi.GREEN);testar.setOnClickListener(v->escolherUsuarioTeste(e));c.addView(testar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        Button administrar=botao("🛠  Administrar empresa — ALTERA REAL");TechCellUi.stylePrimary(this,administrar,TechCellUi.RED);administrar.setOnClickListener(v->confirmarAdministracao(e));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));ap.setMargins(0,dp(8),0,0);c.addView(administrar,ap);lista.addView(c);
    }

    private void confirmarAdministracao(TechCellDeveloper.Empresa e){
        new AlertDialog.Builder(this).setTitle("🛠 Administração REAL")
                .setMessage("Empresa: "+e.nome+"\n\nNeste modo você poderá alterar Master, usuários, permissões, dispositivos e licença. As alterações serão gravadas na empresa e registradas na auditoria.\n\nPara apenas investigar sem salvar, use o modo 🧪 Testar.")
                .setPositiveButton("ENTRAR NA ADMINISTRAÇÃO",(d,w)->abrirAdministracao(e)).setNegativeButton("Cancelar",null).show();
    }

    private void abrirAdministracao(TechCellDeveloper.Empresa e){
        Intent i=new Intent(this,TechCellDeveloperAdminActivity.class);i.putExtra(TechCellDeveloperAdminActivity.EXTRA_EMPRESA_UUID,e.empresaUuid);i.putExtra(TechCellDeveloperAdminActivity.EXTRA_FILIAL_UUID,e.filialUuid);i.putExtra(TechCellDeveloperAdminActivity.EXTRA_EMPRESA_NOME,e.nome);i.putExtra(TechCellDeveloperAdminActivity.EXTRA_OWNER_UID,e.ownerUid);i.putExtra(TechCellDeveloperAdminActivity.EXTRA_OWNER_EMAIL,e.ownerEmail);startActivity(i);
    }

    private void escolherUsuarioTeste(TechCellDeveloper.Empresa e){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando usuários para teste…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{List<TechCellDeveloper.UsuarioTeste> usuarios=TechCellDeveloper.listarUsuariosTeste(getApplicationContext(),e);runOnUiThread(()->{ocupado=false;mostrarUsuariosTeste(e,usuarios);});}catch(Throwable ex){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar os usuários",ex);});}},"TechCell-Developer-TestUsers").start();
    }

    private void mostrarUsuariosTeste(TechCellDeveloper.Empresa e,List<TechCellDeveloper.UsuarioTeste> usuarios){
        if(usuarios==null||usuarios.isEmpty()){new AlertDialog.Builder(this).setTitle("Sem usuários").setMessage("Esta empresa ainda não possui usuários disponíveis para simulação.").setPositiveButton("OK",null).show();return;}
        String[] itens=new String[usuarios.size()];for(int i=0;i<usuarios.size();i++){TechCellDeveloper.UsuarioTeste u=usuarios.get(i);String ic=u.proprietario?"👑 ":("GERENTE".equalsIgnoreCase(u.perfil)?"🧑‍💼 ":"💵 ");itens[i]=ic+u.perfilExibicao()+" • "+u.nome+(u.email==null||u.email.isEmpty()?"":"\n"+u.email)+(u.ativo?"":"  [BLOQUEADO]");}
        new AlertDialog.Builder(this).setTitle("🧪 Testar • "+e.nome).setMessage("Ambiente isolado: nada do teste será enviado à empresa. Escolha exatamente qual usuário deseja reproduzir.").setItems(itens,(d,which)->confirmarTeste(e,usuarios.get(which))).setNegativeButton("Cancelar",null).show();
    }

    private void confirmarTeste(TechCellDeveloper.Empresa e,TechCellDeveloper.UsuarioTeste u){
        if(!u.ativo){new AlertDialog.Builder(this).setTitle("Usuário bloqueado").setMessage("Este usuário está bloqueado e não pode iniciar uma sessão normal.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(this).setTitle("🧪 Entrar no teste isolado?").setMessage("Empresa: "+e.nome+"\nAcesso: "+u.perfilExibicao()+"\nUsuário: "+u.nome+"\n\nSua conta continuará sendo Desenvolvedor. Ao sair, você voltará ao painel. Nenhuma alteração do teste será gravada na empresa.").setPositiveButton("ENTRAR NO TESTE",(d,w)->iniciarTeste(e,u)).setNegativeButton("Cancelar",null).show();
    }

    private void iniciarTeste(TechCellDeveloper.Empresa e,TechCellDeveloper.UsuarioTeste u){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Preparando ambiente isolado…",Toast.LENGTH_LONG).show();
        new Thread(()->{try{TechCellDeveloperTestMode.iniciar(getApplicationContext(),e,u);runOnUiThread(()->{ocupado=false;Intent i=new Intent(this,TechCellHomeActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();});}catch(Throwable ex){runOnUiThread(()->{ocupado=false;erro("Não foi possível iniciar o modo de teste",ex);});}},"TechCell-Developer-TestStart").start();
    }

    private void erro(String titulo,Throwable e){new AlertDialog.Builder(this).setTitle(titulo).setMessage(TechCellCloudUsers.mensagem(e)).setPositiveButton("OK",null).show();}
}
