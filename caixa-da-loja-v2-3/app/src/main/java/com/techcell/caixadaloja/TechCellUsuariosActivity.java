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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gestão de contas, perfis e permissões, exclusiva do Master proprietário. */
public class TechCellUsuariosActivity extends Activity {
    private boolean ocupado;
    private boolean carregando;
    private LinearLayout lista;
    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);
        if(!TechCellAccess.podeAdministrar(this)){
            new AlertDialog.Builder(this).setTitle("Acesso restrito").setMessage("Somente o Master pode administrar usuários.")
                    .setPositiveButton("Voltar",(d,w)->finish()).setCancelable(false).show();return;
        }
        render();carregar();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button voltar=botao("←  Voltar");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        TextView titulo=txt("Usuários / Acessos",26,true);titulo.setPadding(0,dp(16),0,dp(2));root.addView(titulo);
        TextView sub=txt("Perfil + permissões personalizadas",13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout regras=TechCellUi.card(this);regras.setLayoutParams(TechCellUi.fullCardParams(this,12));
        regras.addView(txt("Como funciona",14,true));
        TextView r=txt("O perfil define um modelo inicial. Depois, o Master pode liberar ou retirar cada ferramenta do usuário. O Master proprietário continua com acesso total.",12,false);r.setTextColor(TechCellUi.MUTED);r.setPadding(0,dp(5),0,0);regras.addView(r);root.addView(regras);

        Button novo=botao("＋  Cadastrar novo usuário");TechCellUi.stylePrimary(this,novo,TechCellUi.GREEN);novo.setOnClickListener(v->novoUsuario());
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));np.setMargins(0,dp(4),0,dp(8));root.addView(novo,np);
        Button vincular=botao("🔗  Vincular conta já existente");TechCellUi.styleSecondary(this,vincular);vincular.setOnClickListener(v->vincularUsuario());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));vp.setMargins(0,0,0,dp(10));root.addView(vincular,vp);
        Button atualizar=botao("↻  Atualizar lista");TechCellUi.styleSecondary(this,atualizar);atualizar.setOnClickListener(v->carregar());root.addView(atualizar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));
        lista=new LinearLayout(this);lista.setOrientation(LinearLayout.VERTICAL);root.addView(lista);setContentView(scroll);
    }

    private void carregar(){
        if(carregando||lista==null)return;carregando=true;lista.removeAllViews();
        TextView t=txt("Carregando contas…",13,true);t.setPadding(0,dp(16),0,0);lista.addView(t);
        new Thread(()->{try{List<TechCellCloudUsers.Usuario> us=TechCellCloudUsers.listar(getApplicationContext());runOnUiThread(()->{carregando=false;mostrarLista(us);});}
        catch(Throwable e){runOnUiThread(()->{carregando=false;lista.removeAllViews();erro("Não foi possível carregar os usuários",e);});}},"TechCell-Users-List").start();
    }

    private void mostrarLista(List<TechCellCloudUsers.Usuario> us){
        lista.removeAllViews();
        if(us==null||us.isEmpty()){TextView v=txt("Nenhuma conta encontrada.",13,false);v.setPadding(0,dp(16),0,0);lista.addView(v);return;}
        for(TechCellCloudUsers.Usuario u:us){
            LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,9));
            String status=u.ativo?"ATIVO":"BLOQUEADO";
            card.addView(txt((u.proprietario?"👑  ":"👤  ")+u.nome,16,true));
            TextView d=txt(u.email+"\nNível: "+u.perfilExibicao()+"  •  "+status,12,false);d.setTextColor(u.ativo?TechCellUi.NAVY:TechCellUi.RED);d.setPadding(0,dp(4),0,dp(8));card.addView(d);
            Button acao=botao(u.proprietario?"Gerenciar conta Master":"Gerenciar usuário e permissões");TechCellUi.styleSecondary(this,acao);acao.setOnClickListener(v->acoes(u));card.addView(acao,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));
            lista.addView(card);
        }
    }

    private void novoUsuario(){
        LinearLayout form=form();EditText nome=campo("Nome",false);form.addView(nome);EditText email=campo("E-mail",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);form.addView(email);
        EditText senha=campo("Senha inicial (mínimo 6 caracteres)",true);form.addView(senha);Spinner perfil=spinnerPerfis("CAIXA");form.addView(perfil);
        TextView dica=txt("Após criar, abra Gerenciar usuário e permissões para personalizar as ferramentas.",11,false);dica.setTextColor(TechCellUi.MUTED);form.addView(dica);
        new AlertDialog.Builder(this).setTitle("Cadastrar novo usuário").setView(form).setPositiveButton("Cadastrar",(d,w)->criar(nome.getText().toString(),email.getText().toString(),senha.getText().toString(),perfilSelecionado(perfil))).setNegativeButton("Cancelar",null).show();
    }

    private void vincularUsuario(){
        LinearLayout form=form();TextView info=txt("A senha atual é usada apenas para confirmar a conta e não é salva.",11,false);info.setTextColor(TechCellUi.MUTED);form.addView(info);
        EditText nome=campo("Nome",false);form.addView(nome);EditText email=campo("E-mail da conta existente",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);form.addView(email);
        EditText senha=campo("Senha atual da conta",true);form.addView(senha);Spinner perfil=spinnerPerfis("CAIXA");form.addView(perfil);
        new AlertDialog.Builder(this).setTitle("Vincular conta existente").setView(form).setPositiveButton("Vincular",(d,w)->vincular(nome.getText().toString(),email.getText().toString(),senha.getText().toString(),perfilSelecionado(perfil))).setNegativeButton("Cancelar",null).show();
    }

    private LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(4),dp(20),0);return f;}
    private EditText campo(String hint,boolean senha){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);if(senha)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}

    private void criar(String nome,String email,String senha,String perfil){
        executarSilencioso("Não foi possível cadastrar",()->TechCellCloudUsers.criar(getApplicationContext(),nome,email,senha,perfil),u->{Toast.makeText(this,"Usuário criado: "+u.perfilExibicao(),Toast.LENGTH_LONG).show();carregar();});
    }
    private void vincular(String nome,String email,String senha,String perfil){
        executarSilencioso("Não foi possível vincular a conta",()->TechCellCloudUsers.vincularExistente(getApplicationContext(),nome,email,senha,perfil),u->{new AlertDialog.Builder(this).setTitle("Conta vinculada ✓").setMessage(u.email+" agora está vinculada a esta loja como "+u.perfilExibicao()+".").setPositiveButton("OK",(d,w)->carregar()).show();});
    }

    private void acoes(TechCellCloudUsers.Usuario u){
        LinearLayout menu=form();String status=u.ativo?"ATIVO":"BLOQUEADO";TextView resumo=txt(u.email+"\nNível: "+u.perfilExibicao()+"  •  "+status,12,false);resumo.setTextColor(TechCellUi.MUTED);resumo.setPadding(0,0,0,dp(10));menu.addView(resumo);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(u.nome).setView(menu).setNegativeButton("Fechar",null).create();
        if(u.proprietario){
            Button editar=botao("✎  Editar nome do Master");TechCellUi.styleSecondary(this,editar);editar.setOnClickListener(v->{dialog.dismiss();editarMaster(u);});menu.addView(editar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
            Button senha=botao("✉  Enviar redefinição de senha");TechCellUi.styleSecondary(this,senha);senha.setOnClickListener(v->{dialog.dismiss();reset(u);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));sp.setMargins(0,dp(8),0,0);menu.addView(senha,sp);
        }else{
            Button perm=botao("🔐  Permissões deste usuário");TechCellUi.stylePrimary(this,perm,TechCellUi.BLUE);perm.setOnClickListener(v->{dialog.dismiss();abrirPermissoes(u);});menu.addView(perm,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
            Button editar=botao("✎  Nome / nível / status");TechCellUi.styleSecondary(this,editar);editar.setOnClickListener(v->{dialog.dismiss();editarUsuario(u);});LinearLayout.LayoutParams ep0=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));ep0.setMargins(0,dp(8),0,0);menu.addView(editar,ep0);
            Button bloquear=botao(u.ativo?"⛔  Bloquear usuário":"✓  Reativar usuário");TechCellUi.styleSecondary(this,bloquear);bloquear.setOnClickListener(v->{dialog.dismiss();alterarAtivo(u,!u.ativo);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));bp.setMargins(0,dp(8),0,0);menu.addView(bloquear,bp);
            Button senha=botao("✉  Enviar redefinição de senha");TechCellUi.styleSecondary(this,senha);senha.setOnClickListener(v->{dialog.dismiss();reset(u);});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));sp.setMargins(0,dp(8),0,0);menu.addView(senha,sp);
            Button excluir=botao("🗑  Excluir usuário da loja");TechCellUi.stylePrimary(this,excluir,TechCellUi.RED);excluir.setOnClickListener(v->{dialog.dismiss();confirmarExcluir(u);});LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));ep.setMargins(0,dp(12),0,0);menu.addView(excluir,ep);
        }
        dialog.show();
    }

    private void abrirPermissoes(TechCellCloudUsers.Usuario u){
        if(ocupado)return;ocupado=true;
        new Thread(()->{try{Set<String> atuais=TechCellPermissionAdmin.carregar(getApplicationContext(),u.uid,u.perfil);runOnUiThread(()->{ocupado=false;mostrarPermissoes(u,atuais);});}
        catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar as permissões",e);});}},"TechCell-Permissions-Load").start();
    }

    private void mostrarPermissoes(TechCellCloudUsers.Usuario u,Set<String> atuais){
        ScrollView sv=new ScrollView(this);LinearLayout box=form();sv.addView(box);
        TextView info=txt("Marque exatamente o que "+u.nome+" poderá usar. Alterações chegam aos aparelhos pela nuvem.",12,false);info.setTextColor(TechCellUi.MUTED);box.addView(info);
        LinkedHashMap<String,String> op=new LinkedHashMap<>();
        op.put(TechCellPermissions.VENDER,"Abrir PDV / vender");op.put(TechCellPermissions.CLIENTES,"Clientes");op.put(TechCellPermissions.PRODUTOS,"Ver produtos");op.put(TechCellPermissions.PRODUTOS_EDITAR,"Cadastrar / editar produtos");op.put(TechCellPermissions.PRODUTOS_CUSTO,"Ver e alterar valor de entrada / custo");op.put(TechCellPermissions.ESTOQUE,"Controle de estoque");op.put(TechCellPermissions.RESUMO_DIA,"Ver resumo do dia (vendas + total)");op.put(TechCellPermissions.LUCRO_CUSTO,"Ver lucro e custo no painel");op.put(TechCellPermissions.FINANCEIRO,"Financeiro");op.put(TechCellPermissions.RELATORIOS,"Relatórios completos");op.put(TechCellPermissions.FORNECEDORES,"Fornecedores");op.put(TechCellPermissions.HISTORICO,"Histórico de vendas");
        LinkedHashMap<String,CheckBox> checks=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:op.entrySet()){CheckBox c=new CheckBox(this);c.setText(e.getValue());c.setChecked(atuais.contains(e.getKey()));box.addView(c);checks.put(e.getKey(),c);}
        new AlertDialog.Builder(this).setTitle("Permissões • "+u.nome).setView(sv).setPositiveButton("Salvar",(d,w)->{
            LinkedHashSet<String> novo=new LinkedHashSet<>();for(Map.Entry<String,CheckBox> e:checks.entrySet())if(e.getValue().isChecked())novo.add(e.getKey());
            executar("Permissões atualizadas",()->{TechCellPermissionAdmin.salvar(getApplicationContext(),u.uid,u.perfil,novo);return "Acesso de "+u.nome+":\n"+TechCellPermissions.resumo(novo);});
        }).setNeutralButton("Usar padrão do perfil",(d,w)->{Set<String> padrao=TechCellPermissions.padrao(u.perfil);executar("Permissões atualizadas",()->{TechCellPermissionAdmin.salvar(getApplicationContext(),u.uid,u.perfil,padrao);return "Aplicado o modelo padrão de "+u.perfilExibicao()+".";});}).setNegativeButton("Cancelar",null).show();
    }

    private void editarMaster(TechCellCloudUsers.Usuario u){LinearLayout form=form();EditText nome=campo("Nome do Master",false);nome.setText(u.nome);form.addView(nome);new AlertDialog.Builder(this).setTitle("Editar conta Master").setView(form).setPositiveButton("Salvar",(d,w)->executar("Master atualizado",()->{String n=nome.getText().toString();TechCellCloudUsers.editarNomeProprietario(getApplicationContext(),n);u.nome=n.trim();return "Nome: "+u.nome+"\nAcesso: total";})).setNegativeButton("Cancelar",null).show();}
    private void editarUsuario(TechCellCloudUsers.Usuario u){LinearLayout form=form();EditText nome=campo("Nome",false);nome.setText(u.nome);form.addView(nome);TextView email=txt("Login: "+u.email,11,false);email.setTextColor(TechCellUi.MUTED);form.addView(email);Spinner perfil=spinnerPerfis(u.perfil);form.addView(perfil);CheckBox ativo=new CheckBox(this);ativo.setText("Usuário ativo");ativo.setChecked(u.ativo);form.addView(ativo);new AlertDialog.Builder(this).setTitle("Editar "+u.nome).setView(form).setPositiveButton("Salvar",(d,w)->executar("Usuário atualizado",()->{TechCellCloudUsers.editar(getApplicationContext(),u,nome.getText().toString(),perfilSelecionado(perfil),ativo.isChecked());return "Nome: "+u.nome+"\nNível: "+u.perfilExibicao()+"\nStatus: "+(u.ativo?"ATIVO":"BLOQUEADO");})).setNegativeButton("Cancelar",null).show();}

    private void confirmarExcluir(TechCellCloudUsers.Usuario u){new AlertDialog.Builder(this).setTitle("Excluir "+u.nome+"?").setMessage("Este usuário perderá o acesso a esta loja. A credencial de login do Firebase será preservada.").setPositiveButton("Excluir",(d,w)->excluirUsuario(u)).setNegativeButton("Cancelar",null).show();}
    private void excluirUsuario(TechCellCloudUsers.Usuario u){executar("Usuário excluído",()->{TechCellCloudUsers.excluirDaEmpresa(getApplicationContext(),u);return u.nome+" foi removido desta empresa.";});}
    private void alterarAtivo(TechCellCloudUsers.Usuario u,boolean ativo){executar(ativo?"Usuário reativado":"Usuário bloqueado",()->{TechCellCloudUsers.alterarAtivo(getApplicationContext(),u,ativo);return ativo?"A conta voltou a ter acesso.":"A conta foi bloqueada para esta empresa.";});}
    private void reset(TechCellCloudUsers.Usuario u){executar("Redefinição solicitada",()->{TechCellCloudUsers.enviarRedefinicaoSenha(getApplicationContext(),u.email);return "E-mail de redefinição enviado para "+u.email;});}

    private Spinner spinnerPerfis(String atual){Spinner s=new Spinner(this);String[] itens={"Caixa","Gerente","Master"};s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,itens));String a=atual==null?"":atual.toUpperCase();if(a.contains("MASTER")||a.contains("ADMIN"))s.setSelection(2);else if(a.contains("GERENTE"))s.setSelection(1);else s.setSelection(0);return s;}
    private String perfilSelecionado(Spinner s){int p=s.getSelectedItemPosition();return p==2?"MASTER":(p==1?"GERENTE":"CAIXA");}

    private interface Op{String rodar()throws Exception;}
    private void executar(String titulo,Op op){if(ocupado){Toast.makeText(this,"Aguarde a operação atual terminar.",Toast.LENGTH_SHORT).show();return;}ocupado=true;new Thread(()->{try{String m=op.rodar();runOnUiThread(()->{ocupado=false;new AlertDialog.Builder(this).setTitle(titulo+" ✓").setMessage(m).setPositiveButton("OK",(d,w)->carregar()).show();});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro(titulo,e);});}},"TechCell-Users-Action").start();}
    private interface Carrega<T>{T rodar()throws Exception;} private interface Recebe<T>{void ok(T v);}
    private <T> void executarSilencioso(String titulo,Carrega<T> op,Recebe<T> recebe){if(ocupado){Toast.makeText(this,"Aguarde a operação atual terminar.",Toast.LENGTH_SHORT).show();return;}ocupado=true;new Thread(()->{try{T r=op.rodar();runOnUiThread(()->{ocupado=false;recebe.ok(r);});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro(titulo,e);});}},"TechCell-Users-Action").start();}
    private void erro(String titulo,Throwable e){new AlertDialog.Builder(this).setTitle(titulo).setMessage(TechCellCloudUsers.mensagem(e)).setPositiveButton("OK",null).show();}
}
