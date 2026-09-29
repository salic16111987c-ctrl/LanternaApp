package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class ImportacaoSmbActivity extends Activity {
    private static final int REQ_PACOTE = 7401;
    private static final String FORMAT = "TECHCELL_SMB_MIGRATION_V1";

    private TextView status;
    private LinearLayout resumoBox;
    private Button selecionar;
    private Button limpar;
    private Button importarCadastros;
    private Button importarVendas;
    private Button limparBase;
    private Button limiteEstoque;
    private Button corrigirEstoqueAtual;

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

        LinearLayout filtroEstoque=TechCellUi.card(this);
        filtroEstoque.setBackground(TechCellUi.solid(this,TechCellUi.PALE_BLUE,14));
        filtroEstoque.setLayoutParams(TechCellUi.fullCardParams(this,12));

        TextView filtroTitulo=txt("FILTRO DE SEGURANÇA DO ESTOQUE",12,true);
        filtroTitulo.setTextColor(TechCellUi.NAVY);
        filtroEstoque.addView(filtroTitulo);

        TextView filtroTexto=txt(
                "Na migração, quantidades acima do limite não entram automaticamente no estoque. O produto é importado com saldo 0 para evitar estoques artificiais do sistema antigo.",
                12,false);
        filtroTexto.setTextColor(Color.parseColor("#475467"));
        filtroTexto.setPadding(0,dp(5),0,dp(8));
        filtroEstoque.addView(filtroTexto);

        limiteEstoque=action("Limite automático: 200 unidades");
        TechCellUi.styleSecondary(this,limiteEstoque);
        limiteEstoque.setOnClickListener(v->alterarLimiteEstoque());
        filtroEstoque.addView(limiteEstoque,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        corrigirEstoqueAtual=action("🛠  Corrigir estoque atual acima do limite");
        TechCellUi.stylePrimary(this,corrigirEstoqueAtual,TechCellUi.ORANGE);
        corrigirEstoqueAtual.setOnClickListener(v->confirmarCorrecaoEstoqueAtual());
        LinearLayout.LayoutParams corrLp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(50));
        corrLp.setMargins(0,dp(8),0,0);
        filtroEstoque.addView(corrigirEstoqueAtual,corrLp);

        root.addView(filtroEstoque);

        importarCadastros=action("✅  Importar cadastros e estoque");
        TechCellUi.stylePrimary(this,importarCadastros,TechCellUi.GREEN);
        importarCadastros.setEnabled(false);
        importarCadastros.setAlpha(0.55f);
        importarCadastros.setOnClickListener(v->confirmarImportacaoCadastros());
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        ip.setMargins(0,dp(12),0,0);
        root.addView(importarCadastros,ip);

        TextView etapaCad=txt(
                "Esta etapa grava produtos, estoque dentro do limite de segurança, fornecedores e clientes. Estoques acima do limite entram como 0 e ficam registrados no resumo da migração.",
                11,false);
        etapaCad.setTextColor(TechCellUi.MUTED);
        etapaCad.setPadding(dp(4),dp(7),dp(4),0);
        root.addView(etapaCad);

        importarVendas=action("🧾  Importar histórico de vendas e caixa");
        TechCellUi.styleSecondary(this,importarVendas);
        importarVendas.setEnabled(false);
        importarVendas.setAlpha(0.55f);
        importarVendas.setOnClickListener(v->confirmarImportacaoHistorico());
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52));
        vp.setMargins(0,dp(12),0,0);
        root.addView(importarVendas,vp);

        TextView bloqueio=txt(
                "Depois dos cadastros, esta etapa reconcilia automaticamente as vendas antigas com os lançamentos de caixa. O estoque atual não é baixado novamente.",
                11,false);
        bloqueio.setTextColor(TechCellUi.MUTED);
        bloqueio.setPadding(dp(4),dp(7),dp(4),0);
        root.addView(bloqueio);

        LinearLayout perigo=TechCellUi.card(this);
        perigo.setBackground(TechCellUi.solid(this,Color.parseColor("#FFF4E5"),14));
        LinearLayout.LayoutParams perigoLp=TechCellUi.fullCardParams(this,16);
        perigo.setLayoutParams(perigoLp);

        TextView perigoTitulo=txt("BASE LIMPA PARA MIGRAÇÃO",12,true);
        perigoTitulo.setTextColor(Color.parseColor("#B54708"));
        perigo.addView(perigoTitulo);

        TextView perigoTexto=txt(
                "Use quando quiser apagar todos os cadastros, vendas e testes desta Alpha 42 e reconstruir a base somente com os dados reais do SMB. Configuração do aparelho, empresa e pareamento são preservados.",
                12,false);
        perigoTexto.setTextColor(Color.parseColor("#475467"));
        perigoTexto.setPadding(0,dp(6),0,dp(9));
        perigo.addView(perigoTexto);

        limparBase=action("🧹  Preparar base limpa para migração");
        TechCellUi.styleSecondary(this,limparBase);
        limparBase.setOnClickListener(v->confirmarLimpezaBase());
        perigo.addView(limparBase,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        root.addView(perigo);

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
        atualizarLimiteEstoque(db);
        GestaoDbHelper.SmbImportResumo r=db.resumoImportacaoSmb();
        if(r==null){
            limpar.setEnabled(false);
            limpar.setAlpha(0.55f);
            importarCadastros.setEnabled(false);
            importarCadastros.setAlpha(0.55f);
            importarVendas.setEnabled(false);
            importarVendas.setAlpha(0.55f);
            importarVendas.setText("🔒  Importe primeiro os cadastros e estoque");
            TechCellUi.styleSecondary(this,importarVendas);
            db.close();
            return;
        }
        limpar.setEnabled(true);
        limpar.setAlpha(1f);
        boolean historicoImportado="HISTORICO_IMPORTADO".equalsIgnoreCase(r.status);
        boolean cadastrosImportados=historicoImportado ||
                "CADASTROS_IMPORTADOS".equalsIgnoreCase(r.status);

        importarCadastros.setEnabled(!cadastrosImportados);
        importarCadastros.setAlpha(cadastrosImportados?0.60f:1f);
        importarCadastros.setText(cadastrosImportados?
                "✓  Cadastros e estoque já importados":
                "✅  Importar cadastros e estoque");

        importarVendas.setEnabled(cadastrosImportados && !historicoImportado);
        importarVendas.setAlpha(cadastrosImportados && !historicoImportado?1f:0.60f);
        if(cadastrosImportados && !historicoImportado){
            importarVendas.setText("🧾  Importar histórico de vendas e caixa");
            TechCellUi.stylePrimary(this,importarVendas,TechCellUi.GREEN);
        }else if(historicoImportado){
            importarVendas.setText("✓  Histórico de vendas e caixa já importado");
            TechCellUi.styleSecondary(this,importarVendas);
        }else{
            importarVendas.setText("🔒  Importe primeiro os cadastros e estoque");
            TechCellUi.styleSecondary(this,importarVendas);
        }

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
        card.addView(linha("Números de venda distintos no legado",r.vendas));
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
                cadastrosImportados?
                        "✓ Área de migração mantida para conferência; os dados importados já estão na base principal.":
                        "✓ Base principal preservada — esta prévia está somente na área de migração.",
                12,true);
        safe.setTextColor(TechCellUi.GREEN);
        safe.setPadding(0,dp(10),0,0);
        card.addView(safe);

        if(cadastrosImportados){
            TextView aplicado=txt(
                    "✓ Cadastros aplicados na base principal: "+
                            r.produtosImportados+" produtos • "+
                            r.fornecedoresImportados+" fornecedores • "+
                            r.clientesImportados+" clientes • "+
                            r.ignoradosImportacao+" ignorado(s)/duplicado(s).",
                    12,true);
            aplicado.setTextColor(TechCellUi.GREEN);
            aplicado.setPadding(0,dp(8),0,0);
            card.addView(aplicado);

            if(r.estoquesBloqueados>0){
                TextView filtro=txt(
                        "⚠ Filtro de estoque: "+
                                String.format(new Locale("pt","BR"),"%,d",r.estoquesBloqueados)+
                                " produto(s) acima de "+formatarQtd(r.limiteEstoqueAplicado)+
                                " foram importados com estoque 0.",
                        12,true);
                filtro.setTextColor(Color.parseColor("#B54708"));
                filtro.setPadding(0,dp(7),0,0);
                card.addView(filtro);
            }
        }

        if(historicoImportado){
            TextView hist=txt(
                    "✓ Histórico aplicado: "+
                            String.format(new Locale("pt","BR"),"%,d",r.vendasImportadas)+" vendas • "+
                            String.format(new Locale("pt","BR"),"%,d",r.itensVendaImportados)+" itens • "+
                            String.format(new Locale("pt","BR"),"%,d",r.caixaPreservado)+" lançamentos de caixa preservados.",
                    12,true);
            hist.setTextColor(TechCellUi.GREEN);
            hist.setPadding(0,dp(8),0,0);
            card.addView(hist);
        }

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


    private void atualizarLimiteEstoque(GestaoDbHelper db){
        if(limiteEstoque==null)return;
        double limite=db.getLimiteEstoqueImportacaoSmb();
        GestaoDbHelper.SyncContext ctx=db.getSyncContext();
        boolean master=ctx.configurado && "MASTER".equalsIgnoreCase(ctx.papelDispositivo);
        limiteEstoque.setText(
                "Limite automático: "+formatarQtd(limite)+" unidades"+
                        (master?"  •  alterar":"  •  somente Master"));
        limiteEstoque.setEnabled(master);
        limiteEstoque.setAlpha(master?1f:0.65f);
        if(corrigirEstoqueAtual!=null){
            corrigirEstoqueAtual.setEnabled(master);
            corrigirEstoqueAtual.setAlpha(master?1f:0.65f);
        }
    }


    private void confirmarCorrecaoEstoqueAtual(){
        GestaoDbHelper db=new GestaoDbHelper(this);
        try{
            GestaoDbHelper.SyncContext ctx=db.getSyncContext();
            if(!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)){
                Toast.makeText(this,
                        "Somente o aparelho Master pode corrigir o estoque atual.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            double limite=db.getLimiteEstoqueImportacaoSmb();
            int quantidade=db.countProdutosEstoqueAcima(limite);
            double soma=db.somaQuantidadeEstoqueAcima(limite);
            java.util.List<GestaoDbHelper.Produto> maiores=
                    db.listProdutosEstoqueAcima(limite,12);

            if(quantidade<=0){
                new AlertDialog.Builder(this)
                        .setTitle("Estoque dentro do limite")
                        .setMessage("Nenhum produto está acima de "+formatarQtd(limite)+" unidades.")
                        .setPositiveButton("OK",null)
                        .show();
                return;
            }

            StringBuilder msg=new StringBuilder();
            msg.append("Foram encontrados ")
                    .append(String.format(new Locale("pt","BR"),"%,d",quantidade))
                    .append(" produto(s) acima de ")
                    .append(formatarQtd(limite))
                    .append(" unidades.\n\n");
            msg.append("Soma das quantidades suspeitas: ")
                    .append(formatarQtd(soma)).append("\n\n");
            msg.append("Maiores saldos encontrados:\n");
            for(GestaoDbHelper.Produto p:maiores){
                msg.append("• ").append(p.nome)
                        .append(" — ").append(formatarQtd(p.estoque))
                        .append("\n");
            }
            if(quantidade>maiores.size())
                msg.append("• … e mais ")
                        .append(quantidade-maiores.size())
                        .append(" produto(s).\n");

            msg.append("\nAo confirmar, SOMENTE esses estoques acima do limite serão alterados para 0. ")
                    .append("Produto, preço, custo, código e histórico de vendas serão preservados. ")
                    .append("Antes da correção será criada uma cópia interna de segurança.");

            final double limiteFinal=limite;
            new AlertDialog.Builder(this)
                    .setTitle("Corrigir estoque acima de "+formatarQtd(limite)+"?")
                    .setMessage(msg.toString())
                    .setPositiveButton("Corrigir estoque",(d,w)->executarCorrecaoEstoqueAtual(limiteFinal))
                    .setNegativeButton("Cancelar",null)
                    .show();
        }finally{
            db.close();
        }
    }

    private void executarCorrecaoEstoqueAtual(double limite){
        setOcupado(true,"Criando cópia de segurança e corrigindo estoques…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "Sincronização ocupada. Tente novamente em alguns segundos."));
                return;
            }

            File copia=null;
            try{
                TechCellBackgroundSync.parar(getApplicationContext());

                GestaoDbHelper checkpoint=new GestaoDbHelper(getApplicationContext());
                try{
                    SQLiteDatabase sql=checkpoint.getWritableDatabase();
                    android.database.Cursor ck=sql.rawQuery("PRAGMA wal_checkpoint(FULL)",null);
                    try{ while(ck.moveToNext()){} }finally{ ck.close(); }
                }finally{
                    checkpoint.close();
                }

                copia=criarCopiaSegurancaInterna();

                GestaoDbHelper helper=new GestaoDbHelper(getApplicationContext());
                int corrigidos;
                try{
                    corrigidos=helper.corrigirEstoquesAcimaDoLimite(limite);
                }finally{
                    helper.close();
                }

                final File copiaFinal=copia;
                final int totalCorrigido=corrigidos;
                runOnUiThread(()->{
                    setOcupado(false,
                            "Correção concluída: "+totalCorrigido+" produto(s) ajustado(s).");
                    renderResumo();
                    new AlertDialog.Builder(this)
                            .setTitle("Estoque corrigido")
                            .setMessage(
                                    totalCorrigido+" produto(s) acima de "+formatarQtd(limite)+
                                    " unidades tiveram o estoque alterado para 0.\n\n"+
                                    "Produto, preços e histórico foram preservados.\n"+
                                    "Cópia interna de segurança: "+
                                    (copiaFinal==null?"não disponível":copiaFinal.getName()))
                            .setPositiveButton("OK",null)
                            .show();
                });
            }catch(Throwable e){
                final File copiaFinal=copia;
                runOnUiThread(()->{
                    setOcupado(false,"Correção não concluída: "+mensagem(e));
                    new AlertDialog.Builder(this)
                            .setTitle("Correção não concluída")
                            .setMessage(
                                    mensagem(e)+
                                    (copiaFinal==null?"":"\n\nA cópia interna de segurança foi criada antes da tentativa."))
                            .setPositiveButton("OK",null)
                            .show();
                });
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-Estoque-Correcao").start();
    }

    private void alterarLimiteEstoque(){
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SyncContext ctx=db.getSyncContext();
        double atual=db.getLimiteEstoqueImportacaoSmb();
        db.close();

        if(!ctx.configurado || !"MASTER".equalsIgnoreCase(ctx.papelDispositivo)){
            Toast.makeText(this,
                    "Somente o aparelho Master pode alterar esse limite.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        EditText campo=new EditText(this);
        campo.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        campo.setText(Math.abs(atual-Math.rint(atual))<0.000001
                ? String.valueOf((long)Math.rint(atual))
                : String.valueOf(atual));
        campo.setSelectAllOnFocus(true);
        int pad=dp(20);
        LinearLayout box=new LinearLayout(this);
        box.setPadding(pad,0,pad,0);
        box.addView(campo,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Limite automático de estoque")
                .setMessage(
                        "Produtos acima desse valor não terão o saldo importado automaticamente. "+
                        "O cadastro entra com estoque 0 para revisão.")
                .setView(box)
                .setPositiveButton("Salvar",(d,w)->{
                    try{
                        String s=campo.getText().toString().trim().replace(",",".");
                        double valor=Double.parseDouble(s);
                        GestaoDbHelper helper=new GestaoDbHelper(this);
                        try{
                            helper.setLimiteEstoqueImportacaoSmb(valor);
                            atualizarLimiteEstoque(helper);
                        }finally{
                            helper.close();
                        }
                        Toast.makeText(this,
                                "Limite alterado para "+formatarQtd(valor)+" unidades.",
                                Toast.LENGTH_LONG).show();
                    }catch(Throwable e){
                        Toast.makeText(this,mensagem(e),Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private String formatarQtd(double v){
        if(Math.abs(v-Math.rint(v))<0.000001)
            return String.format(new Locale("pt","BR"),"%,d",(long)Math.rint(v));
        return String.format(new Locale("pt","BR"),"%,.3f",v)
                .replaceAll("0+$","").replaceAll("[,.]$","");
    }

    private void confirmarImportacaoCadastros(){
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SmbImportResumo r=db.resumoImportacaoSmb();
        double limite=db.getLimiteEstoqueImportacaoSmb();
        db.close();
        if(r==null){
            Toast.makeText(this,"Carregue primeiro a pré-importação SMB.",Toast.LENGTH_LONG).show();
            return;
        }

        String msg=
                "Serão importados até:\n\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.produtos)+" produtos\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.fornecedores)+" fornecedores\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.clientes)+" clientes\n\n"+
                "Filtro de estoque: até "+formatarQtd(limite)+" unidades por produto entram automaticamente. "+
                "Acima disso, o produto entra com estoque 0 para revisão.\n\n"+
                "Produtos que já existirem por código ou código de barras serão preservados e ignorados.\n\n"+
                "Os "+String.format(new Locale("pt","BR"),"%,d",r.ajustesEstoque)+
                " ajustes antigos NÃO serão reaplicados.\n\n"+
                "Vendas e caixa ainda NÃO serão importados nesta etapa.";

        new AlertDialog.Builder(this)
                .setTitle("Importar cadastros e estoque?")
                .setMessage(msg)
                .setPositiveButton("Importar",(d,w)->executarImportacaoCadastros())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarImportacaoCadastros(){
        setOcupado(true,"Importando cadastros e estoque…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "Sincronização ocupada. Tente novamente em alguns segundos."));
                return;
            }

            try{
                TechCellBackgroundSync.parar(getApplicationContext());
                GestaoDbHelper db=new GestaoDbHelper(getApplicationContext());
                GestaoDbHelper.SmbImportResult r;
                try{
                    r=db.importarCadastrosSmb();
                }finally{
                    db.close();
                }

                runOnUiThread(()->{
                    setOcupado(false,
                            "Cadastros e estoque importados. O histórico de vendas e caixa já pode ser importado.");
                    renderResumo();
                    mostrarConclusaoCadastros(r);
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    setOcupado(false,"Importação não concluída: "+mensagem(e));
                    new AlertDialog.Builder(this)
                            .setTitle("Importação não concluída")
                            .setMessage(mensagem(e)+"\n\nA operação usa uma transação única; em caso de falha, as alterações desta etapa são revertidas.")
                            .setPositiveButton("OK",null)
                            .show();
                });
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-SMB-Import-Cadastros").start();
    }

    private void mostrarConclusaoCadastros(GestaoDbHelper.SmbImportResult r){
        String msg=
                "Produtos importados: "+String.format(new Locale("pt","BR"),"%,d",r.produtosImportados)+"\n"+
                "Fornecedores importados: "+String.format(new Locale("pt","BR"),"%,d",r.fornecedoresImportados)+"\n"+
                "Clientes importados: "+String.format(new Locale("pt","BR"),"%,d",r.clientesImportados)+"\n"+
                "Ignorados/duplicados: "+String.format(new Locale("pt","BR"),"%,d",r.ignorados)+"\n"+
                "Conflitos de produtos preservados: "+String.format(new Locale("pt","BR"),"%,d",r.produtosConflitantes)+"\n"+
                "Estoques bloqueados pelo filtro: "+String.format(new Locale("pt","BR"),"%,d",r.estoquesBloqueados)+"\n"+
                "Limite aplicado: "+formatarQtd(r.limiteEstoqueAplicado)+" unidades por produto\n\n"+
                "Quantidades dentro do limite foram importadas. Quantidades acima dele entraram como estoque 0.\n"+
                "Agora você pode importar o histórico de vendas e caixa sem alterar novamente o estoque.";

        new AlertDialog.Builder(this)
                .setTitle("Cadastros e estoque importados")
                .setMessage(msg)
                .setPositiveButton("Ver no sistema",null)
                .setCancelable(false)
                .show();
    }


    private void confirmarImportacaoHistorico(){
        GestaoDbHelper db=new GestaoDbHelper(this);
        GestaoDbHelper.SmbImportResumo r=db.resumoImportacaoSmb();
        db.close();
        if(r==null){
            Toast.makeText(this,"Carregue primeiro a pré-importação SMB.",Toast.LENGTH_LONG).show();
            return;
        }
        if(!"CADASTROS_IMPORTADOS".equalsIgnoreCase(r.status)){
            if("HISTORICO_IMPORTADO".equalsIgnoreCase(r.status)){
                Toast.makeText(this,"O histórico deste pacote já foi importado.",Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"Importe primeiro os cadastros e o estoque.",Toast.LENGTH_LONG).show();
            }
            return;
        }

        String msg=
                "O Tech Cell vai reconstruir automaticamente o histórico do SMB usando os itens de venda, a sessão de caixa, a data e o lançamento financeiro.\n\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.vendaItens)+" itens de venda serão reconciliados\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.caixa)+" lançamentos de caixa serão preservados para auditoria\n\n"+
                "O SMB reutilizou alguns números de venda em caixas/datas diferentes; por isso a quantidade final de vendas é calculada durante a reconciliação.\n\n"+
                "IMPORTANTE: o estoque atual NÃO será alterado nem baixado novamente.\n\n"+
                "O backup antigo não guarda o custo histórico em cada item vendido. Quando houver vínculo com o produto atual, o relatório de lucro usará o custo disponível no cadastro como referência.";

        new AlertDialog.Builder(this)
                .setTitle("Importar histórico de vendas e caixa?")
                .setMessage(msg)
                .setPositiveButton("Importar histórico",(d,w)->executarImportacaoHistorico())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarImportacaoHistorico(){
        setOcupado(true,"Reconciliando vendas, pagamentos e caixa do SMB…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "Sincronização ocupada. Tente novamente em alguns segundos."));
                return;
            }

            try{
                TechCellBackgroundSync.parar(getApplicationContext());
                GestaoDbHelper db=new GestaoDbHelper(getApplicationContext());
                GestaoDbHelper.SmbSalesImportResult r;
                try{
                    r=db.importarHistoricoSmb();
                }finally{
                    db.close();
                }

                runOnUiThread(()->{
                    setOcupado(false,
                            "Histórico SMB importado. Estoque atual preservado.");
                    renderResumo();
                    mostrarConclusaoHistorico(r);
                });
            }catch(Throwable e){
                runOnUiThread(()->{
                    setOcupado(false,"Histórico não importado: "+mensagem(e));
                    renderResumo();
                    new AlertDialog.Builder(this)
                            .setTitle("Histórico não importado")
                            .setMessage(mensagem(e)+"\n\nA etapa usa uma transação única. Se ocorrer falha, as vendas desta tentativa são revertidas.")
                            .setPositiveButton("OK",null)
                            .show();
                });
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-SMB-Import-Historico").start();
    }

    private void mostrarConclusaoHistorico(GestaoDbHelper.SmbSalesImportResult r){
        NumberFormat moeda=NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
        StringBuilder msg=new StringBuilder();
        msg.append("Vendas históricas importadas: ")
                .append(String.format(new Locale("pt","BR"),"%,d",r.vendasImportadas));
        msg.append("\nItens de venda importados: ")
                .append(String.format(new Locale("pt","BR"),"%,d",r.itensImportados));
        msg.append("\nFaturamento histórico reconstruído: ")
                .append(moeda.format(r.faturamentoImportado));
        msg.append("\nLançamentos de caixa preservados: ")
                .append(String.format(new Locale("pt","BR"),"%,d",r.caixaPreservado));

        if(r.vendasIgnoradas>0)
            msg.append("\nVendas já existentes/ignoradas: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.vendasIgnoradas));
        if(r.divergenciasReconciliadas>0)
            msg.append("\nVendas com diferença reconciliada entre itens/pagamento: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.divergenciasReconciliadas));
        if(r.itensSemProduto>0)
            msg.append("\nItens históricos sem vínculo com produto atual: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.itensSemProduto));
        if(r.caixaSemVenda>0)
            msg.append("\nLançamentos de venda no caixa sem item correspondente: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.caixaSemVenda));
        if(r.vendasSemValor>0)
            msg.append("\nVendas históricas com valor zero: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.vendasSemValor));
        if(r.vendasAPrazo>0)
            msg.append("\nVendas identificadas como a prazo: ")
                    .append(String.format(new Locale("pt","BR"),"%,d",r.vendasAPrazo));

        msg.append("\n\nO estoque atual não foi alterado.");
        msg.append("\nAs vendas antigas agora passam a participar dos relatórios por data.");
        msg.append("\n\nObservação: o custo/lucro histórico é uma referência quando o produto antigo pôde ser ligado ao cadastro atual; o SMB não gravava o custo em cada linha de venda.");

        new AlertDialog.Builder(this)
                .setTitle("Histórico de vendas importado")
                .setMessage(msg.toString())
                .setPositiveButton("Ver relatórios",null)
                .setCancelable(false)
                .show();
    }


    private void confirmarLimpezaBase(){
        new AlertDialog.Builder(this)
                .setTitle("Preparar base limpa para migração?")
                .setMessage(
                        "Esta operação vai apagar os dados operacionais desta Alpha 42:\n\n"+
                        "• produtos e estoque atuais\n"+
                        "• vendas e itens de venda\n"+
                        "• clientes e fornecedores\n"+
                        "• despesas e saídas\n"+
                        "• pré-importação e histórico SMB já carregado\n\n"+
                        "Serão preservados:\n"+
                        "• configuração do aparelho\n"+
                        "• UUIDs da empresa/filial/dispositivo\n"+
                        "• configuração da empresa\n"+
                        "• pareamento do Master\n\n"+
                        "Antes da limpeza será criada uma cópia interna de segurança da base atual.")
                .setPositiveButton("Continuar",(d,w)->confirmarLimpezaBaseFinal())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void confirmarLimpezaBaseFinal(){
        new AlertDialog.Builder(this)
                .setTitle("CONFIRMAR LIMPEZA")
                .setMessage(
                        "Depois desta etapa, carregue novamente o pacote SMB e importe primeiro cadastros/estoque e depois o histórico.\n\n"+
                        "Deseja realmente apagar os dados de teste agora?")
                .setPositiveButton("Sim, limpar dados de teste",(d,w)->executarLimpezaBase())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarLimpezaBase(){
        setOcupado(true,"Criando cópia de segurança e limpando dados de teste…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "Sincronização ocupada. Tente novamente em alguns segundos."));
                return;
            }

            File copia=null;
            try{
                TechCellBackgroundSync.parar(getApplicationContext());

                GestaoDbHelper checkpoint=new GestaoDbHelper(getApplicationContext());
                try{
                    SQLiteDatabase sql=checkpoint.getWritableDatabase();
                    android.database.Cursor ck=sql.rawQuery("PRAGMA wal_checkpoint(FULL)",null);
                    try{ while(ck.moveToNext()){} }finally{ ck.close(); }
                }finally{
                    checkpoint.close();
                }

                copia=criarCopiaSegurancaInterna();

                GestaoDbHelper helper=new GestaoDbHelper(getApplicationContext());
                GestaoDbHelper.ResetMigracaoResult r;
                try{
                    r=helper.resetarDadosOperacionaisParaMigracao();
                }finally{
                    helper.close();
                }

                final File copiaFinal=copia;
                runOnUiThread(()->{
                    setOcupado(false,
                            "Base preparada. Agora selecione novamente o pacote SMB.");
                    renderResumo();
                    mostrarConclusaoLimpezaBase(r,copiaFinal);
                });
            }catch(Throwable e){
                final File copiaFinal=copia;
                runOnUiThread(()->{
                    setOcupado(false,"Limpeza não concluída: "+mensagem(e));
                    new AlertDialog.Builder(this)
                            .setTitle("Limpeza não concluída")
                            .setMessage(
                                    mensagem(e)+
                                    (copiaFinal==null?"":"\n\nA cópia interna de segurança foi criada antes da tentativa."))
                            .setPositiveButton("OK",null)
                            .show();
                });
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-SMB-Reset-Base").start();
    }

    private File criarCopiaSegurancaInterna() throws Exception{
        File origem=getDatabasePath("gestao_techcell.db");
        if(origem==null||!origem.exists())
            throw new IllegalStateException("Banco de dados atual não foi encontrado.");

        File dir=new File(getFilesDir(),"migration_safety");
        if(!dir.exists()&&!dir.mkdirs())
            throw new IllegalStateException("Não foi possível criar a pasta de segurança.");

        File destino=new File(dir,
                "gestao_techcell-pre_migracao-"+System.currentTimeMillis()+".db");

        try(FileInputStream in=new FileInputStream(origem);
            FileOutputStream out=new FileOutputStream(destino)){
            byte[] buffer=new byte[64*1024];
            int n;
            while((n=in.read(buffer))>0)out.write(buffer,0,n);
            out.flush();
            out.getFD().sync();
        }

        if(destino.length()<=0)throw new IllegalStateException(
                "A cópia interna de segurança ficou vazia.");
        return destino;
    }

    private void mostrarConclusaoLimpezaBase(
            GestaoDbHelper.ResetMigracaoResult r,File copia){
        String msg=
                "Base operacional limpa com sucesso.\n\n"+
                "Removidos:\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.produtos)+" produtos\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.vendas)+" vendas\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.itensVenda)+" itens de venda\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.clientes)+" clientes\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.fornecedores)+" fornecedores\n"+
                "• "+String.format(new Locale("pt","BR"),"%,d",r.despesas)+" despesas/saídas\n\n"+
                "Configuração do aparelho, empresa e pareamento foram preservados.\n"+
                "Cópia interna de segurança: "+(copia==null?"não disponível":copia.getName())+"\n\n"+
                "Agora selecione novamente o pacote SMB e faça as duas etapas de importação.";

        new AlertDialog.Builder(this)
                .setTitle("Base pronta para migração")
                .setMessage(msg)
                .setPositiveButton("Selecionar pacote SMB",(d,w)->selecionarPacote())
                .setNegativeButton("Depois",null)
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
        if(limparBase!=null)limparBase.setEnabled(!ocupado);
        if(ocupado){
            if(limiteEstoque!=null){
                limiteEstoque.setEnabled(false);
                limiteEstoque.setAlpha(0.55f);
            }
            if(corrigirEstoqueAtual!=null){
                corrigirEstoqueAtual.setEnabled(false);
                corrigirEstoqueAtual.setAlpha(0.55f);
            }
        }else if(limiteEstoque!=null || corrigirEstoqueAtual!=null){
            GestaoDbHelper helperPermissao=new GestaoDbHelper(this);
            try{
                GestaoDbHelper.SyncContext ctx=helperPermissao.getSyncContext();
                boolean master=ctx.configurado && "MASTER".equalsIgnoreCase(ctx.papelDispositivo);
                if(limiteEstoque!=null){
                    limiteEstoque.setEnabled(master);
                    limiteEstoque.setAlpha(master?1f:0.65f);
                }
                if(corrigirEstoqueAtual!=null){
                    corrigirEstoqueAtual.setEnabled(master);
                    corrigirEstoqueAtual.setAlpha(master?1f:0.65f);
                }
            }finally{
                helperPermissao.close();
            }
        }
        if(importarCadastros!=null)importarCadastros.setEnabled(!ocupado);
        if(importarVendas!=null)importarVendas.setEnabled(!ocupado);
        selecionar.setAlpha(ocupado?0.55f:1f);
        limpar.setAlpha(ocupado?0.55f:1f);
        if(limparBase!=null)limparBase.setAlpha(ocupado?0.55f:1f);
        if(importarCadastros!=null)importarCadastros.setAlpha(ocupado?0.55f:1f);
        if(importarVendas!=null)importarVendas.setAlpha(ocupado?0.55f:1f);
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
