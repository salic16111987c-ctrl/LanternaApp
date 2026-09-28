package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ImportacaoSmbActivity extends Activity {
    private static final int REQ_PACOTE = 7401;
    private static final String FORMAT = "TECHCELL_SMB_MIGRATION_V1";

    private TextView status;
    private LinearLayout resumoBox;
    private Button selecionar;
    private Button limpar;
    private Button importarFinal;

    private int dp(int v){ return TechCellUi.dp(this,v); }

    private TextView txt(String s,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TechCellUi.TEXT);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        return t;
    }

    private Button action(String label){
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(0);
        return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        montar();
        renderResumo();
    }

    private void montar(){
        TechCellUi.applyWindowChrome(this);

        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(32));
        scroll.addView(root);

        Button voltar=action("←  Voltar");
        TechCellUi.styleSecondary(this,voltar);
        voltar.setOnClickListener(v->finish());
        root.addView(voltar,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));

        TextView titulo=txt("Importação SMB",27,true);
        titulo.setPadding(0,dp(16),0,0);
        root.addView(titulo);

        TextView sub=txt("Migração segura • pré-importação • Alpha 42",13,false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout aviso=TechCellUi.card(this);
        aviso.setBackground(TechCellUi.solid(this,TechCellUi.PALE_BLUE,14));
        aviso.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView at=txt("ETAPA 1 — ANALISAR SEM ALTERAR A BASE",12,true);
        at.setTextColor(TechCellUi.NAVY);
        aviso.addView(at);
        TextView av=txt(
                "O pacote é carregado em uma área separada do banco. Produtos, vendas, estoque, clientes e financeiro do Tech Cell não são alterados nesta etapa.",
                13,false);
        av.setTextColor(Color.parseColor("#475467"));
        av.setPadding(0,dp(6),0,0);
        aviso.addView(av);
        root.addView(aviso);

        selecionar=action("📂  Selecionar pacote SMB");
        TechCellUi.stylePrimary(this,selecionar,TechCellUi.GREEN);
        selecionar.setOnClickListener(v->selecionarPacote());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        sp.setMargins(0,dp(14),0,0);
        root.addView(selecionar,sp);

        status=txt("Nenhum pacote carregado.",12,true);
        status.setTextColor(TechCellUi.NAVY);
        status.setGravity(Gravity.CENTER);
        status.setBackground(TechCellUi.pillBackground(this));
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.addView(status,TechCellUi.fullCardParams(this,10));

        resumoBox=new LinearLayout(this);
        resumoBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resumoBox);

        importarFinal=action("🔒  Importação final — aguardando validação");
        TechCellUi.styleSecondary(this,importarFinal);
        importarFinal.setEnabled(false);
        importarFinal.setAlpha(0.55f);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        ip.setMargins(0,dp(12),0,0);
        root.addView(importarFinal,ip);

        TextView bloqueio=txt(
                "Nesta Alpha 42, a migração fica em pré-importação. Só liberaremos a gravação definitiva depois de validar duplicidades, histórico de vendas, caixa e pagamentos.",
                11,false);
        bloqueio.setTextColor(TechCellUi.MUTED);
        bloqueio.setPadding(dp(4),dp(7),dp(4),0);
        root.addView(bloqueio);

        limpar=action("🗑  Limpar somente a pré-importação");
        TechCellUi.styleSecondary(this,limpar);
        limpar.setOnClickListener(v->confirmarLimpeza());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        lp.setMargins(0,dp(14),0,0);
        root.addView(limpar,lp);

        setContentView(scroll);
    }

    private void selecionarPacote(){
        if(TechCellSyncCoordinator.estaSincronizando()){
            Toast.makeText(this,
                    "Aguarde a sincronização terminar e tente novamente.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_PACOTE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_PACOTE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        carregarPacote(data.getData());
    }

    private void carregarPacote(Uri uri){
        setOcupado(true,"Lendo e validando pacote SMB…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "Sincronização ocupada. Tente novamente em alguns segundos."));
                return;
            }

            GestaoDbHelper helper=new GestaoDbHelper(getApplicationContext());
            SQLiteDatabase db=null;
            try{
                TechCellBackgroundSync.parar(getApplicationContext());
                InputStream in=getContentResolver().openInputStream(uri);
                if(in==null)throw new IllegalStateException("Não foi possível abrir o arquivo.");

                try(BufferedReader reader=new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8),64*1024)){
                    String primeira=reader.readLine();
                    if(primeira==null||primeira.trim().isEmpty())
                        throw new IllegalArgumentException("Arquivo vazio.");

                    JSONObject manifest=new JSONObject(primeira);
                    if(!"MANIFEST".equalsIgnoreCase(manifest.optString("record_type")))
                        throw new IllegalArgumentException("O arquivo não possui manifesto de migração.");
                    String format=manifest.optString("format","");
                    if(!FORMAT.equals(format))
                        throw new IllegalArgumentException(
                                "Formato não reconhecido. Selecione um pacote .techcellmigration preparado para a Alpha 42.");

                    db=helper.getWritableDatabase();
                    db.beginTransaction();

                    long session=helper.iniciarPreImportacaoSmb(
                            db,
                            nomeDocumento(uri),
                            manifest.optString("source_system",""),
                            manifest.optString("source_file",""),
                            manifest.optString("backup_date",""),
                            format,
                            manifest.toString());

                    int total=0;
                    int warnings=0;
                    String linha;
                    while((linha=reader.readLine())!=null){
                        if(linha.trim().isEmpty())continue;
                        JSONObject rec=new JSONObject(linha);
                        String type=rec.optString("record_type","").trim().toUpperCase(Locale.ROOT);
                        if(type.isEmpty()||"MANIFEST".equals(type))continue;

                        JSONObject dataObj=rec.optJSONObject("data");
                        if(dataObj==null)dataObj=new JSONObject();

                        String sourceId=rec.optString("source_id","");
                        String parent=rec.optString("parent_source_id","");
                        String nome=rec.optString("display_name","");
                        String key1="";
                        String key2="";
                        String warning="";
                        String validation="OK";

                        if("PRODUTO".equals(type)){
                            key1=valor(dataObj,"CODPROD");
                            key2=valor(dataObj,"CODBARRAS");
                            if(nome.trim().isEmpty()){
                                validation="AVISO";
                                warning="Produto sem nome.";
                            }
                        }else if("FORNECEDOR".equals(type)){
                            key1=valor(dataObj,"CNPJ");
                        }else if("CLIENTE".equals(type)){
                            key1=valor(dataObj,"CPF");
                        }else if("VENDA_ITEM".equals(type)){
                            key1=valor(dataObj,"CODPROD");
                            key2=valor(dataObj,"CODBARRAS");
                            if(parent.trim().isEmpty()){
                                validation="AVISO";
                                warning="Item de venda sem número da venda.";
                            }
                        }else if("CAIXA".equals(type)){
                            key1=valor(dataObj,"CODLANC");
                        }else if("AJUSTE_ESTOQUE".equals(type)){
                            key1=valor(dataObj,"CODPROD");
                        }

                        if(sourceId.trim().isEmpty()){
                            validation="AVISO";
                            warning=warning.isEmpty()?"Registro sem identificador de origem.":warning;
                        }
                        if("AVISO".equals(validation))warnings++;

                        helper.inserirRegistroSmb(
                                db,session,type,sourceId,parent,nome,key1,key2,
                                dataObj.toString(),validation,warning);
                        total++;

                        if(total%2000==0){
                            final int progresso=total;
                            runOnUiThread(()->status.setText(
                                    "Carregando pré-importação… "+progresso+" registros"));
                        }
                    }

                    helper.finalizarPreImportacaoSmb(db,session,total,warnings);
                    db.setTransactionSuccessful();
                }finally{
                    if(db!=null && db.inTransaction())db.endTransaction();
                }

                runOnUiThread(()->{
                    setOcupado(false,"Pré-importação concluída com sucesso. Nenhum dado da base principal foi alterado.");
                    renderResumo();
                    mostrarConclusaoPreImportacao();
                });
            }catch(Throwable e){
                if(db!=null && db.inTransaction()){
                    try{db.endTransaction();}catch(Throwable ignored){}
                }
                runOnUiThread(()->{
                    setOcupado(false,"Pacote não carregado: "+mensagem(e));
                    renderResumo();
                });
            }finally{
                helper.close();
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-SMB-Stage").start();
    }

    private void renderResumo(){
        resumoBox.removeAllViews();
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SmbImportResumo r=db.resumoImportacaoSmb();
        if(r==null){
            limpar.setEnabled(false);
            limpar.setAlpha(0.55f);
            db.close();
            return;
        }
        limpar.setEnabled(true);
        limpar.setAlpha(1f);

        LinearLayout card=TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this,12));
        TextView t=txt("Pré-importação carregada",16,true);
        card.addView(t);

        String fonte=(r.sourceSystem==null||r.sourceSystem.trim().isEmpty())?r.sourceName:r.sourceSystem;
        TextView f=txt(fonte+"\nBackup: "+vazio(r.sourceFile)+"  •  "+vazio(r.backupDate),11,false);
        f.setTextColor(TechCellUi.MUTED);
        f.setPadding(0,dp(4),0,dp(8));
        card.addView(f);

        card.addView(linha("Produtos",r.produtos));
        card.addView(linha("Vendas encontradas",r.vendas));
        card.addView(linha("Itens de vendas",r.vendaItens));
        card.addView(linha("Fornecedores",r.fornecedores));
        card.addView(linha("Clientes",r.clientes));
        card.addView(linha("Grupos",r.grupos));
        card.addView(linha("Fabricantes",r.fabricantes));
        card.addView(linha("Lançamentos de caixa",r.caixa));
        card.addView(linha("Ajustes de estoque",r.ajustesEstoque));

        TextView sep=txt("Comparação com a base atual",13,true);
        sep.setPadding(0,dp(12),0,dp(4));
        card.addView(sep);

        int atuais=db.count();
        card.addView(linha("Produtos já no Tech Cell",atuais));
        TextView conflito=txt(
                "Possíveis produtos já existentes: "+r.conflitosProdutos,
                13,true);
        conflito.setTextColor(r.conflitosProdutos>0?Color.parseColor("#B54708"):TechCellUi.GREEN);
        conflito.setPadding(0,dp(4),0,0);
        card.addView(conflito);

        if(r.warnings>0){
            TextView warn=txt("Avisos na leitura: "+r.warnings,12,true);
            warn.setTextColor(Color.parseColor("#B54708"));
            warn.setPadding(0,dp(7),0,0);
            card.addView(warn);
        }

        TextView safe=txt(
                "✓ Base principal preservada — esta prévia está somente na área de migração.",
                12,true);
        safe.setTextColor(TechCellUi.GREEN);
        safe.setPadding(0,dp(10),0,0);
        card.addView(safe);

        resumoBox.addView(card);
        db.close();
    }

    private TextView linha(String label,int value){
        TextView t=txt(label+": "+String.format(new Locale("pt","BR"),"%,d",value),13,false);
        t.setPadding(0,dp(3),0,dp(3));
        return t;
    }

    private void mostrarConclusaoPreImportacao(){
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SmbImportResumo r=db.resumoImportacaoSmb();
        db.close();
        if(r==null)return;

        String total=String.format(new Locale("pt","BR"),"%,d",r.stagedRecords);
        String produtos=String.format(new Locale("pt","BR"),"%,d",r.produtos);
        String vendas=String.format(new Locale("pt","BR"),"%,d",r.vendas);

        StringBuilder msg=new StringBuilder();
        msg.append(total).append(" registros foram analisados e carregados somente na área de pré-importação.");
        msg.append("\n\nProdutos: ").append(produtos);
        msg.append("\nVendas encontradas: ").append(vendas);
        msg.append("\nPossíveis produtos já existentes: ").append(r.conflitosProdutos);
        if(r.warnings>0)msg.append("\nAvisos para revisar: ").append(r.warnings);
        msg.append("\n\nNenhum dado da base principal foi alterado.");

        new AlertDialog.Builder(this)
                .setTitle("Pré-importação concluída com sucesso")
                .setMessage(msg.toString())
                .setPositiveButton("Ver resultado",null)
                .setCancelable(false)
                .show();
    }

    private void confirmarLimpeza(){
        new AlertDialog.Builder(this)
                .setTitle("Limpar pré-importação?")
                .setMessage("Isso apaga somente a área temporária da migração. Produtos, vendas, estoque e demais dados do Tech Cell não serão alterados.")
                .setPositiveButton("Limpar",(d,w)->{
                    GestaoDbHelper db=new GestaoDbHelper(this);
                    db.limparPreImportacaoSmb();
                    db.close();
                    status.setText("Pré-importação limpa.");
                    renderResumo();
                })
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void setOcupado(boolean ocupado,String texto){
        selecionar.setEnabled(!ocupado);
        limpar.setEnabled(!ocupado);
        selecionar.setAlpha(ocupado?0.55f:1f);
        limpar.setAlpha(ocupado?0.55f:1f);
        status.setText(texto);
    }

    private String valor(JSONObject o,String chave){
        if(o==null||o.isNull(chave))return "";
        String x=o.optString(chave,"");
        return x==null?"":x.trim();
    }

    private String vazio(String s){
        return s==null||s.trim().isEmpty()?"—":s.trim();
    }

    private String nomeDocumento(Uri uri){
        try{
            android.database.Cursor c=getContentResolver().query(uri,null,null,null,null);
            if(c!=null){
                try{
                    int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if(i>=0&&c.moveToFirst())return c.getString(i);
                }finally{c.close();}
            }
        }catch(Throwable ignored){}
        return "pacote SMB";
    }

    private String mensagem(Throwable e){
        if(e==null)return "erro desconhecido";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();
    }
}
