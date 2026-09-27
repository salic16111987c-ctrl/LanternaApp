package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TechCellHomeActivity extends Activity {
    private int dp(int v){return TechCellUi.dp(this,v);}
    private TextView text(String v,int s,boolean b){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout modulo(String icon,String titulo,String detalhe,boolean destaque){
        LinearLayout card=TechCellUi.card(this);card.setLayoutParams(TechCellUi.fullCardParams(this,12));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=text(icon,27,false);top.addView(ic,new LinearLayout.LayoutParams(dp(46),ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(titulo,19,true));TextView sub=text(detalhe,12,false);sub.setTextColor(TechCellUi.MUTED);sub.setPadding(0,dp(3),0,0);texts.addView(sub);
        top.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.addView(top);
        Button abrir=new Button(this);abrir.setText("Abrir  →");abrir.setTextSize(14);if(destaque)TechCellUi.stylePrimary(this,abrir);else TechCellUi.styleSecondary(this,abrir);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));bp.setMargins(0,dp(12),0,0);card.addView(abrir,bp);card.setTag(abrir);return card;
    }
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);TechCellUi.applyWindowChrome(this);
        TechCellBackgroundSync.garantir(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(28),dp(18),dp(30));scroll.addView(root);
        TextView brand=text("TECH CELL ACS",29,true);brand.setGravity(Gravity.CENTER);root.addView(brand);
        TextView sub=text("Operação da loja em um só lugar",14,false);sub.setTextColor(TechCellUi.MUTED);sub.setGravity(Gravity.CENTER);sub.setPadding(0,dp(4),0,dp(12));root.addView(sub);
        LinearLayout gestao=modulo("🏪","Gestão Tech Cell","PDV, produtos, estoque, clientes, financeiro e relatórios.",true);
        Button abrirGestao=(Button)gestao.getTag();abrirGestao.setOnClickListener(v->startActivity(new Intent(this,GestaoActivity.class)));gestao.setOnClickListener(v->abrirGestao.performClick());root.addView(gestao);
        LinearLayout caixa=modulo("💵","Caixa da Loja","Caixa atual preservado para lançamentos e fechamento.",false);
        Button abrirCaixa=(Button)caixa.getTag();abrirCaixa.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));caixa.setOnClickListener(v->abrirCaixa.performClick());root.addView(caixa);
        TextView safe=text("Ambiente de teste da Gestão • o Caixa da Loja continua separado e preservado.",12,true);
        safe.setTextColor(TechCellUi.GREEN);safe.setGravity(Gravity.CENTER);safe.setBackground(TechCellUi.solid(this,TechCellUi.PALE_GREEN,12));safe.setPadding(dp(12),dp(11),dp(12),dp(11));
        root.addView(safe,TechCellUi.fullCardParams(this,16));setContentView(scroll);
    }

    @Override protected void onResume(){
        super.onResume();
        TechCellBackgroundSync.garantir(this);
    }
}