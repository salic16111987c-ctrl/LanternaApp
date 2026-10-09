package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Entrada principal da Central da Plataforma. */
public class TechCellDeveloperHubActivity extends Activity {
    private int dp(int v){ return TechCellUi.dp(this,v); }
    private TextView txt(String s,int z,boolean b){ TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(TechCellUi.TEXT);if(b)t.setTypeface(null,android.graphics.Typeface.BOLD);return t; }
    private Button botao(String s){ Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b; }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);TechCellUi.applyWindowChrome(this);
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(TechCellUi.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(22),dp(18),dp(34));sv.addView(root);
        TextView t=txt("Central da Plataforma",28,true);root.addView(t);
        TextView d=txt("Criação de empresas, contratos, licenças e suporte do Desenvolvedor.",12,false);d.setTextColor(TechCellUi.MUTED);d.setPadding(0,dp(5),0,dp(14));root.addView(d);
        Button nova=botao("+ NOVA EMPRESA");TechCellUi.stylePrimary(this,nova,TechCellUi.GREEN);nova.setOnClickListener(v->startActivity(new Intent(this,TechCellDeveloperCompanyActivity.class)));root.addView(nova,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        Button painel=botao("EMPRESAS CADASTRADAS / SUPORTE");TechCellUi.stylePrimary(this,painel,TechCellUi.BLUE);painel.setOnClickListener(v->startActivity(new Intent(this,TechCellDeveloperActivity.class)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));p.setMargins(0,dp(10),0,0);root.addView(painel,p);
        setContentView(sv);
    }
}
