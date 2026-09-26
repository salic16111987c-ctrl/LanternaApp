package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

public class ConfiguracaoDispositivoActivity extends Activity {
    private GestaoDbHelper db;
    private GestaoDbHelper.SyncContext atual;
    private EditText nome;
    private EditText masterHost;
    private EditText masterPort;
    private RadioGroup papeis;
    private LinearLayout blocoMasterRemoto;
    private TextView dicaRede;
    private TextView resultadoRede;
    private Button buscarMaster;
    private Button testarConexao;

    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView text(String v,int s,boolean b){
        TextView t=new TextView(this);
        t.setText(v);t.setTextSize(s);t.setTextColor(TechCellUi.TEXT);
        if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);
        return t;
    }
    private String curto(String id){
        if(id==null||id.trim().isEmpty())return "—";
        String x=id.replace("-","");
        return x.substring(0,Math.min(8,x.length())).toUpperCase(Locale.ROOT);
    }
    private EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);e.setSingleLine(true);e.setTextSize(15);
        TechCellUi.styleSearch(this,e);
        return e;
    }
    private RadioButton papel(String titulo,String valor){
        RadioButton r=new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(titulo);r.setTag(valor);r.setTextSize(15);
        r.setTextColor(TechCellUi.TEXT);r.setPadding(0,dp(6),0,dp(6));
        return r;
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        TechCellUi.applyWindowChrome(this);
        db=new GestaoDbHelper(this);
        atual=db.getSyncContext();
        if(atual.configurado && "MASTER".equalsIgnoreCase(atual.papelDispositivo)) iniciarMaster();
        render();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(30));
        scroll.addView(root);

        Button voltar=new Button(this);
        voltar.setText("←  Voltar");TechCellUi.styleSecondary(this,voltar);
        voltar.setOnClickListener(v->finish());
        root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView titulo=text("Dispositivo e rede",27,true);
        titulo.setPadding(0,dp(16),0,0);root.addView(titulo);
        TextView sub=text("Descoberta e conexão local pelo roteador • Alpha 32",13,false);
        sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        LinearLayout identidade=TechCellUi.card(this);
        identidade.setLayoutParams(TechCellUi.fullCardParams(this,16));
        identidade.addView(text("Identidade desta instalação",15,true));
        TextView ids=text(
                "Empresa  "+curto(atual.empresaUuid)+"\n"+
                "Filial       "+curto(atual.filialUuid)+"\n"+
                "Dispositivo  "+curto(atual.dispositivoUuid),
                13,false);
        ids.setTextColor(TechCellUi.MUTED);ids.setPadding(0,dp(8),0,0);
        identidade.addView(ids);root.addView(identidade);

        TextView nomeLabel=text("Nome deste aparelho",14,true);
        nomeLabel.setPadding(0,dp(18),0,dp(7));root.addView(nomeLabel);
        nome=input("Ex.: Caixa 1, Celular Master, Balcão");
        String nomeAtual=atual.nomeDispositivo==null?"":atual.nomeDispositivo.trim();
        if(!atual.configurado && (nomeAtual.isEmpty()||"Este aparelho".equalsIgnoreCase(nomeAtual))){
            nome.setText((Build.MANUFACTURER+" "+Build.MODEL).trim());
        }else nome.setText(nomeAtual);
        root.addView(nome,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        TextView funcao=text("Função deste aparelho",14,true);
        funcao.setPadding(0,dp(18),0,dp(4));root.addView(funcao);
        TextView explicacao=text("O Master concentra os dados. Os demais aparelhos localizam o Master automaticamente dentro da mesma rede Wi-Fi/LAN.",12,false);
        explicacao.setTextColor(TechCellUi.MUTED);root.addView(explicacao);

        papeis=new RadioGroup(this);papeis.setOrientation(RadioGroup.VERTICAL);
        papeis.setPadding(dp(6),dp(6),dp(6),dp(4));
        RadioButton master=papel("Master — servidor principal desta loja","MASTER");
        RadioButton caixa=papel("Caixa — vendas e operação do PDV","CAIXA");
        RadioButton admin=papel("Administrador — gestão e relatórios","ADMIN");
        RadioButton consulta=papel("Consulta — acesso sem operação de caixa","CONSULTA");
        papeis.addView(master);papeis.addView(caixa);papeis.addView(admin);papeis.addView(consulta);
        root.addView(papeis,TechCellUi.fullCardParams(this,8));

        String papelAtual=atual.papelDispositivo==null?"":atual.papelDispositivo;
        for(int i=0;i<papeis.getChildCount();i++){
            RadioButton r=(RadioButton)papeis.getChildAt(i);
            if(papelAtual.equalsIgnoreCase(String.valueOf(r.getTag())))r.setChecked(true);
        }

        blocoMasterRemoto=TechCellUi.card(this);
        blocoMasterRemoto.setLayoutParams(TechCellUi.fullCardParams(this,12));
        blocoMasterRemoto.addView(text("Conexão com o Master",15,true));

        buscarMaster=new Button(this);
        buscarMaster.setText("🔎  Procurar Master na rede");
        buscarMaster.setTextSize(15);TechCellUi.stylePrimary(this,buscarMaster);
        buscarMaster.setOnClickListener(v->procurarMaster());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));
        bp.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(buscarMaster,bp);

        TextView h=text("Endereço do Master",12,true);h.setTextColor(TechCellUi.MUTED);
        h.setPadding(0,dp(12),0,dp(5));blocoMasterRemoto.addView(h);
        masterHost=input("Será preenchido automaticamente");
        masterHost.setText(atual.masterHost==null?"":atual.masterHost);
        blocoMasterRemoto.addView(masterHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        TextView p=text("Porta local",12,true);p.setTextColor(TechCellUi.MUTED);
        p.setPadding(0,dp(10),0,dp(5));blocoMasterRemoto.addView(p);
        masterPort=input("8765");masterPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        masterPort.setText(String.valueOf(atual.masterPort>0?atual.masterPort:8765));
        blocoMasterRemoto.addView(masterPort,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        testarConexao=new Button(this);
        testarConexao.setText("Testar conexão com o Master");
        TechCellUi.styleSecondary(this,testarConexao);
        testarConexao.setOnClickListener(v->testarMasterSalvo());
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        tp.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(testarConexao,tp);

        if(atual.masterName!=null&&!atual.masterName.trim().isEmpty()){
            TextView vinc=text("Vinculado a: "+atual.masterName+" • "+atual.masterHost,12,true);
            vinc.setTextColor(TechCellUi.GREEN);vinc.setPadding(0,dp(10),0,0);
            blocoMasterRemoto.addView(vinc);
        }
        root.addView(blocoMasterRemoto);

        dicaRede=text("",13,true);dicaRede.setPadding(dp(12),dp(11),dp(12),dp(11));
        dicaRede.setBackground(TechCellUi.pillBackground(this));
        root.addView(dicaRede,TechCellUi.fullCardParams(this,12));

        resultadoRede=text("Rede local: aguardando teste.",12,true);
        resultadoRede.setTextColor(TechCellUi.MUTED);
        resultadoRede.setPadding(dp(12),dp(10),dp(12),dp(10));
        resultadoRede.setBackground(TechCellUi.cardBackground(this));
        root.addView(resultadoRede,TechCellUi.fullCardParams(this,8));

        papeis.setOnCheckedChangeListener((group,checkedId)->atualizarTipo());
        atualizarTipo();

        Button salvar=new Button(this);salvar.setText("Salvar configuração");
        salvar.setTextSize(16);TechCellUi.stylePrimary(this,salvar,TechCellUi.GREEN);
        salvar.setOnClickListener(v->salvar(true));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));
        sp.setMargins(0,dp(16),0,0);root.addView(salvar,sp);

        TextView aviso=text("Alpha 32 testa descoberta, pareamento e comunicação local. Produtos e vendas ainda não são transferidos entre os aparelhos.",11,false);
        aviso.setTextColor(TechCellUi.MUTED);aviso.setGravity(Gravity.CENTER);
        aviso.setPadding(dp(8),dp(12),dp(8),0);root.addView(aviso);

        setContentView(scroll);
    }

    private String papelSelecionado(){
        int id=papeis.getCheckedRadioButtonId();
        if(id==-1)return "";
        RadioButton r=findViewById(id);
        return r==null?"":String.valueOf(r.getTag());
    }

    private void atualizarTipo(){
        boolean ehMaster="MASTER".equals(papelSelecionado());
        blocoMasterRemoto.setVisibility(ehMaster?View.GONE:View.VISIBLE);
        if(ehMaster){
            String ip=ipv4Local();
            dicaRede.setText(
                    "Modo Master Android\n"+
                    "Servidor local na porta "+portaDigitada()+".\n"+
                    "IP atual: "+(ip.isEmpty()?"não identificado":ip)+
                    "\nMantenha todos os aparelhos no mesmo roteador.");
        }else{
            dicaRede.setText(
                    "Modo terminal\n"+
                    "Use “Procurar Master na rede”. Internet não é necessária; apenas a rede local do roteador.");
        }
    }

    private int portaDigitada(){
        try{
            String p=masterPort==null?"":masterPort.getText().toString().trim();
            int v=p.isEmpty()?8765:Integer.parseInt(p);
            return v>0&&v<=65535?v:8765;
        }catch(Throwable e){return 8765;}
    }

    private boolean salvar(boolean fechar){
        try{
            String papel=papelSelecionado();
            if(papel.isEmpty())throw new IllegalArgumentException("Selecione a função deste aparelho.");
            int porta=portaDigitada();
            String host="MASTER".equals(papel)?"":masterHost.getText().toString().trim();
            db.salvarConfiguracaoDispositivo(nome.getText().toString(),papel,host,porta);
            atual=db.getSyncContext();
            if("MASTER".equals(papel)){
                reiniciarMaster();
                resultadoRede.setText("Master local iniciado. Outro Android já pode procurar este aparelho na mesma rede.");
                resultadoRede.setTextColor(TechCellUi.GREEN);
            }else{
                pararMaster();
            }
            Toast.makeText(this,"Configuração do dispositivo salva.",Toast.LENGTH_SHORT).show();
            if(fechar)finish();
            return true;
        }catch(Throwable e){
            Toast.makeText(this,e.getMessage()==null?"Não foi possível salvar.":e.getMessage(),Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void procurarMaster(){
        if("MASTER".equals(papelSelecionado())){
            Toast.makeText(this,"Este aparelho está selecionado como Master.",Toast.LENGTH_LONG).show();
            return;
        }
        if(!salvar(false))return;

        buscarMaster.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Procurando Master no roteador…");

        new Thread(()->{
            try{
                TechCellLanClient.MasterInfo descoberto=TechCellLanClient.descobrirPrimeiro(4000);
                if(descoberto==null){
                    runOnUiThread(()->{
                        buscarMaster.setEnabled(true);
                        resultadoRede.setTextColor(TechCellUi.RED);
                        resultadoRede.setText("Nenhum Master encontrado. Confira se os dois aparelhos estão no mesmo roteador e se o Master está aberto/configurado.");
                    });
                    return;
                }
                TechCellLanClient.MasterInfo confirmado=TechCellLanClient.testarConexao(
                        descoberto.host,descoberto.port,atual.dispositivoUuid);
                runOnUiThread(()->mostrarMasterEncontrado(confirmado));
            }catch(Throwable e){
                runOnUiThread(()->{
                    buscarMaster.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Falha na procura: "+mensagem(e));
                });
            }
        },"TechCell-Discovery").start();
    }

    private void mostrarMasterEncontrado(TechCellLanClient.MasterInfo info){
        buscarMaster.setEnabled(true);
        resultadoRede.setTextColor(TechCellUi.GREEN);
        resultadoRede.setText("Master encontrado: "+nomeMaster(info)+" • "+info.host+":"+info.port);

        String msg="Master: "+nomeMaster(info)+"\n"+
                "IP: "+info.host+":"+info.port+"\n"+
                "Empresa: "+curto(info.empresaUuid)+"\n"+
                "Filial: "+curto(info.filialUuid)+"\n\n"+
                "Ao conectar, este aparelho passará a pertencer à empresa/filial do Master.";

        new AlertDialog.Builder(this)
                .setTitle("Master Tech Cell encontrado")
                .setMessage(msg)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Conectar",(d,w)->vincular(info))
                .show();
    }

    private void vincular(TechCellLanClient.MasterInfo info){
        try{
            String papel=papelSelecionado();
            if("MASTER".equals(papel)||papel.isEmpty())throw new IllegalStateException("Selecione Caixa, Administrador ou Consulta.");
            db.salvarConfiguracaoDispositivo(nome.getText().toString(),papel,info.host,info.port);
            db.vincularAoMaster(info.host,info.port,info.empresaUuid,info.filialUuid,info.dispositivoUuid,info.nome);
            atual=db.getSyncContext();
            masterHost.setText(info.host);masterPort.setText(String.valueOf(info.port));
            resultadoRede.setTextColor(TechCellUi.GREEN);
            resultadoRede.setText("CONECTADO ✓  "+nomeMaster(info)+" • empresa "+curto(info.empresaUuid)+" • filial "+curto(info.filialUuid));
            Toast.makeText(this,"Aparelho vinculado ao Master.",Toast.LENGTH_LONG).show();
        }catch(Throwable e){
            resultadoRede.setTextColor(TechCellUi.RED);
            resultadoRede.setText("Vínculo bloqueado: "+mensagem(e));
            new AlertDialog.Builder(this).setTitle("Não foi possível vincular").setMessage(mensagem(e)).setPositiveButton("OK",null).show();
        }
    }

    private void testarMasterSalvo(){
        String host=masterHost.getText().toString().trim();
        int porta=portaDigitada();
        if(host.isEmpty()){
            Toast.makeText(this,"Procure o Master primeiro ou informe o endereço.",Toast.LENGTH_LONG).show();
            return;
        }
        testarConexao.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Testando "+host+":"+porta+"…");
        new Thread(()->{
            try{
                TechCellLanClient.MasterInfo info=TechCellLanClient.testarConexao(host,porta,atual.dispositivoUuid);
                db.registrarMasterOnline(info.host);
                runOnUiThread(()->{
                    testarConexao.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.GREEN);
                    resultadoRede.setText("CONEXÃO LOCAL OK ✓  "+nomeMaster(info)+" • "+info.host+":"+info.port);
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    testarConexao.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Sem resposta do Master: "+mensagem(e));
                });
            }
        },"TechCell-Ping").start();
    }

    private String nomeMaster(TechCellLanClient.MasterInfo info){
        return info.nome==null||info.nome.trim().isEmpty()?"Master Tech Cell":info.nome.trim();
    }

    private String mensagem(Throwable e){
        if(e==null)return "erro desconhecido";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;
    }

    private void reiniciarMaster(){
        pararMaster();
        iniciarMaster();
    }

    private void iniciarMaster(){
        try{
            Intent i=new Intent(this,TechCellMasterService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Throwable e){
            Toast.makeText(this,"Não foi possível iniciar o Master: "+mensagem(e),Toast.LENGTH_LONG).show();
        }
    }

    private void pararMaster(){
        try{stopService(new Intent(this,TechCellMasterService.class));}catch(Throwable ignored){}
    }

    private String ipv4Local(){
        try{
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()){
                NetworkInterface ni=interfaces.nextElement();
                if(!ni.isUp()||ni.isLoopback())continue;
                Enumeration<InetAddress> enderecos=ni.getInetAddresses();
                while(enderecos.hasMoreElements()){
                    InetAddress a=enderecos.nextElement();
                    if(a instanceof Inet4Address&&!a.isLoopbackAddress()){
                        String host=a.getHostAddress();
                        if(host!=null&&!host.startsWith("169.254."))return host;
                    }
                }
            }
        }catch(Throwable ignored){}
        return "";
    }
}
