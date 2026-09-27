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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

public class ConfiguracaoDispositivoActivity extends Activity {
    private GestaoDbHelper db;
    private GestaoDbHelper.SyncContext atual;
    private EditText nome;
    private EditText masterHost;
    private EditText masterPort;
    private EditText pairingCode;
    private RadioGroup papeis;
    private LinearLayout blocoMasterRemoto;
    private TextView dicaRede;
    private TextView resultadoRede;
    private TextView resumoSync;
    private Button buscarMaster;
    private Button testarConexao;
    private Button sincronizarAgora;
    private Button enviarVendas;
    private TextView resumoVendas;
    private Button atualizarProdutos;
    private TextView resumoProdutos;
    private TextView bateriaStatus;
    private Button liberarBateria;

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
        TechCellBackgroundSync.garantir(this);
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(db!=null){
            atual=db.getSyncContext();
            TechCellBackgroundSync.garantir(this);
            atualizarProtecaoBateria();
        }
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
        TextView sub=text("Pareamento e sincronização inicial • Alpha 39",13,false);
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
        TextView explicacao=text("O Master guarda a base principal. O terminal recebe uma cópia local inicial para produtos, estoque, clientes, fornecedores e configuração da empresa.",12,false);
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
        masterHost=input("Ex.: 10.0.0.195");
        masterHost.setText(atual.masterHost==null?"":atual.masterHost);
        blocoMasterRemoto.addView(masterHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        TextView p=text("Porta local",12,true);p.setTextColor(TechCellUi.MUTED);
        p.setPadding(0,dp(10),0,dp(5));blocoMasterRemoto.addView(p);
        masterPort=input("8765");masterPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        masterPort.setText(String.valueOf(atual.masterPort>0?atual.masterPort:8765));
        blocoMasterRemoto.addView(masterPort,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        TextView pc=text("Código de pareamento do Master",12,true);pc.setTextColor(TechCellUi.MUTED);
        pc.setPadding(0,dp(10),0,dp(5));blocoMasterRemoto.addView(pc);
        pairingCode=input("6 dígitos mostrados no Master");
        pairingCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        blocoMasterRemoto.addView(pairingCode,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));

        testarConexao=new Button(this);
        testarConexao.setText("Testar / vincular ao Master");
        TechCellUi.styleSecondary(this,testarConexao);
        testarConexao.setOnClickListener(v->testarMasterManual());
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        tp.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(testarConexao,tp);

        sincronizarAgora=new Button(this);
        sincronizarAgora.setText("↓  Sincronizar dados do Master");
        sincronizarAgora.setTextSize(15);TechCellUi.stylePrimary(this,sincronizarAgora,TechCellUi.GREEN);
        sincronizarAgora.setOnClickListener(v->sincronizarComMasterSalvo());
        LinearLayout.LayoutParams sy=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));
        sy.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(sincronizarAgora,sy);

        enviarVendas=new Button(this);
        enviarVendas.setText("↑  Enviar vendas pendentes ao Master");
        enviarVendas.setTextSize(15);TechCellUi.stylePrimary(this,enviarVendas,TechCellUi.BLUE);
        enviarVendas.setOnClickListener(v->enviarVendasPendentes());
        LinearLayout.LayoutParams ev=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));
        ev.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(enviarVendas,ev);

        resumoVendas=text(resumoVendasPendentes(),12,true);
        resumoVendas.setTextColor(db.countVendasPendentesMaster()>0?TechCellUi.ORANGE:TechCellUi.GREEN);
        resumoVendas.setPadding(0,dp(10),0,0);
        blocoMasterRemoto.addView(resumoVendas);

        atualizarProdutos=new Button(this);
        atualizarProdutos.setText("↻  Atualizar vendas / produtos / estoque agora");
        atualizarProdutos.setTextSize(15);TechCellUi.styleSecondary(this,atualizarProdutos);
        atualizarProdutos.setOnClickListener(v->atualizarProdutosDoMaster());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        ap.setMargins(0,dp(10),0,0);blocoMasterRemoto.addView(atualizarProdutos,ap);

        resumoProdutos=text(resumoProdutosIncremental(),12,true);
        resumoProdutos.setTextColor(atual.lastProductPullError!=null&&!atual.lastProductPullError.trim().isEmpty()
                ?TechCellUi.RED:TechCellUi.GREEN);
        resumoProdutos.setPadding(0,dp(8),0,0);
        blocoMasterRemoto.addView(resumoProdutos);

        if(atual.masterName!=null&&!atual.masterName.trim().isEmpty()){
            TextView vinc=text("Vinculado a: "+atual.masterName+" • "+atual.masterHost,12,true);
            vinc.setTextColor(TechCellUi.GREEN);vinc.setPadding(0,dp(10),0,0);
            blocoMasterRemoto.addView(vinc);
        }
        root.addView(blocoMasterRemoto);

        dicaRede=text("",13,true);dicaRede.setPadding(dp(12),dp(11),dp(12),dp(11));
        dicaRede.setBackground(TechCellUi.pillBackground(this));
        root.addView(dicaRede,TechCellUi.fullCardParams(this,12));

        LinearLayout bateria=TechCellUi.card(this);
        bateria.setLayoutParams(TechCellUi.fullCardParams(this,10));
        bateria.addView(text("Tela bloqueada e bateria",15,true));

        bateriaStatus=text("",13,true);
        bateriaStatus.setPadding(0,dp(8),0,0);
        bateria.addView(bateriaStatus);

        TextView bateriaExp=text(
                "Para Master e Caixa continuarem ativos com a tela apagada, deixe o Tech Cell sem otimização/restrição de bateria.",
                12,false);
        bateriaExp.setTextColor(TechCellUi.MUTED);
        bateriaExp.setPadding(0,dp(6),0,0);
        bateria.addView(bateriaExp);

        liberarBateria=new Button(this);
        liberarBateria.setText("Liberar funcionamento com tela bloqueada");
        liberarBateria.setTextSize(14);
        TechCellUi.stylePrimary(this,liberarBateria,TechCellUi.BLUE);
        liberarBateria.setOnClickListener(v->abrirProtecaoBateria());
        LinearLayout.LayoutParams batp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        batp.setMargins(0,dp(10),0,0);
        bateria.addView(liberarBateria,batp);
        root.addView(bateria);

        atualizarProtecaoBateria();

        resultadoRede=text("Rede local: aguardando teste.",12,true);
        resultadoRede.setTextColor(TechCellUi.MUTED);
        resultadoRede.setPadding(dp(12),dp(10),dp(12),dp(10));
        resultadoRede.setBackground(TechCellUi.cardBackground(this));
        root.addView(resultadoRede,TechCellUi.fullCardParams(this,8));

        resumoSync=text(resumoSincronizacao(),12,true);
        resumoSync.setTextColor(atual.lastSnapshotAt>0?TechCellUi.GREEN:TechCellUi.MUTED);
        resumoSync.setPadding(dp(12),dp(10),dp(12),dp(10));
        resumoSync.setBackground(TechCellUi.cardBackground(this));
        root.addView(resumoSync,TechCellUi.fullCardParams(this,8));

        papeis.setOnCheckedChangeListener((group,checkedId)->atualizarTipo());
        atualizarTipo();

        Button salvar=new Button(this);salvar.setText("Salvar configuração");
        salvar.setTextSize(16);TechCellUi.stylePrimary(this,salvar,TechCellUi.GREEN);
        salvar.setOnClickListener(v->salvar(true));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));
        sp.setMargins(0,dp(16),0,0);root.addView(salvar,sp);

        TextView aviso=text("Alpha 39 sincroniza vendas e estornos nos dois sentidos, mantém o número oficial do Master em todos os aparelhos e continua sincronizando produtos, preços e estoque automaticamente.",11,false);
        aviso.setTextColor(TechCellUi.MUTED);aviso.setGravity(Gravity.CENTER);
        aviso.setPadding(dp(8),dp(12),dp(8),0);root.addView(aviso);

        setContentView(scroll);
    }

    private void atualizarProtecaoBateria(){
        if(bateriaStatus==null||liberarBateria==null)return;
        boolean ok=TechCellBatteryGuard.liberado(this);
        bateriaStatus.setText(TechCellBatteryGuard.status(this));
        bateriaStatus.setTextColor(ok?TechCellUi.GREEN:TechCellUi.ORANGE);
        liberarBateria.setText(ok
                ?"Bateria liberada ✓  •  revisar configuração"
                :"Liberar funcionamento com tela bloqueada");
        TechCellBackgroundSync.garantir(this);
    }

    private void abrirProtecaoBateria(){
        new AlertDialog.Builder(this)
                .setTitle("Funcionamento com tela bloqueada")
                .setMessage("O Android pode reduzir a atividade de rede quando a tela fica apagada. Na próxima tela, autorize o Tech Cell a ignorar a otimização de bateria ou deixe o aplicativo como “Sem restrições/Não otimizar”.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Abrir configuração",(d,w)->TechCellBatteryGuard.abrirConfiguracao(this))
                .show();
    }

    private String resumoVendasPendentes(){
        int pendentes=db.countVendasPendentesMaster();
        if(pendentes>0)return "Vendas aguardando envio ao Master: "+pendentes;
        if(atual.lastSalePullError!=null&&!atual.lastSalePullError.trim().isEmpty()){
            return "Falha ao receber histórico do Master: "+atual.lastSalePullError;
        }
        if(atual.lastSalePullAt>0){
            return "Vendas sincronizadas nos dois sentidos ✓ • cursor "+atual.lastSalePullSeq+
                    " • recebidas no último ciclo: "+atual.lastSalePullCount;
        }
        if(atual.lastSalePushAt>0 && (atual.lastSalePushError==null||atual.lastSalePushError.trim().isEmpty())){
            return "Vendas locais enviadas ao Master ✓ • aguardando primeiro recebimento.";
        }
        return "Vendas pendentes: 0 • histórico do Master aguardando primeiro ciclo.";
    }

    private String resumoProdutosIncremental(){
        if(atual.masterAuthToken==null||atual.masterAuthToken.trim().isEmpty()){
            return "Atualização incremental: aguardando autorização do Master.";
        }
        if(atual.lastProductPullError!=null&&!atual.lastProductPullError.trim().isEmpty()){
            return "Última atualização incremental falhou: "+atual.lastProductPullError;
        }
        if(atual.lastProductPullAt<=0){
            return "Sincronização em segundo plano: pronta para o primeiro ciclo.";
        }
        String hora=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",new Locale("pt","BR")).format(new Date(atual.lastProductPullAt));
        return "Segundo plano ativo • produtos/estoque: "+hora+" • cursor "+atual.lastProductPullSeq+
                " • alterações recebidas: "+atual.lastProductPullCount;
    }

    private String resumoSincronizacao(){
        if(atual.lastSnapshotAt<=0)return "Dados do Master: ainda não sincronizados.";
        String hora=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",new Locale("pt","BR")).format(new Date(atual.lastSnapshotAt));
        return "Última sincronização: "+hora+"\n"+
                "Produtos: "+atual.lastSnapshotProdutos+"  •  Clientes: "+atual.lastSnapshotClientes+
                "  •  Fornecedores: "+atual.lastSnapshotFornecedores;
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
            String codigo=atual.lanPairingCode==null||atual.lanPairingCode.trim().isEmpty()?"—":atual.lanPairingCode;
            dicaRede.setText(
                    "Modo Master Android\n"+
                    "IP atual: "+(ip.isEmpty()?"não identificado":ip)+"  •  Porta "+portaDigitada()+
                    "\nCódigo de pareamento: "+codigo+
                    "\nO código é necessário para um novo terminal copiar os dados.");
        }else{
            dicaRede.setText(
                    "Modo terminal\n"+
                    "Pode usar a busca automática ou informar o IP do Master manualmente. Internet não é necessária.");
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
            TechCellBackgroundSync.garantir(this);
            if("MASTER".equals(papel)){
                resultadoRede.setText("Master local iniciado. Os terminais podem conectar usando o código de pareamento.");
                resultadoRede.setTextColor(TechCellUi.GREEN);
            }else if(atual.masterAuthToken!=null&&!atual.masterAuthToken.trim().isEmpty()){
                resultadoRede.setText("Sincronização automática em segundo plano ativa ✓");
                resultadoRede.setTextColor(TechCellUi.GREEN);
            }
            atualizarTipo();
            Toast.makeText(this,"Configuração do dispositivo salva.",Toast.LENGTH_SHORT).show();
            if(fechar)finish();
            return true;
        }catch(Throwable e){
            Toast.makeText(this,mensagem(e),Toast.LENGTH_LONG).show();
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
                        resultadoRede.setText("Busca automática não encontrou o Master. Você pode informar o IP manualmente e usar “Testar / vincular”.");
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

    private void testarMasterManual(){
        if(!salvar(false))return;
        String host=masterHost.getText().toString().trim();
        int porta=portaDigitada();
        if(host.isEmpty()){
            Toast.makeText(this,"Informe o IP do Master.",Toast.LENGTH_LONG).show();
            return;
        }

        testarConexao.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Testando "+host+":"+porta+"…");

        new Thread(()->{
            try{
                TechCellLanClient.MasterInfo info=TechCellLanClient.testarConexao(host,porta,atual.dispositivoUuid);
                runOnUiThread(()->{
                    testarConexao.setEnabled(true);
                    mostrarMasterEncontrado(info);
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    testarConexao.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Sem resposta do Master: "+mensagem(e));
                });
            }
        },"TechCell-Manual-Ping").start();
    }

    private void mostrarMasterEncontrado(TechCellLanClient.MasterInfo info){
        buscarMaster.setEnabled(true);
        resultadoRede.setTextColor(TechCellUi.GREEN);
        resultadoRede.setText("Master respondeu: "+nomeMaster(info)+" • "+info.host+":"+info.port);

        String msg="Master: "+nomeMaster(info)+"\n"+
                "IP: "+info.host+":"+info.port+"\n"+
                "Empresa: "+curto(info.empresaUuid)+"\n"+
                "Filial: "+curto(info.filialUuid)+"\n\n"+
                "Vincular este aparelho a esse Master?";

        new AlertDialog.Builder(this)
                .setTitle("Master Tech Cell encontrado")
                .setMessage(msg)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Vincular",(d,w)->vincular(info,true))
                .show();
    }

    private void vincular(TechCellLanClient.MasterInfo info, boolean oferecerSync){
        try{
            String papel=papelSelecionado();
            if("MASTER".equals(papel)||papel.isEmpty())throw new IllegalStateException("Selecione Caixa, Administrador ou Consulta.");
            db.salvarConfiguracaoDispositivo(nome.getText().toString(),papel,info.host,info.port);
            db.vincularAoMaster(info.host,info.port,info.empresaUuid,info.filialUuid,info.dispositivoUuid,info.nome);
            atual=db.getSyncContext();
            masterHost.setText(info.host);masterPort.setText(String.valueOf(info.port));
            resultadoRede.setTextColor(TechCellUi.GREEN);
            resultadoRede.setText("VINCULADO ✓  "+nomeMaster(info)+" • empresa "+curto(info.empresaUuid)+" • filial "+curto(info.filialUuid));

            if(oferecerSync){
                new AlertDialog.Builder(this)
                        .setTitle("Vínculo concluído")
                        .setMessage("O Caixa já pertence à empresa do Master. Agora podemos copiar configuração, produtos/estoque, clientes e fornecedores.")
                        .setNegativeButton("Depois",null)
                        .setPositiveButton("Sincronizar agora",(d,w)->sincronizar(info))
                        .show();
            }
        }catch(Throwable e){
            resultadoRede.setTextColor(TechCellUi.RED);
            resultadoRede.setText("Vínculo bloqueado: "+mensagem(e));
            new AlertDialog.Builder(this).setTitle("Não foi possível vincular").setMessage(mensagem(e)).setPositiveButton("OK",null).show();
        }
    }

    private void sincronizarComMasterSalvo(){
        String host=masterHost.getText().toString().trim();
        int porta=portaDigitada();
        if(host.isEmpty()){
            Toast.makeText(this,"Vincule ou informe o Master primeiro.",Toast.LENGTH_LONG).show();
            return;
        }
        sincronizarAgora.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Validando o Master…");
        new Thread(()->{
            try{
                TechCellLanClient.MasterInfo info=TechCellLanClient.testarConexao(host,porta,atual.dispositivoUuid);
                runOnUiThread(()->{
                    sincronizarAgora.setEnabled(true);
                    if(atual.masterDeviceUuid==null || atual.masterDeviceUuid.trim().isEmpty() ||
                            !info.dispositivoUuid.equalsIgnoreCase(atual.masterDeviceUuid)){
                        vincular(info,false);
                    }
                    sincronizar(info);
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    sincronizarAgora.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Não foi possível validar o Master: "+mensagem(e));
                });
            }
        },"TechCell-Validate-Sync").start();
    }

    private void sincronizar(TechCellLanClient.MasterInfo info){
        String codigo=pairingCode.getText().toString().trim();
        if(codigo.isEmpty()){
            pairingCode.requestFocus();
            Toast.makeText(this,"Digite o código de pareamento mostrado no Master.",Toast.LENGTH_LONG).show();
            return;
        }

        sincronizarAgora.setEnabled(false);
        buscarMaster.setEnabled(false);
        testarConexao.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Baixando dados do Master…");

        new Thread(()->{
            try{
                TechCellLanClient.SnapshotDownload download=TechCellLanClient.baixarSnapshot(
                        info.host,info.port,atual.dispositivoUuid,codigo);
                GestaoDbHelper.SnapshotStats st=db.aplicarSnapshotInicial(download.json);
                if(download.authToken!=null&&!download.authToken.trim().isEmpty()){
                    db.salvarTokenMaster(download.authToken);
                }
                db.registrarMasterOnline(info.host);
                TechCellBackgroundSync.garantir(this);
                runOnUiThread(()->{
                    atual=db.getSyncContext();
                    sincronizarAgora.setEnabled(true);buscarMaster.setEnabled(true);testarConexao.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.GREEN);
                    resultadoRede.setText("SINCRONIZAÇÃO OK ✓  Dados recebidos do Master.");
                    resumoSync.setText(resumoSincronizacao());
                    resumoSync.setTextColor(TechCellUi.GREEN);
                    if(resumoVendas!=null){
                        resumoVendas.setText(resumoVendasPendentes());
                        resumoVendas.setTextColor(db.countVendasPendentesMaster()>0?TechCellUi.ORANGE:TechCellUi.GREEN);
                    }
                    if(resumoProdutos!=null){
                        resumoProdutos.setText(resumoProdutosIncremental());
                        resumoProdutos.setTextColor(TechCellUi.GREEN);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Sincronização concluída")
                            .setMessage("Produtos/estoque: "+st.produtos+"\nClientes: "+st.clientes+"\nFornecedores: "+st.fornecedores+
                                    "\n\nAbra Produtos ou Estoque neste aparelho para conferir os dados do Master.")
                            .setPositiveButton("OK",null)
                            .show();
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    sincronizarAgora.setEnabled(true);buscarMaster.setEnabled(true);testarConexao.setEnabled(true);
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Falha na sincronização: "+mensagem(e));
                    new AlertDialog.Builder(this)
                            .setTitle("Sincronização não concluída")
                            .setMessage(mensagem(e))
                            .setPositiveButton("OK",null)
                            .show();
                });
            }
        },"TechCell-Snapshot").start();
    }

    private void enviarVendasPendentes(){
        if("MASTER".equals(papelSelecionado())){
            Toast.makeText(this,"O Master já é a base principal das vendas.",Toast.LENGTH_LONG).show();
            return;
        }
        int qtd=db.countVendasPendentesMaster();
        if(qtd<=0){
            Toast.makeText(this,"Não há vendas pendentes para enviar.",Toast.LENGTH_SHORT).show();
            if(resumoVendas!=null){
                resumoVendas.setText(resumoVendasPendentes());
                resumoVendas.setTextColor(TechCellUi.GREEN);
            }
            return;
        }

        String codigo=pairingCode==null?"":pairingCode.getText().toString().trim();
        GestaoDbHelper.SyncContext ctx=db.getSyncContext();
        boolean temToken=ctx.masterAuthToken!=null&&!ctx.masterAuthToken.trim().isEmpty();
        if(!temToken && codigo.isEmpty()){
            pairingCode.requestFocus();
            Toast.makeText(this,"Informe o código de pareamento do Master para autorizar este Caixa.",Toast.LENGTH_LONG).show();
            return;
        }

        enviarVendas.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Enviando "+qtd+" venda(s) pendente(s) ao Master…");

        new Thread(()->{
            TechCellSaleSync.Resultado r=TechCellSaleSync.enviarPendentes(this,codigo);
            TechCellProductSync.Resultado pr=null;
            TechCellSalePullSync.Resultado vr=null;
            if(r.erro==null||r.erro.trim().isEmpty()){
                pr=TechCellProductSync.puxarAlteracoes(this);
                vr=TechCellSalePullSync.puxarAlteracoes(this);
            }
            TechCellProductSync.Resultado produtosFinal=pr;
            TechCellSalePullSync.Resultado vendasFinal=vr;
            runOnUiThread(()->{
                enviarVendas.setEnabled(true);
                atual=db.getSyncContext();
                if(r.erro==null||r.erro.trim().isEmpty()){
                    resultadoRede.setTextColor(TechCellUi.GREEN);
                    resultadoRede.setText("VENDAS SINCRONIZADAS ✓  Enviadas: "+r.enviadas+
                            (r.jaExistiam>0?" • já existentes no Master: "+r.jaExistiam:""));
                }else{
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Envio interrompido: "+r.erro);
                }
                resumoVendas.setText(resumoVendasPendentes());
                resumoVendas.setTextColor(
                        db.countVendasPendentesMaster()>0 ||
                                (vendasFinal!=null&&vendasFinal.erro!=null&&!vendasFinal.erro.trim().isEmpty())
                                ?TechCellUi.ORANGE:TechCellUi.GREEN);
                if(resumoProdutos!=null){
                    resumoProdutos.setText(resumoProdutosIncremental());
                    resumoProdutos.setTextColor(produtosFinal!=null&&produtosFinal.erro!=null&&!produtosFinal.erro.trim().isEmpty()
                            ?TechCellUi.RED:TechCellUi.GREEN);
                }
            });
        },"TechCell-Sale-Push").start();
    }

    private void atualizarProdutosDoMaster(){
        if("MASTER".equals(papelSelecionado())){
            Toast.makeText(this,"Este aparelho é o Master e já possui a base principal.",Toast.LENGTH_SHORT).show();
            return;
        }
        GestaoDbHelper.SyncContext ctx=db.getSyncContext();
        if(ctx.masterAuthToken==null||ctx.masterAuthToken.trim().isEmpty()){
            Toast.makeText(this,"Faça o pareamento/sincronização inicial com o Master primeiro.",Toast.LENGTH_LONG).show();
            return;
        }
        if(db.countVendasPendentesMaster()>0){
            Toast.makeText(this,"Existem vendas pendentes. Envie as vendas ao Master antes de atualizar o estoque.",Toast.LENGTH_LONG).show();
            return;
        }

        atualizarProdutos.setEnabled(false);
        resultadoRede.setTextColor(TechCellUi.BLUE);
        resultadoRede.setText("Buscando vendas, produtos e estoque no Master…");

        new Thread(()->{
            TechCellProductSync.Resultado r=TechCellProductSync.puxarAlteracoes(this);
            TechCellSalePullSync.Resultado vr=TechCellSalePullSync.puxarAlteracoes(this);
            runOnUiThread(()->{
                atualizarProdutos.setEnabled(true);
                atual=db.getSyncContext();
                boolean okProdutos=r.erro==null||r.erro.trim().isEmpty();
                boolean okVendas=vr.erro==null||vr.erro.trim().isEmpty();
                if(okProdutos&&okVendas){
                    resultadoRede.setTextColor(TechCellUi.GREEN);
                    resultadoRede.setText("SINCRONIZAÇÃO ATUALIZADA ✓  Vendas: "+vr.total()+
                            " • produtos/estoque: "+r.total());
                }else{
                    resultadoRede.setTextColor(TechCellUi.RED);
                    resultadoRede.setText("Falha na atualização: "+
                            (!okVendas?vr.erro:r.erro));
                }
                resumoVendas.setText(resumoVendasPendentes());
                resumoVendas.setTextColor(okVendas?TechCellUi.GREEN:TechCellUi.RED);
                resumoProdutos.setText(resumoProdutosIncremental());
                resumoProdutos.setTextColor(okProdutos?TechCellUi.GREEN:TechCellUi.RED);
            });
        },"TechCell-Manual-Full-Pull").start();
    }

    private String nomeMaster(TechCellLanClient.MasterInfo info){
        return info.nome==null||info.nome.trim().isEmpty()?"Master Tech Cell":info.nome.trim();
    }

    private String mensagem(Throwable e){
        if(e==null)return "erro desconhecido";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;
    }

    private void reiniciarMaster(){ pararMaster(); iniciarMaster(); }

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
