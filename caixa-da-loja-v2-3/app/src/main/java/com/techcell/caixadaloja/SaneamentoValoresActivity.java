package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SaneamentoValoresActivity extends Activity {
    private final NumberFormat moeda =
            NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private final LinkedHashMap<Long,Integer> selecoes = new LinkedHashMap<>();

    private GestaoDbHelper db;
    private LinearLayout lista;
    private TextView resumo;
    private Button carregarMais;
    private Button aplicar;
    private Button alterarTeto;

    private double limite;
    private int total;
    private int carregados;
    private boolean master;
    private boolean ocupado;
    private boolean primeiroResume=true;

    private int dp(int v){ return TechCellUi.dp(this,v); }

    private TextView txt(String s,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TechCellUi.TEXT);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        return t;
    }

    private Button action(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(0);
        return b;
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        db=new GestaoDbHelper(this);
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(primeiroResume){
            primeiroResume=false;
            return;
        }
        if(db!=null&&!ocupado)render();
    }

    private void render(){
        TechCellUi.applyWindowChrome(this);
        selecoes.clear();
        carregados=0;

        limite=db.getLimiteValorSaneamento();
        total=db.countProdutosValorSuspeito(limite);
        GestaoDbHelper.SyncContext ctx=db.getSyncContext();
        master=ctx.configurado && "MASTER".equalsIgnoreCase(ctx.papelDispositivo);

        LinearLayout tela=new LinearLayout(this);
        tela.setOrientation(LinearLayout.VERTICAL);
        tela.setPadding(dp(14),dp(12),dp(14),dp(12));
        tela.setBackgroundColor(TechCellUi.BG);

        Button voltar=action("←  Voltar");
        TechCellUi.styleSecondary(this,voltar);
        voltar.setOnClickListener(v->finish());
        tela.addView(voltar,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));

        TextView titulo=txt("Revisão de valores suspeitos",25,true);
        titulo.setPadding(0,dp(14),0,dp(2));
        tela.addView(titulo);

        TextView sub=txt(
                "Saneamento temporário da base antiga • custo e preços",
                12,false);
        sub.setTextColor(TechCellUi.MUTED);
        tela.addView(sub);

        LinearLayout aviso=TechCellUi.card(this);
        aviso.setBackground(TechCellUi.solid(this,Color.parseColor("#FFF4E5"),14));
        aviso.setLayoutParams(TechCellUi.fullCardParams(this,10));

        TextView at=txt("NADA É APAGADO AUTOMATICAMENTE",12,true);
        at.setTextColor(TechCellUi.ORANGE);
        aviso.addView(at);

        TextView av=txt(
                "O relatório mostra somente produtos com custo, preço à vista ou preço a prazo acima do teto. "+
                "Marque apenas o campo que estiver errado. Campos não marcados permanecem intactos.",
                12,false);
        av.setTextColor(Color.parseColor("#475467"));
        av.setPadding(0,dp(5),0,0);
        aviso.addView(av);
        tela.addView(aviso);

        alterarTeto=action("Teto atual: "+moeda.format(limite)+"  •  alterar");
        TechCellUi.styleSecondary(this,alterarTeto);
        alterarTeto.setEnabled(master);
        alterarTeto.setAlpha(master?1f:0.65f);
        alterarTeto.setOnClickListener(v->alterarTeto());
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(48));
        tp.setMargins(0,dp(10),0,0);
        tela.addView(alterarTeto,tp);

        resumo=txt("",12,true);
        resumo.setTextColor(TechCellUi.NAVY);
        resumo.setGravity(Gravity.CENTER);
        resumo.setBackground(TechCellUi.pillBackground(this));
        resumo.setPadding(dp(10),dp(8),dp(10),dp(8));
        tela.addView(resumo,TechCellUi.fullCardParams(this,8));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        lista=new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0,dp(4),0,dp(8));
        scroll.addView(lista);
        tela.addView(scroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        carregarMais=action("Carregar mais");
        TechCellUi.styleSecondary(this,carregarMais);
        carregarMais.setOnClickListener(v->carregarMais());
        tela.addView(carregarMais,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));

        aplicar=action("Corrigir campos selecionados");
        TechCellUi.stylePrimary(this,aplicar,TechCellUi.ORANGE);
        aplicar.setOnClickListener(v->confirmarAplicacao());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52));
        ap.setMargins(0,dp(8),0,0);
        tela.addView(aplicar,ap);

        if(!master){
            TextView somente=txt(
                    "Somente o aparelho Master pode alterar o teto ou aplicar correções.",
                    11,true);
            somente.setTextColor(TechCellUi.RED);
            somente.setGravity(Gravity.CENTER);
            somente.setPadding(0,dp(7),0,0);
            tela.addView(somente);
        }

        setContentView(tela);
        atualizarResumo();
        carregarMais();
    }

    private void carregarMais(){
        if(ocupado || carregados>=total)return;
        List<GestaoDbHelper.Produto> itens=
                db.listProdutosValorSuspeito(limite,carregados,100);

        for(GestaoDbHelper.Produto p:itens){
            lista.addView(cardProduto(p));
        }
        carregados+=itens.size();
        carregarMais.setVisibility(carregados<total?View.VISIBLE:View.GONE);
        atualizarResumo();
    }

    private View cardProduto(GestaoDbHelper.Produto p){
        LinearLayout card=TechCellUi.card(this);
        card.setLayoutParams(TechCellUi.fullCardParams(this,7));

        String cab=(p.codigo==null||p.codigo.trim().isEmpty())
                ? p.nome : p.codigo+" • "+p.nome;
        card.addView(txt(cab,15,true));

        TextView valores=txt(
                "Custo "+moeda.format(p.custo)+
                        "   •   Venda "+moeda.format(p.precoVenda)+
                        "   •   Prazo "+moeda.format(p.precoPrazo),
                11,false);
        valores.setTextColor(TechCellUi.MUTED);
        valores.setPadding(0,dp(4),0,dp(6));
        card.addView(valores);

        if(p.custo>limite){
            card.addView(checkCampo(
                    p,GestaoDbHelper.VALOR_CAMPO_CUSTO,
                    "Zerar CUSTO  •  "+moeda.format(p.custo)));
        }
        if(p.precoVenda>limite){
            card.addView(checkCampo(
                    p,GestaoDbHelper.VALOR_CAMPO_VENDA,
                    "Zerar VENDA À VISTA  •  "+moeda.format(p.precoVenda)));
        }
        if(p.precoPrazo>limite){
            card.addView(checkCampo(
                    p,GestaoDbHelper.VALOR_CAMPO_PRAZO,
                    "Zerar VENDA A PRAZO  •  "+moeda.format(p.precoPrazo)));
        }

        Button editar=action("Editar produto sem zerar");
        TechCellUi.styleSecondary(this,editar);
        editar.setOnClickListener(v->{
            Intent i=new Intent(this,ProdutosActivity.class);
            i.putExtra(ProdutosActivity.EXTRA_PRODUTO_ID,p.id);
            i.putExtra(ProdutosActivity.EXTRA_FECHAR_APOS_SALVAR,true);
            startActivity(i);
        });
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(42));
        ep.setMargins(0,dp(7),0,0);
        card.addView(editar,ep);

        return card;
    }

    private CheckBox checkCampo(
            GestaoDbHelper.Produto p,int bit,String texto){
        CheckBox cb=new CheckBox(this);
        cb.setText(texto);
        cb.setTextSize(13);
        cb.setTextColor(TechCellUi.RED);
        cb.setPadding(0,dp(2),0,dp(2));
        cb.setOnCheckedChangeListener((buttonView,isChecked)->{
            int atual=selecoes.containsKey(p.id)?selecoes.get(p.id):0;
            if(isChecked)atual|=bit;
            else atual&=~bit;
            if(atual==0)selecoes.remove(p.id);
            else selecoes.put(p.id,atual);
            atualizarResumo();
        });
        return cb;
    }

    private void atualizarResumo(){
        int produtos=selecoes.size();
        int campos=0;
        for(Integer m:selecoes.values()){
            if(m!=null)campos+=Integer.bitCount(m);
        }

        resumo.setText(
                total+" produto(s) acima de "+moeda.format(limite)+
                        "  •  exibidos "+Math.min(carregados,total)+"/"+total+
                        "\nSelecionados: "+produtos+" produto(s) • "+campos+" campo(s)");

        boolean pode=master && !ocupado && campos>0;
        aplicar.setEnabled(pode);
        aplicar.setAlpha(pode?1f:0.55f);
    }

    private void alterarTeto(){
        if(!master){
            Toast.makeText(this,
                    "Somente o Master pode alterar o teto.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        EditText campo=new EditText(this);
        campo.setInputType(
                InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        campo.setText(Math.abs(limite-Math.rint(limite))<0.000001
                ?String.valueOf((long)Math.rint(limite))
                :String.valueOf(limite));
        campo.setSelectAllOnFocus(true);

        LinearLayout box=new LinearLayout(this);
        box.setPadding(dp(20),0,dp(20),0);
        box.addView(campo,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Alterar teto do relatório")
                .setMessage(
                        "O teto apenas define quais valores serão mostrados como suspeitos. "+
                        "Nada será zerado sem você marcar o campo.")
                .setView(box)
                .setPositiveButton("Aplicar teto",(d,w)->{
                    try{
                        double v=Double.parseDouble(
                                campo.getText().toString().trim().replace(",","."));
                        db.setLimiteValorSaneamento(v);
                        render();
                    }catch(Throwable e){
                        Toast.makeText(this,mensagem(e),Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void confirmarAplicacao(){
        int campos=0;
        for(Integer m:selecoes.values())
            if(m!=null)campos+=Integer.bitCount(m);
        if(campos<=0)return;

        final int camposFinal=campos;
        new AlertDialog.Builder(this)
                .setTitle("Corrigir valores selecionados?")
                .setMessage(
                        "Você selecionou "+selecoes.size()+" produto(s) e "+
                                camposFinal+" campo(s).\n\n"+
                                "Somente os campos marcados e que ainda estiverem acima de "+
                                moeda.format(limite)+" serão alterados para R$ 0,00.\n\n"+
                                "Antes da alteração será criada uma cópia interna de segurança.")
                .setPositiveButton("Confirmar correção",(d,w)->executarAplicacao())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarAplicacao(){
        final LinkedHashMap<Long,Integer> escolhidos=
                new LinkedHashMap<>(selecoes);
        final double teto=limite;
        setOcupado(true);

        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->{
                    setOcupado(false);
                    Toast.makeText(this,
                            "Sincronização ocupada. Tente novamente em alguns segundos.",
                            Toast.LENGTH_LONG).show();
                });
                return;
            }

            File copia=null;
            try{
                TechCellBackgroundSync.parar(getApplicationContext());

                GestaoDbHelper checkpoint=
                        new GestaoDbHelper(getApplicationContext());
                try{
                    SQLiteDatabase sql=checkpoint.getWritableDatabase();
                    android.database.Cursor ck=
                            sql.rawQuery("PRAGMA wal_checkpoint(FULL)",null);
                    try{while(ck.moveToNext()){}}
                    finally{ck.close();}
                }finally{
                    checkpoint.close();
                }

                copia=criarCopiaSegurancaInterna();

                GestaoDbHelper helper=
                        new GestaoDbHelper(getApplicationContext());
                GestaoDbHelper.CorrecaoValoresResult r;
                try{
                    r=helper.corrigirValoresSelecionados(escolhidos,teto);
                }finally{
                    helper.close();
                }

                final File copiaFinal=copia;
                runOnUiThread(()->{
                    setOcupado(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Correção concluída")
                            .setMessage(
                                    r.produtos+" produto(s) corrigido(s)\n"+
                                    r.campos+" campo(s) alterado(s) para R$ 0,00\n\n"+
                                    "Cópia interna de segurança: "+
                                    (copiaFinal==null?"não disponível":copiaFinal.getName()))
                            .setPositiveButton("Ver relatório",(d,w)->render())
                            .setCancelable(false)
                            .show();
                });
            }catch(Throwable e){
                final File copiaFinal=copia;
                runOnUiThread(()->{
                    setOcupado(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Correção não concluída")
                            .setMessage(
                                    mensagem(e)+
                                    (copiaFinal==null?"":
                                            "\n\nA cópia interna de segurança foi criada antes da tentativa."))
                            .setPositiveButton("OK",null)
                            .show();
                });
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-Saneamento-Valores").start();
    }

    private File criarCopiaSegurancaInterna() throws Exception{
        File origem=getDatabasePath("gestao_techcell.db");
        if(origem==null||!origem.exists())
            throw new IllegalStateException("Banco de dados atual não foi encontrado.");

        File dir=new File(getFilesDir(),"migration_safety");
        if(!dir.exists()&&!dir.mkdirs())
            throw new IllegalStateException("Não foi possível criar a pasta de segurança.");

        File destino=new File(
                dir,"gestao_techcell-pre_valores-"+System.currentTimeMillis()+".db");

        try(FileInputStream in=new FileInputStream(origem);
            FileOutputStream out=new FileOutputStream(destino)){
            byte[] buffer=new byte[64*1024];
            int n;
            while((n=in.read(buffer))>0)out.write(buffer,0,n);
            out.flush();
            out.getFD().sync();
        }

        if(destino.length()<=0)
            throw new IllegalStateException("A cópia interna de segurança ficou vazia.");
        return destino;
    }

    private void setOcupado(boolean valor){
        ocupado=valor;
        if(alterarTeto!=null){
            alterarTeto.setEnabled(master&&!valor);
            alterarTeto.setAlpha(master&&!valor?1f:0.55f);
        }
        if(carregarMais!=null)carregarMais.setEnabled(!valor);
        atualizarResumo();
    }

    private String mensagem(Throwable e){
        if(e==null)return "erro desconhecido";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()
                ?e.getClass().getSimpleName():m.trim();
    }

    @Override protected void onDestroy(){
        if(db!=null)db.close();
        super.onDestroy();
    }
}
