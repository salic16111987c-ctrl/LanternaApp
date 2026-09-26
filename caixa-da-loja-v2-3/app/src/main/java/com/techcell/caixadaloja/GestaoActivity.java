package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.Locale;

public class GestaoActivity extends Activity {
    private final NumberFormat moeda=NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView text(String v,int s,boolean b){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button moduleButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(14);TechCellUi.styleSecondary(this,b);return b;}
    private LinearLayout metric(String label,String value,int color){
        LinearLayout c=TechCellUi.card(this);c.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView l=text(label,11,true);l.setTextColor(TechCellUi.MUTED);c.addView(l);TextView v=text(value,18,true);v.setTextColor(color);v.setPadding(0,dp(3),0,0);c.addView(v);return c;
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
    @Override protected void onCreate(Bundle b){super.onCreate(b);render();}
    @Override protected void onResume(){super.onResume();render();}
    private void render(){
        TechCellUi.applyWindowChrome(this);GestaoDbHelper db=new GestaoDbHelper(this);GestaoDbHelper.ResumoVendas hoje=db.resumoHoje();
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(30));scroll.addView(root);
        Button back=new Button(this);back.setText("←  Voltar");TechCellUi.styleSecondary(this,back);back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        TextView title=text("Gestão Tech Cell",27,true);title.setPadding(0,dp(16),0,0);root.addView(title);TextView sub=text("Painel principal • Alpha 29",13,false);sub.setTextColor(TechCellUi.MUTED);root.addView(sub);
        TextView ht=text("Hoje",17,true);ht.setPadding(0,dp(16),0,0);root.addView(ht);
        addMetricRow(root,metric("TOTAL VENDIDO",moeda.format(hoje.total),TechCellUi.GREEN),metric("LUCRO BRUTO",moeda.format(hoje.lucro),TechCellUi.GREEN));
        addMetricRow(root,metric("VENDAS",String.valueOf(hoje.quantidadeVendas),TechCellUi.BLUE),metric("CUSTO",moeda.format(hoje.custo),TechCellUi.TEXT));
        LinearLayout rec=TechCellUi.card(this);rec.setLayoutParams(TechCellUi.fullCardParams(this,8));TextView rt=text("Recebimentos",12,true);rt.setTextColor(TechCellUi.MUTED);rec.addView(rt);
        TextView rv=text("Dinheiro "+moeda.format(hoje.dinheiro)+"   •   PIX "+moeda.format(hoje.pix)+"   •   Cartão "+moeda.format(hoje.cartao),13,true);rv.setPadding(0,dp(5),0,0);rec.addView(rv);root.addView(rec);
        TextView menu=text("Acesso rápido",17,true);menu.setPadding(0,dp(18),0,0);root.addView(menu);
        Button pdv=new Button(this);pdv.setText("🛒  ABRIR PDV / VENDER");pdv.setTextSize(16);TechCellUi.stylePrimary(this,pdv,TechCellUi.GREEN);
        pdv.setOnClickListener(v->{try{startActivity(new Intent(this,PdvActivity.class));}catch(Throwable e){String d=e.getClass().getSimpleName();if(e.getMessage()!=null&&!e.getMessage().trim().isEmpty())d+="\n"+e.getMessage();new AlertDialog.Builder(this).setTitle("Não foi possível abrir o PDV").setMessage(d).setPositiveButton("OK",null).show();}});
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(60));pp.setMargins(0,dp(8),0,0);root.addView(pdv,pp);
        Button prod=moduleButton("📦  Produtos");prod.setOnClickListener(v->startActivity(new Intent(this,ProdutosActivity.class)));Button est=moduleButton("🧮  Estoque");est.setOnClickListener(v->startActivity(new Intent(this,EstoqueActivity.class)));addModuleRow(root,prod,est);
        Button fin=moduleButton("💰  Financeiro");fin.setOnClickListener(v->startActivity(new Intent(this,FinanceiroActivity.class)));Button rel=moduleButton("📊  Relatórios");rel.setOnClickListener(v->startActivity(new Intent(this,RelatoriosActivity.class)));addModuleRow(root,fin,rel);
        Button cli=moduleButton("👤  Clientes");cli.setOnClickListener(v->startActivity(new Intent(this,ClientesActivity.class)));Button forn=moduleButton("🚚  Fornecedores");forn.setOnClickListener(v->startActivity(new Intent(this,FornecedoresActivity.class)));addModuleRow(root,cli,forn);
        Button hist=moduleButton("🧾  Vendas");hist.setOnClickListener(v->startActivity(new Intent(this,HistoricoVendasActivity.class)));Button fiscal=moduleButton("⚙  Fiscal");fiscal.setOnClickListener(v->startActivity(new Intent(this,ConfiguracoesFiscaisActivity.class)));addModuleRow(root,hist,fiscal);
        Button prep=moduleButton("✅  Homologação");prep.setOnClickListener(v->startActivity(new Intent(this,PreparacaoFiscalActivity.class)));Button smb=moduleButton("🗃️  Importação SMB");smb.setOnClickListener(v->Toast.makeText(this,"Importação bloqueada até conferirmos os registros do SMB.",Toast.LENGTH_LONG).show());addModuleRow(root,prep,smb);
        TextView safe=text("Base local preservada • "+db.count()+" produtos cadastrados",12,true);safe.setTextColor(TechCellUi.GREEN);safe.setGravity(Gravity.CENTER);safe.setBackground(TechCellUi.solid(this,TechCellUi.PALE_GREEN,12));safe.setPadding(dp(12),dp(10),dp(12),dp(10));root.addView(safe,TechCellUi.fullCardParams(this,16));
        setContentView(scroll);
    }
}