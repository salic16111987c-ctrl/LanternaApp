package com.techcell.caixadaloja;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class TechCellUi {
    public static final int NAVY=Color.parseColor("#0B1F3A"), BLUE=Color.parseColor("#1769C2"),
            GREEN=Color.parseColor("#07884B"), RED=Color.parseColor("#B42318"),
            TEXT=Color.parseColor("#172033"), MUTED=Color.parseColor("#667085"),
            BORDER=Color.parseColor("#DCE2EA"), BG=Color.parseColor("#F5F7FB"),
            PALE_BLUE=Color.parseColor("#EFF6FF"), PALE_GREEN=Color.parseColor("#ECFDF3");
    private TechCellUi(){}
    public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable solid(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static GradientDrawable bordered(Context c,int color,int radius,int stroke){
        GradientDrawable d=solid(c,color,radius); d.setStroke(dp(c,1),stroke); return d;
    }
    public static GradientDrawable cardBackground(Context c){return bordered(c,Color.WHITE,14,BORDER);}
    public static GradientDrawable pillBackground(Context c){return bordered(c,PALE_BLUE,12,Color.parseColor("#CFE0F5"));}
    public static void applyWindowChrome(Activity a){a.getWindow().setStatusBarColor(NAVY);a.getWindow().setNavigationBarColor(Color.WHITE);}
    public static void stylePrimary(Context c,Button b){stylePrimary(c,b,BLUE);}
    public static void stylePrimary(Context c,Button b,int color){
        b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(null,Typeface.BOLD);
        b.setBackground(solid(c,color,12));b.setMinHeight(0);b.setMinWidth(0);
    }
    public static void styleSecondary(Context c,Button b){
        b.setAllCaps(false);b.setTextColor(NAVY);b.setBackground(bordered(c,Color.WHITE,12,BORDER));b.setMinHeight(0);b.setMinWidth(0);
    }
    public static void styleDanger(Context c,Button b){
        b.setAllCaps(false);b.setTextColor(RED);b.setBackground(bordered(c,Color.WHITE,12,Color.parseColor("#F0C6C2")));b.setMinHeight(0);b.setMinWidth(0);
    }
    public static void styleFilter(Context c,Button b){styleSecondary(c,b);b.setTextSize(13);}
    public static void styleSearch(Context c,EditText e){
        e.setBackground(bordered(c,Color.WHITE,12,BORDER));e.setPadding(dp(c,14),0,dp(c,14),0);e.setTextColor(TEXT);e.setHintTextColor(Color.parseColor("#8793A5"));
    }
    public static LinearLayout card(Context c){
        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(cardBackground(c));
        card.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));card.setElevation(dp(c,1));return card;
    }
    public static LinearLayout.LayoutParams fullCardParams(Context c,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,dp(c,top),0,0);return p;
    }
}