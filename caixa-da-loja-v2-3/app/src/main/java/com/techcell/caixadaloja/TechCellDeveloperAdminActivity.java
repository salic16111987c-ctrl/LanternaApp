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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Central de administração REAL de uma empresa pela conta global do Desenvolvedor. */
public class TechCellDeveloperAdminActivity extends Activity {
    public static final String EXTRA_EMPRESA_UUID = "empresa_uuid";
    public static final String EXTRA_FILIAL_UUID = "filial_uuid";
    public static final String EXTRA_EMPRESA_NOME = "empresa_nome";
    public static final String EXTRA_OWNER_UID = "owner_uid";
    public static final String EXTRA_OWNER_EMAIL = "owner_email";

    private String empresaUuid = "";
    private String filialUuid = "";
    private String empresaNome = "";
    private String ownerUid = "";
    private String ownerEmail = "";
    private boolean ocupado;

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView txt(String s,int z,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button botao(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);
        empresaUuid=valor(EXTRA_EMPRESA_UUID);filialUuid=valor(EXTRA_FILIAL_UUID);empresaNome=valor(EXTRA_EMPRESA_NOME);ownerUid=valor(EXTRA_OWNER_UID);ownerEmail=valor(EXTRA_OWNER_EMAIL);
        if(empresaUuid.isEmpty()){new AlertDialog.Builder(this).setTitle("Empresa inválida").setMessage("Não foi possível identificar a empresa.").setPositiveButton("Voltar",(d,w)->finish()).setCancelable(false).show();return;}
        if(empresaNome.isEmpty())empresaNome="Empresa";
        render();
    }

