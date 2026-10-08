package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

public class GestaoActivity extends Activity {
    private final NumberFormat moeda=NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final Handler liveHandler=new Handler(Looper.getMainLooper());
    private boolean syncProdutosRodando;
    private boolean liveAtivo;
    private long versaoAcesso;
    private TextView totalVendidoView;
    private TextView lucroView;
    private TextView vendasView;
    private TextView custoView;
    private TextView recebimentosView;

    private final Runnable liveRefresh=new Runnable(){
        @Override public void run(){
            if(!liveAtivo)return;
            long atual=TechCellPermissionRealtime.versao();
            if(atual!=versaoAcesso){
                versaoAcesso=atual;
                if(TechCellAccess.controleAtivo(GestaoActivity.this)&&!TechCellAccess.temSessaoValida(GestaoActivity.this)){
                    finish();return;
                }
                render();
            }
            atualizarResumoAoVivo();
            liveHandler.postDelayed(this,1000);
        }
    };

    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView text(String v,int s,boolean b){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button moduleButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);TechCellUi.styleSecondary(this,b);return b;}

    private LinearLayout metric(String label,String value,int color,int tipo){
        LinearLayout c=TechCellUi.card(this);c.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView l=text(label,11,true);l.setTextColor(TechCellUi.MUTED);c.addView(l);
        TextView v=text(value,18,true);v.setTextColor(color);v.setPadding(0,dp(3),0,0);c.addView(v);
        if(tipo==1)totalVendidoView=v;else if(tipo==2)lucroView=v;else if(tipo==3)vendasView=v;else if(tipo==4)custoView=v;
        return c;
    }
    private void addMetricRow(LinearLayout root,LinearLayout a,LinearLayout b){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutParams(TechCellUi.fullCardParams(this,8));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);ap.setMargins(0,0,dp(4),0);row.addView(a,ap);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);bp.setMargins(dp(4),0,0,0);row.addView(b,bp);root.addView(row);
    }
    private void addModuleRow(LinearLayout root,Button a,Button b){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));rp.setMargins(0,dp(8),0,0);row.setLayoutParams(rp);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(56),1);ap.setMargins(0,0,dp(4),0);row.addView(a,ap);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(56),1);bp.setMargins(dp(4),0,0,0);row.addView(b,bp);root.addView(row);
    }
    private void addFull(LinearLayout root,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));p.setMargins(0,dp(8),0,0);root.addView(b,p);}
    private String curto(String id){if(id==null||id.trim().isEmpty())return "—";String x=id.replace("-","");return x.substring(0,Math.min(8,x.length())).toUpperCase(Locale.ROOT);}

    @Override protected void onCreate(Bundle b){super.onCreate(b);render();}
    @Override protected void onResume(){
        super.onResume();TechCellBackgroundSync.garantir(this);
        if(TechCellAccess.controleAtivo(this)&&!TechCellAccess.temSessaoValida(this)){finish();return;}
        versaoAcesso=TechCellPermissionRealtime.versao();
        render();liveAtivo=true;liveHandler.removeCallbacks(liveRefresh);liveHandler.postDelayed(liveRefresh,500);sincronizarTerminal();
    }
    @Override protected void onPause(){liveAtivo=false;liveHandler.removeCallbacks(liveRefresh);super.onPause();}
    @Override protected void onDestroy(){liveAtivo=false;liveHandler.removeCallbacksAndMessages(null);super.onDestroy();}

    private void atualizarTexto(TextView view,String novo){
        if(view==null||novo==null||novo.equals(String.valueOf(view.getText())))return;
        view.setText(novo);view.animate().cancel();view.setScaleX(1f);view.setScaleY(1f);
        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction(()->view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
    }
    private void atualizarResumoAoVivo(){
        if(!liveAtivo||!TechCellAccess.podeResumoDia(this)||totalVendidoView==null)return;
        GestaoDbHelper db=new GestaoDbHelper(this);
        try{
            GestaoDbHelper.ResumoVendas hoje=db.resumoHoje();
            atualizarTexto(totalVendidoView,moeda.format(hoje.total));
            atualizarTexto(vendasView,String.valueOf(hoje.quantidadeVendas));
            if(TechCellAccess.podeResumoFinanceiro(this)){
                atualizarTexto(lucroView,moeda.format(hoje.lucro));
                atualizarTexto(custoView,moeda.format(hoje.custo));
                atualizarTexto(recebimentosView,"Dinheiro "+moeda.format(hoje.dinheiro)+"   •   PIX "+moeda.format(hoje.pix)+"   •   Cartão "+moeda.format(hoje.cartao));
            }
        }finally{db.close();}
    }

    private void sincronizarTerminal(){
        if(syncProdutosRodando)return;GestaoDbHelper db=new GestaoDbHelper(this);GestaoDbHelper.SyncContext ctx=db.getSyncContext();db.close();
        if(!ctx.configurado||"MASTER".equalsIgnoreCase(ctx.papelDispositivo))return;
        if(ctx.masterHost==null||ctx.masterHost.trim().isEmpty()||ctx.masterAuthToken==null||ctx.masterAuthToken.trim().isEmpty())return;
        syncProdutosRodando=true;
        new Thread(()->{TechCellSyncCoordinator.Resultado r=TechCellSyncCoordinator.sincronizar(getApplicationContext());runOnUiThread(()->{syncProdutosRodando=false;if(!r.ocupado&&(r.vendasEnviadas>0||r.vendasRecebidas>0))atualizarResumoAoVivo();});},"TechCell-Gestao-Sync").start();
    }

    private void render(){
        TechCellUi.applyWindowChrome(this);if(TechCellAccess.controleAtivo(this)&&!TechCellAccess.temSessaoValida(this))return;
        TechCellAccess.Sessao sessao=TechCellAccess.sessao(this);boolean master=TechCellAccess.podeAdministrar(this);
        totalVendidoView=null;lucroView=null;vendasView=null;custoView=null;recebimentosView=null;
        GestaoDbHelper db=new GestaoDbHelper(this);GestaoDbHelper.ResumoVendas hoje=db.resumoHoje();
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button back=new Button(this);back.setText("←  Voltar");TechCellUi.styleSecondary(this,back);back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        TextView title=text("Gestão Tech Cell",27,true);title.setPadding(0,dp(16),0,0);root.addView(title);
        TextView sub=text("Perfil: "+TechCellAccess.perfilExibicao(sessao.perfil)+(sessao.nome==null||sessao.nome.isEmpty()?"":" • "+sessao.nome),13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);

        if(TechCellAccess.podeResumoDia(this)){
            TextView ht=text("Resumo de hoje",17,true);ht.setPadding(0,dp(16),0,0);root.addView(ht);
            addMetricRow(root,metric("TOTAL VENDIDO",moeda.format(hoje.total),TechCellUi.GREEN,1),metric("VENDAS",String.valueOf(hoje.quantidadeVendas),TechCellUi.BLUE,3));
            if(TechCellAccess.podeResumoFinanceiro(this)){
                addMetricRow(root,metric("LUCRO BRUTO",moeda.format(hoje.lucro),TechCellUi.GREEN,2),metric("CUSTO",moeda.format(hoje.custo),TechCellUi.TEXT,4));
                LinearLayout rec=TechCellUi.card(this);rec.setLayoutParams(TechCellUi.fullCardParams(this,8));TextView rt=text("Recebimentos",12,true);rt.setTextColor(TechCellUi.MUTED);rec.addView(rt);
                recebimentosView=text("Dinheiro "+moeda.format(hoje.dinheiro)+"   •   PIX "+moeda.format(hoje.pix)+"   •   Cartão "+moeda.format(hoje.cartao),13,true);recebimentosView.setPadding(0,dp(5),0,0);rec.addView(recebimentosView);root.addView(rec);
            }else{
                TextView dica=text("Resumo liberado para conferência do dia. Lucro, custo e financeiro completo permanecem protegidos.",11,false);dica.setTextColor(TechCellUi.MUTED);dica.setPadding(dp(4),dp(2),dp(4),0);root.addView(dica);
            }
        }

        TextView menu=text("Acesso rápido",17,true);menu.setPadding(0,dp(18),0,0);root.addView(menu);
        if(TechCellAccess.podeVender(this)){
            Button pdv=new Button(this);pdv.setText("🛒  ABRIR PDV / VENDER");pdv.setTextSize(16);pdv.setAllCaps(false);TechCellUi.stylePrimary(this,pdv,TechCellUi.GREEN);
            pdv.setOnClickListener(v->{try{startActivity(new Intent(this,PdvActivity.class));}catch(Throwable e){String d=e.getClass().getSimpleName();if(e.getMessage()!=null&&!e.getMessage().trim().isEmpty())d+="\n"+e.getMessage();new AlertDialog.Builder(this).setTitle("Não foi possível abrir o PDV").setMessage(d).setPositiveButton("OK",null).show();}});addFull(root,pdv);
        }

        java.util.ArrayList<Button> botoes=new java.util.ArrayList<>();
        if(TechCellAccess.podeProdutos(this)){Button b=moduleButton("📦  Produtos");b.setOnClickListener(v->startActivity(new Intent(this,ProdutosActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeEstoque(this)){Button b=moduleButton("🧮  Estoque");b.setOnClickListener(v->startActivity(new Intent(this,EstoqueActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeClientes(this)){Button b=moduleButton("👤  Clientes");b.setOnClickListener(v->startActivity(new Intent(this,ClientesActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeFornecedores(this)){Button b=moduleButton("🚚  Fornecedores");b.setOnClickListener(v->startActivity(new Intent(this,FornecedoresActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeFinanceiro(this)){Button b=moduleButton("💰  Financeiro");b.setOnClickListener(v->startActivity(new Intent(this,FinanceiroActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeRelatorios(this)){Button b=moduleButton("📊  Relatórios");b.setOnClickListener(v->startActivity(new Intent(this,RelatoriosActivity.class)));botoes.add(b);}
        if(TechCellAccess.podeHistorico(this)){Button b=moduleButton("🧾  Vendas");b.setOnClickListener(v->startActivity(new Intent(this,HistoricoVendasActivity.class)));botoes.add(b);}
        for(int i=0;i<botoes.size();i+=2){if(i+1<botoes.size())addModuleRow(root,botoes.get(i),botoes.get(i+1));else addFull(root,botoes.get(i));}

        if(master){
            Button fiscal=moduleButton("⚙  Fiscal");fiscal.setOnClickListener(v->startActivity(new Intent(this,ConfiguracoesFiscaisActivity.class)));
            Button rede=moduleButton("📡  Dispositivo / Rede");rede.setOnClickListener(v->startActivity(new Intent(this,ConfiguracaoDispositivoActivity.class)));addModuleRow(root,fiscal,rede);
            Button prep=moduleButton("✅  Homologação");prep.setOnClickListener(v->startActivity(new Intent(this,PreparacaoFiscalActivity.class)));
            Button smb=moduleButton("🗃️  Importação SMB");smb.setOnClickListener(v->startActivity(new Intent(this,ImportacaoSmbActivity.class)));addModuleRow(root,prep,smb);
            Button backup=moduleButton("💾  Backup / Restaurar");backup.setOnClickListener(v->startActivity(new Intent(this,BackupRestoreActivity.class)));addFull(root,backup);
            Button saneamento=moduleButton("⚠  Revisar valores suspeitos");TechCellUi.stylePrimary(this,saneamento,TechCellUi.ORANGE);saneamento.setOnClickListener(v->startActivity(new Intent(this,SaneamentoValoresActivity.class)));addFull(root,saneamento);
            Button usuarios=moduleButton("👥  Usuários / Acessos");TechCellUi.stylePrimary(this,usuarios,TechCellUi.BLUE);usuarios.setOnClickListener(v->startActivity(new Intent(this,TechCellUsuariosActivity.class)));addFull(root,usuarios);
        }

        GestaoDbHelper.SyncContext sync=db.getSyncContext();int pendentes=db.countSyncPendentes();
        if(master){
            String papel=sync.configurado?sync.papelDispositivo:"NÃO CONFIGURADO";
            TextView infra=text("Empresa "+curto(sync.empresaUuid)+"  •  Filial "+curto(sync.filialUuid)+"  •  Dispositivo "+curto(sync.dispositivoUuid)+"\n"+(sync.configurado?sync.nomeDispositivo+"  •  Função do aparelho: "+papel:"Função do aparelho: "+papel)+"\nRede local: "+("MASTER".equalsIgnoreCase(sync.papelDispositivo)?"Master ativo/configurado":(sync.masterHost==null||sync.masterHost.trim().isEmpty()?"Master não vinculado":"Master "+sync.masterHost))+"\n"+TechCellSyncCoordinator.resumo(this)+"\nNuvem: "+(sync.cloudAtiva?"ativa":"ainda não configurada")+"  •  Pendências: "+pendentes,11,true);
            infra.setTextColor(TechCellUi.NAVY);infra.setGravity(Gravity.CENTER);infra.setBackground(TechCellUi.pillBackground(this));infra.setPadding(dp(12),dp(10),dp(12),dp(10));root.addView(infra,TechCellUi.fullCardParams(this,16));
        }

        String acesso=master?"Master • acesso total":"Acesso personalizado • "+TechCellPermissions.resumo(sessao.permissoes);
        TextView safe=text(acesso+" • "+db.count()+" produtos",12,true);safe.setTextColor(TechCellUi.GREEN);safe.setGravity(Gravity.CENTER);safe.setBackground(TechCellUi.solid(this,TechCellUi.PALE_GREEN,12));safe.setPadding(dp(12),dp(10),dp(12),dp(10));root.addView(safe,TechCellUi.fullCardParams(this,8));
        db.close();setContentView(scroll);
    }
}
