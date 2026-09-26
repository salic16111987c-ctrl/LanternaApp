package com.techcell.caixadaloja;

import android.app.Activity;
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
        r.setText(titulo);
        r.setTag(valor);
        r.setTextSize(15);
        r.setTextColor(TechCellUi.TEXT);
        r.setPadding(0,dp(6),0,dp(6));
        return r;
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        TechCellUi.applyWindowChrome(this);
        db=new GestaoDbHelper(this);
        atual=db.getSyncContext();
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
        voltar.setText("←  Voltar");
        TechCellUi.styleSecondary(this,voltar);
        voltar.setOnClickListener(v->finish());
        root.addView(voltar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView titulo=text("Dispositivo e rede",27,true);
        titulo.setPadding(0,dp(16),0,0);
        root.addView(titulo);
        TextView sub=text("Defina a função deste aparelho na loja • Alpha 31",13,false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout identidade=TechCellUi.card(this);
        identidade.setLayoutParams(TechCellUi.fullCardParams(this,16));
        identidade.addView(text("Identidade desta instalação",15,true));
        TextView ids=text(
                "Empresa  "+curto(atual.empresaUuid)+"\n"+
                "Filial       "+curto(atual.filialUuid)+"\n"+
                "Dispositivo  "+curto(atual.dispositivoUuid),
                13,false);
        ids.setTextColor(TechCellUi.MUTED);
        ids.setPadding(0,dp(8),0,0);
        identidade.addView(ids);
        root.addView(identidade);

        TextView nomeLabel=text("Nome deste aparelho",14,true);
        nomeLabel.setPadding(0,dp(18),0,dp(7));
        root.addView(nomeLabel);
        nome=input("Ex.: Caixa 1, Celular Master, Balcão");
        String nomeAtual=atual.nomeDispositivo==null?"":atual.nomeDispositivo.trim();
        if(!atual.configurado && (nomeAtual.isEmpty()||"Este aparelho".equalsIgnoreCase(nomeAtual))){
            String modelo=(Build.MANUFACTURER+" "+Build.MODEL).trim();
            nome.setText(modelo);
        }else{
            nome.setText(nomeAtual);
        }
        root.addView(nome,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        TextView funcao=text("Função deste aparelho",14,true);
        funcao.setPadding(0,dp(18),0,dp(4));
        root.addView(funcao);
        TextView explicacao=text("O Master concentra os dados da loja. Os demais aparelhos conversarão com ele pelo mesmo roteador.",12,false);
        explicacao.setTextColor(TechCellUi.MUTED);
        root.addView(explicacao);

        papeis=new RadioGroup(this);
        papeis.setOrientation(RadioGroup.VERTICAL);
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
        TextView h=text("Endereço do Master",12,true);h.setTextColor(TechCellUi.MUTED);h.setPadding(0,dp(10),0,dp(5));blocoMasterRemoto.addView(h);
        masterHost=input("Pode ficar vazio por enquanto");
        masterHost.setText(atual.masterHost==null?"":atual.masterHost);
        blocoMasterRemoto.addView(masterHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        TextView p=text("Porta local",12,true);p.setTextColor(TechCellUi.MUTED);p.setPadding(0,dp(10),0,dp(5));blocoMasterRemoto.addView(p);
        masterPort=input("8765");
        masterPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        masterPort.setText(String.valueOf(atual.masterPort>0?atual.masterPort:8765));
        blocoMasterRemoto.addView(masterPort,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        TextView auto=text("O endereço pode permanecer vazio nesta Alpha. Na próxima etapa o sistema procurará o Master automaticamente na rede local.",12,false);
        auto.setTextColor(TechCellUi.MUTED);auto.setPadding(0,dp(9),0,0);blocoMasterRemoto.addView(auto);
        root.addView(blocoMasterRemoto);

        dicaRede=text("",13,true);
        dicaRede.setPadding(dp(12),dp(11),dp(12),dp(11));
        dicaRede.setBackground(TechCellUi.pillBackground(this));
        root.addView(dicaRede,TechCellUi.fullCardParams(this,12));

        papeis.setOnCheckedChangeListener((group,checkedId)->atualizarTipo());
        atualizarTipo();

        Button salvar=new Button(this);
        salvar.setText("Salvar configuração");
        salvar.setTextSize(16);
        TechCellUi.stylePrimary(this,salvar,TechCellUi.GREEN);
        salvar.setOnClickListener(v->salvar());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));
        sp.setMargins(0,dp(16),0,0);
        root.addView(salvar,sp);

        TextView aviso=text("Esta Alpha apenas salva a identidade e a função do aparelho. Ela ainda não transforma o Android em servidor de rede.",11,false);
        aviso.setTextColor(TechCellUi.MUTED);
        aviso.setGravity(Gravity.CENTER);
        aviso.setPadding(dp(8),dp(12),dp(8),0);
        root.addView(aviso);

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
                    "Este aparelho será o servidor local da loja.\n"+
                    "IP atual na rede: "+(ip.isEmpty()?"não identificado":ip)+
                    "\nTodos os caixas deverão estar no mesmo roteador.");
        }else{
            dicaRede.setText(
                    "Modo terminal\n"+
                    "Este aparelho usará o Master da mesma loja pela rede local do roteador.");
        }
    }

    private void salvar(){
        try{
            String papel=papelSelecionado();
            if(papel.isEmpty())throw new IllegalArgumentException("Selecione a função deste aparelho.");
            int porta=8765;
            String p=masterPort.getText().toString().trim();
            if(!p.isEmpty())porta=Integer.parseInt(p);
            String host="MASTER".equals(papel)?"":masterHost.getText().toString().trim();
            db.salvarConfiguracaoDispositivo(nome.getText().toString(),papel,host,porta);
            Toast.makeText(this,"Configuração do dispositivo salva.",Toast.LENGTH_LONG).show();
            finish();
        }catch(NumberFormatException e){
            Toast.makeText(this,"Porta inválida.",Toast.LENGTH_LONG).show();
        }catch(Throwable e){
            Toast.makeText(this,e.getMessage()==null?"Não foi possível salvar.":e.getMessage(),Toast.LENGTH_LONG).show();
        }
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