    private String valor(String k){String v=getIntent().getStringExtra(k);return v==null?"":v.trim();}

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(32));scroll.addView(root);
        Button voltar=botao("←  Voltar ao Desenvolvedor");TechCellUi.styleSecondary(this,voltar);voltar.setOnClickListener(v->finish());root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView titulo=txt("Administrar empresa",27,true);titulo.setPadding(0,dp(16),0,dp(2));root.addView(titulo);
        TextView sub=txt(empresaNome+(ownerEmail.isEmpty()?"":"\nMaster: "+ownerEmail),13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout alerta=TechCellUi.card(this);alerta.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView at=txt("🛠  MODO ADMINISTRAÇÃO REAL",14,true);at.setTextColor(TechCellUi.RED);alerta.addView(at);
        TextView ad=txt("Tudo o que você alterar aqui é gravado de verdade na empresa. As ações do Desenvolvedor ficam registradas na auditoria.",12,true);ad.setTextColor(TechCellUi.RED);ad.setPadding(0,dp(6),0,0);alerta.addView(ad);root.addView(alerta);

        LinearLayout separacao=TechCellUi.card(this);separacao.setLayoutParams(TechCellUi.fullCardParams(this,7));
        TextView st=txt("Este NÃO é o modo de teste",12,true);st.setTextColor(TechCellUi.ORANGE);separacao.addView(st);
        TextView sd=txt("Para apenas reproduzir o que Master/Gerente/Caixa enxergam sem salvar alterações, volte e use 🧪 Testar como usuário.",11,false);sd.setTextColor(TechCellUi.MUTED);sd.setPadding(0,dp(4),0,0);separacao.addView(sd);root.addView(separacao);

        Button usuarios=botao("👥  Usuários, Master e permissões");TechCellUi.stylePrimary(this,usuarios,TechCellUi.BLUE);usuarios.setOnClickListener(v->carregarUsuarios());add(root,usuarios,14,54);
        Button dispositivos=botao("📱  Dispositivos e autorizações");TechCellUi.styleSecondary(this,dispositivos);dispositivos.setOnClickListener(v->carregarDispositivos());add(root,dispositivos,8,50);
        Button licenca=botao("💳  Licença, plano e acesso Master");TechCellUi.styleSecondary(this,licenca);licenca.setOnClickListener(v->carregarLicenca());add(root,licenca,8,50);
        Button auditoria=botao("🧾  Auditoria do Desenvolvedor");TechCellUi.styleSecondary(this,auditoria);auditoria.setOnClickListener(v->carregarAuditoria());add(root,auditoria,8,50);

        LinearLayout info=TechCellUi.card(this);info.setLayoutParams(TechCellUi.fullCardParams(this,12));
        info.addView(txt("Responsabilidades",13,true));
        TextView id=txt("O Master continua podendo administrar os usuários e permissões da própria empresa. O Desenvolvedor tem o mesmo controle de suporte em todas as empresas, além de licença, dispositivos e auditoria global.",11,false);id.setTextColor(TechCellUi.MUTED);id.setPadding(0,dp(5),0,0);info.addView(id);root.addView(info);
        setContentView(scroll);
    }

    private void add(LinearLayout root,Button b,int top,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(h));p.setMargins(0,dp(top),0,0);root.addView(b,p);}
    private LinearLayout form(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(4),dp(20),0);return f;}
    private EditText campo(String hint,boolean senha){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);if(senha)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}

    private void carregarUsuarios(){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando usuários…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{List<TechCellDeveloperAdmin.Usuario> us=TechCellDeveloperAdmin.listarUsuarios(getApplicationContext(),empresaUuid);runOnUiThread(()->{ocupado=false;mostrarUsuarios(us);});}
        catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar os usuários",e);});}},"TechCell-DevAdmin-Users").start();
    }

    private void mostrarUsuarios(List<TechCellDeveloperAdmin.Usuario> us){
        if(us==null||us.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Usuários • "+empresaNome).setMessage("Nenhum usuário encontrado.")
                    .setPositiveButton("＋ Cadastrar",(d,w)->novoUsuario()).setNegativeButton("Fechar",null).show();return;
        }
        String[] itens=new String[us.size()];
        for(int i=0;i<us.size();i++){TechCellDeveloperAdmin.Usuario u=us.get(i);String ic=u.proprietario?"👑 ":("GERENTE".equalsIgnoreCase(u.perfil)?"🧑‍💼 ":"💵 ");itens[i]=ic+u.nome+" • "+u.perfilExibicao()+"\n"+u.email+(u.ativo?"":"  [BLOQUEADO]");}
        new AlertDialog.Builder(this).setTitle("Usuários • "+empresaNome)
                .setMessage("Toque em uma conta para administrar. Alterações aqui são REAIS.")
                .setItems(itens,(d,w)->acoesUsuario(us.get(w)))
                .setPositiveButton("＋ Cadastrar novo",(d,w)->novoUsuario())
                .setNegativeButton("Fechar",null).show();
    }

    private void novoUsuario(){
        LinearLayout f=form();EditText nome=campo("Nome",false);f.addView(nome);EditText email=campo("E-mail",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);f.addView(email);EditText senha=campo("Senha inicial (mínimo 6 caracteres)",true);f.addView(senha);Spinner perfil=spinnerPerfis("CAIXA");f.addView(perfil);
        TextView d=txt("A nova conta será criada no Firebase e vinculada diretamente a esta empresa. A senha não fica visível para o Desenvolvedor depois.",11,false);d.setTextColor(TechCellUi.MUTED);f.addView(d);
        new AlertDialog.Builder(this).setTitle("Cadastrar usuário REAL").setView(f).setPositiveButton("Cadastrar",(x,w)->executar("Cadastrando usuário…",()->{TechCellDeveloperAdmin.criarUsuario(getApplicationContext(),empresaUuid,filialUuid,nome.getText().toString(),email.getText().toString(),senha.getText().toString(),perfilSelecionado(perfil));return "Usuário cadastrado e auditado.";},true)).setNegativeButton("Cancelar",null).show();
    }

    private void acoesUsuario(TechCellDeveloperAdmin.Usuario u){
        LinearLayout menu=form();TextView r=txt(u.email+"\nNível: "+u.perfilExibicao()+" • "+(u.ativo?"ATIVO":"BLOQUEADO"),12,false);r.setTextColor(TechCellUi.MUTED);r.setPadding(0,0,0,dp(10));menu.addView(r);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle((u.proprietario?"👑 ":"👤 ")+u.nome).setView(menu).setNegativeButton("Fechar",null).create();
        if(u.proprietario){
            Button editar=botao("✎  Editar nome do Master");TechCellUi.styleSecondary(this,editar);editar.setOnClickListener(v->{dialog.dismiss();editarMaster(u);});menu.addView(editar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
            Button reset=botao("✉  Enviar redefinição de senha");TechCellUi.styleSecondary(this,reset);reset.setOnClickListener(v->{dialog.dismiss();confirmarReset(u);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));p.setMargins(0,dp(8),0,0);menu.addView(reset,p);
            TextView d=txt("Para bloquear/liberar o Master, use Licença, plano e acesso Master. A senha atual nunca é exibida.",11,false);d.setTextColor(TechCellUi.MUTED);d.setPadding(0,dp(10),0,0);menu.addView(d);
        }else{
            Button perm=botao("🔐  Permissões deste usuário");TechCellUi.stylePrimary(this,perm,TechCellUi.BLUE);perm.setOnClickListener(v->{dialog.dismiss();mostrarPermissoes(u);});menu.addView(perm,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
            Button editar=botao("✎  Nome / nível / status");TechCellUi.styleSecondary(this,editar);editar.setOnClickListener(v->{dialog.dismiss();editarUsuario(u);});LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));p1.setMargins(0,dp(8),0,0);menu.addView(editar,p1);
            Button reset=botao("✉  Enviar redefinição de senha");TechCellUi.styleSecondary(this,reset);reset.setOnClickListener(v->{dialog.dismiss();confirmarReset(u);});LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));p2.setMargins(0,dp(8),0,0);menu.addView(reset,p2);
            Button excluir=botao("🗑  Remover usuário da empresa");TechCellUi.styleDanger(this,excluir);excluir.setOnClickListener(v->{dialog.dismiss();confirmarExcluir(u);});LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));p3.setMargins(0,dp(12),0,0);menu.addView(excluir,p3);
        }
        dialog.show();
    }

    private void editarMaster(TechCellDeveloperAdmin.Usuario u){
        LinearLayout f=form();EditText nome=campo("Nome do Master",false);nome.setText(u.nome);f.addView(nome);
        new AlertDialog.Builder(this).setTitle("Editar Master • REAL").setView(f).setPositiveButton("Salvar",(d,w)->executar("Salvando Master…",()->{TechCellDeveloperAdmin.editarUsuario(getApplicationContext(),empresaUuid,filialUuid,u,nome.getText().toString(),"MASTER",true);return "Master atualizado e auditado.";},true)).setNegativeButton("Cancelar",null).show();
    }

    private void editarUsuario(TechCellDeveloperAdmin.Usuario u){
        LinearLayout f=form();EditText nome=campo("Nome",false);nome.setText(u.nome);f.addView(nome);TextView login=txt("Login: "+u.email,11,false);login.setTextColor(TechCellUi.MUTED);f.addView(login);Spinner perfil=spinnerPerfis(u.perfil);f.addView(perfil);CheckBox ativo=new CheckBox(this);ativo.setText("Usuário ativo");ativo.setChecked(u.ativo);f.addView(ativo);
        new AlertDialog.Builder(this).setTitle("Editar usuário • REAL").setView(f).setPositiveButton("Salvar",(d,w)->executar("Salvando usuário…",()->{TechCellDeveloperAdmin.editarUsuario(getApplicationContext(),empresaUuid,filialUuid,u,nome.getText().toString(),perfilSelecionado(perfil),ativo.isChecked());return "Usuário atualizado e auditado.";},true)).setNegativeButton("Cancelar",null).show();
    }

    private void mostrarPermissoes(TechCellDeveloperAdmin.Usuario u){
        ScrollView sv=new ScrollView(this);LinearLayout box=form();sv.addView(box);TextView info=txt("Marque exatamente o que "+u.nome+" poderá acessar. Esta alteração vai para a empresa real.",12,false);info.setTextColor(TechCellUi.RED);box.addView(info);
        LinkedHashMap<String,String> op=new LinkedHashMap<>();
        op.put(TechCellPermissions.VENDER,"Abrir PDV / vender");op.put(TechCellPermissions.CLIENTES,"Clientes");op.put(TechCellPermissions.PRODUTOS,"Ver produtos");op.put(TechCellPermissions.PRODUTOS_EDITAR,"Cadastrar / editar produtos");op.put(TechCellPermissions.PRODUTOS_CUSTO,"Ver e alterar valor de entrada / custo");op.put(TechCellPermissions.ESTOQUE,"Controle de estoque");op.put(TechCellPermissions.RESUMO_DIA,"Ver resumo do dia");op.put(TechCellPermissions.LUCRO_CUSTO,"Ver lucro e custo");op.put(TechCellPermissions.FINANCEIRO,"Financeiro");op.put(TechCellPermissions.RELATORIOS,"Relatórios completos");op.put(TechCellPermissions.FORNECEDORES,"Fornecedores");op.put(TechCellPermissions.HISTORICO,"Histórico de vendas");op.put(TechCellPermissions.USUARIOS,"Administrar usuários (quando suportado pelo perfil)");
        LinkedHashMap<String,CheckBox> checks=new LinkedHashMap<>();for(Map.Entry<String,String> e:op.entrySet()){CheckBox c=new CheckBox(this);c.setText(e.getValue());c.setChecked(u.permissoes.contains(e.getKey()));box.addView(c);checks.put(e.getKey(),c);}
        new AlertDialog.Builder(this).setTitle("Permissões • "+u.nome).setView(sv).setPositiveButton("SALVAR REAL",(d,w)->{LinkedHashSet<String> novo=new LinkedHashSet<>();for(Map.Entry<String,CheckBox> e:checks.entrySet())if(e.getValue().isChecked())novo.add(e.getKey());executar("Salvando permissões…",()->{TechCellDeveloperAdmin.salvarPermissoes(getApplicationContext(),empresaUuid,u,novo);return "Permissões atualizadas e auditadas.";},true);}).setNeutralButton("Padrão do perfil",(d,w)->{Set<String> padrao=TechCellPermissions.padrao(u.perfil);executar("Aplicando padrão…",()->{TechCellDeveloperAdmin.salvarPermissoes(getApplicationContext(),empresaUuid,u,padrao);return "Padrão de "+u.perfilExibicao()+" aplicado e auditado.";},true);}).setNegativeButton("Cancelar",null).show();
    }

    private void confirmarReset(TechCellDeveloperAdmin.Usuario u){new AlertDialog.Builder(this).setTitle("Redefinir senha?").setMessage("Será enviado um e-mail de redefinição para:\n"+u.email+"\n\nA senha atual não será exibida ao Desenvolvedor.").setPositiveButton("Enviar",(d,w)->executar("Enviando redefinição…",()->{TechCellDeveloperAdmin.enviarRedefinicaoSenha(getApplicationContext(),empresaUuid,u);return "E-mail de redefinição enviado e ação auditada.";},false)).setNegativeButton("Cancelar",null).show();}
    private void confirmarExcluir(TechCellDeveloperAdmin.Usuario u){new AlertDialog.Builder(this).setTitle("Remover "+u.nome+"?").setMessage("A conta perderá o vínculo com esta empresa. A credencial Firebase não será apagada. Esta ação será auditada.").setPositiveButton("REMOVER",(d,w)->executar("Removendo usuário…",()->{TechCellDeveloperAdmin.excluirUsuario(getApplicationContext(),empresaUuid,u);return "Usuário removido da empresa e ação auditada.";},true)).setNegativeButton("Cancelar",null).show();}

    private Spinner spinnerPerfis(String atual){Spinner s=new Spinner(this);String[] itens={"CAIXA","GERENTE"};s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,itens));for(int i=0;i<itens.length;i++)if(itens[i].equalsIgnoreCase(atual)){s.setSelection(i);break;}return s;}
    private String perfilSelecionado(Spinner s){return String.valueOf(s.getSelectedItem());}

    private void carregarDispositivos(){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando dispositivos…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{List<TechCellDeveloperAdmin.Dispositivo> ds=TechCellDeveloperAdmin.listarDispositivos(getApplicationContext(),empresaUuid);runOnUiThread(()->{ocupado=false;mostrarDispositivos(ds);});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar os dispositivos",e);});}},"TechCell-DevAdmin-Devices").start();
    }

    private void mostrarDispositivos(List<TechCellDeveloperAdmin.Dispositivo> ds){
        ScrollView sv=new ScrollView(this);LinearLayout box=form();sv.addView(box);
        if(ds==null||ds.isEmpty()){TextView v=txt("Nenhum dispositivo solicitado para esta empresa.",12,false);v.setTextColor(TechCellUi.MUTED);box.addView(v);}else for(TechCellDeveloperAdmin.Dispositivo d:ds){
            LinearLayout c=TechCellUi.card(this);c.setLayoutParams(TechCellUi.fullCardParams(this,8));c.addView(txt("📱 "+(d.nomeAparelho.isEmpty()?d.deviceUuid:d.nomeAparelho),14,true));TextView x=txt((d.nomeUsuario.isEmpty()?d.email:d.nomeUsuario)+"\nPerfil: "+(d.perfil.isEmpty()?"CAIXA":d.perfil)+" • Status: "+d.status+(d.licenseSlotId.isEmpty()?"":"\nVaga: "+d.licenseSlotId),11,false);x.setTextColor(TechCellUi.MUTED);x.setPadding(0,dp(4),0,dp(6));c.addView(x);
            if(TechCellDeviceAuthorization.PENDENTE.equalsIgnoreCase(d.status)){Button a=botao("✓ Autorizar");TechCellUi.stylePrimary(this,a,TechCellUi.GREEN);a.setOnClickListener(v->confirmarAcaoDevice(d,"AUTORIZAR"));c.addView(a,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));Button n=botao("✕ Negar");TechCellUi.styleDanger(this,n);n.setOnClickListener(v->confirmarAcaoDevice(d,"NEGAR"));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44));np.setMargins(0,dp(6),0,0);c.addView(n,np);}else if(TechCellDeviceAuthorization.AUTORIZADO.equalsIgnoreCase(d.status)){Button b=botao("⛔ Bloquear e liberar vaga");TechCellUi.styleDanger(this,b);b.setOnClickListener(v->confirmarAcaoDevice(d,"BLOQUEAR"));c.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));}else{Button a=botao("✓ Reautorizar");TechCellUi.stylePrimary(this,a,TechCellUi.GREEN);a.setOnClickListener(v->confirmarAcaoDevice(d,"AUTORIZAR"));c.addView(a,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));}
            box.addView(c);
        }
        new AlertDialog.Builder(this).setTitle("Dispositivos • "+empresaNome).setView(sv).setPositiveButton("Atualizar",(d,w)->carregarDispositivos()).setNegativeButton("Fechar",null).show();
    }

    private void confirmarAcaoDevice(TechCellDeveloperAdmin.Dispositivo d,String acao){String msg="Aparelho: "+d.nomeAparelho+"\nUsuário: "+d.email+"\n\nEsta alteração é REAL e será auditada.";new AlertDialog.Builder(this).setTitle(acao+" dispositivo?").setMessage(msg).setPositiveButton(acao,(x,w)->executar(acao+" dispositivo…",()->{if("AUTORIZAR".equals(acao))TechCellDeveloperAdmin.autorizarDispositivo(getApplicationContext(),empresaUuid,d);else if("NEGAR".equals(acao))TechCellDeveloperAdmin.negarDispositivo(getApplicationContext(),empresaUuid,d);else TechCellDeveloperAdmin.bloquearDispositivo(getApplicationContext(),empresaUuid,d);return "Dispositivo atualizado e ação auditada.";},false)).setNegativeButton("Cancelar",null).show();}

    private void carregarLicenca(){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando licença…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{TechCellLicenseManager.Licenca l=TechCellLicenseManager.lerServidor(getApplicationContext(),empresaUuid);int usados=l.existe?TechCellLicenseManager.contarSlotsEmUso(getApplicationContext(),empresaUuid):0;runOnUiThread(()->{ocupado=false;mostrarLicenca(l,usados);});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar a licença",e);});}},"TechCell-DevAdmin-License").start();
    }

    private void mostrarLicenca(TechCellLicenseManager.Licenca l,int usados){
        ScrollView sv=new ScrollView(this);LinearLayout f=form();sv.addView(f);TextView r=txt("Caixas em uso agora: "+usados+(l.existe?" / "+l.caixasContratados:" • empresa legado"),12,true);r.setPadding(0,0,0,dp(8));f.addView(r);
        TextView ls=txt("Status da licença",11,true);f.addView(ls);Spinner status=new Spinner(this);String[] sts={"PENDENTE","ATIVA","SUSPENSA","CANCELADA"};status.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sts));f.addView(status);selecionar(status,sts,l.existe?l.status:"ATIVA");
        TextView lp=txt("Plano",11,true);lp.setPadding(0,dp(8),0,0);f.addView(lp);Spinner plano=new Spinner(this);String[] ps={"LOCAL","HIBRIDO","NUVEM"};plano.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ps));f.addView(plano);selecionar(plano,ps,l.existe?l.plano:"HIBRIDO");
        CheckBox master=new CheckBox(this);master.setText("Master liberado");master.setChecked(!l.existe||l.masterAtivo);f.addView(master);EditText caixas=new EditText(this);caixas.setHint("Quantidade de licenças Caixa");caixas.setInputType(InputType.TYPE_CLASS_NUMBER);caixas.setText(String.valueOf(l.existe?l.caixasContratados:Math.max(1,usados)));f.addView(caixas);
        TextView d=txt("Bloquear Master aqui impede o acesso comercial do Master sem apagar a conta. Reduzir Caixas exige vagas livres.",11,false);d.setTextColor(TechCellUi.MUTED);f.addView(d);
        new AlertDialog.Builder(this).setTitle("Licença • "+empresaNome).setView(sv).setPositiveButton("SALVAR REAL",(x,w)->{int qtd;try{qtd=Integer.parseInt(caixas.getText().toString().trim());}catch(Throwable z){qtd=0;}final int q=qtd;String st=String.valueOf(status.getSelectedItem()),pl=String.valueOf(plano.getSelectedItem());boolean ma=master.isChecked();executar("Salvando licença…",()->{TechCellDeveloper.Empresa e=new TechCellDeveloper.Empresa();e.empresaUuid=empresaUuid;e.filialUuid=filialUuid;e.nome=empresaNome;e.ownerUid=ownerUid;e.ownerEmail=ownerEmail;e.licenca=l;e.caixasEmUso=usados;TechCellDeveloper.salvarLicenca(getApplicationContext(),e,st,pl,ma,q);TechCellDeveloperAdmin.registrarAuditoria(getApplicationContext(),empresaUuid,"LICENCA_ALTERADA","LICENCA",empresaUuid,"Status: "+st+" • Plano: "+pl+" • Master: "+(ma?"LIBERADO":"BLOQUEADO")+" • Caixas: "+q);return "Licença atualizada e auditada.";},false);}).setNegativeButton("Cancelar",null).show();
    }

    private void selecionar(Spinner s,String[] itens,String valor){for(int i=0;i<itens.length;i++)if(itens[i].equalsIgnoreCase(valor)){s.setSelection(i);return;}}

    private void carregarAuditoria(){
        if(ocupado)return;ocupado=true;Toast.makeText(this,"Carregando auditoria…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{List<TechCellDeveloperAdmin.EventoAuditoria> es=TechCellDeveloperAdmin.listarAuditoria(getApplicationContext(),empresaUuid);runOnUiThread(()->{ocupado=false;mostrarAuditoria(es);});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível carregar a auditoria",e);});}},"TechCell-DevAdmin-Audit").start();
    }

    private void mostrarAuditoria(List<TechCellDeveloperAdmin.EventoAuditoria> es){
        ScrollView sv=new ScrollView(this);LinearLayout box=form();sv.addView(box);SimpleDateFormat fmt=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","BR"));
        if(es==null||es.isEmpty()){TextView v=txt("Ainda não há ações do Desenvolvedor registradas para esta empresa.",12,false);v.setTextColor(TechCellUi.MUTED);box.addView(v);}else for(TechCellDeveloperAdmin.EventoAuditoria e:es){LinearLayout c=TechCellUi.card(this);c.setLayoutParams(TechCellUi.fullCardParams(this,8));c.addView(txt(e.acao.replace('_',' '),13,true));String quando=e.criadoEm>0?fmt.format(new Date(e.criadoEm)):"Aguardando horário do servidor";TextView d=txt(quando+"\nPor: "+(e.atorNome.isEmpty()?e.atorEmail:e.atorNome)+"\n"+e.descricao,11,false);d.setTextColor(TechCellUi.MUTED);d.setPadding(0,dp(4),0,0);c.addView(d);box.addView(c);}
        new AlertDialog.Builder(this).setTitle("Auditoria • "+empresaNome).setView(sv).setPositiveButton("Atualizar",(d,w)->carregarAuditoria()).setNegativeButton("Fechar",null).show();
    }

    private interface Acao {String executar() throws Exception;}
    private void executar(String progresso,Acao acao,boolean recarregarUsuarios){if(ocupado)return;ocupado=true;Toast.makeText(this,progresso,Toast.LENGTH_SHORT).show();new Thread(()->{try{String msg=acao.executar();runOnUiThread(()->{ocupado=false;Toast.makeText(this,msg,Toast.LENGTH_LONG).show();if(recarregarUsuarios)carregarUsuarios();});}catch(Throwable e){runOnUiThread(()->{ocupado=false;erro("Não foi possível concluir",e);});}},"TechCell-DevAdmin-Action").start();}
    private void erro(String titulo,Throwable e){new AlertDialog.Builder(this).setTitle(titulo).setMessage(TechCellCloudUsers.mensagem(e)).setPositiveButton("OK",null).show();}
}
