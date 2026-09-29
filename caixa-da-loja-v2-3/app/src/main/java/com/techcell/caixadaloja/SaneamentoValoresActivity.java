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

        TextView titulo=txt("Revisão do custo do estoque",25,true);
        titulo.setPadding(0,dp(14),0,dp(2));
        tela.addView(titulo);

        TextView sub=txt(
                "Saneamento temporário da base antiga • quantidade × custo unitário",
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
                "O relatório mostra produtos cujo CUSTO TOTAL DO ESTOQUE (quantidade × custo unitário) passa do teto. "+
                "Nada é corrigido sozinho. Marque somente os produtos cujo saldo de estoque antigo estiver errado.",
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

        aplicar=action("Zerar estoque dos selecionados");
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

        double custoTotal=p.estoque*p.custo;
        double vendaPotencial=p.estoque*p.precoVenda;

        TextView valores=txt(
                "Qtd. "+formatarQtd(p.estoque)+" "+(p.unidade==null?"":p.unidade)+
                        "   •   Custo unit. "+moeda.format(p.custo)+
                        "\nCUSTO TOTAL "+moeda.format(custoTotal)+
                        "   •   Venda potencial "+moeda.format(vendaPotencial),
                11,false);
        valores.setTextColor(TechCellUi.MUTED);
        valores.setPadding(0,dp(4),0,dp(6));
        card.addView(valores);

        CheckBox zerar=new CheckBox(this);
        zerar.setText("Zerar ESTOQUE deste produto");
        zerar.setTextSize(13);
        zerar.setTextColor(TechCellUi.RED);
        zerar.setPadding(0,dp(2),0,dp(2));
        zerar.setOnCheckedChangeListener((buttonView,isChecked)->{
            if(isChecked)selecoes.put(p.id,1);
            else selecoes.remove(p.id);
            atualizarResumo();
        });
        card.addView(zerar);

        Button zerarAgora=action("ZERAR ESTOQUE DESTE PRODUTO");
        TechCellUi.styleDanger(this,zerarAgora);
        zerarAgora.setEnabled(master);
        zerarAgora.setAlpha(master?1f:0.55f);
        zerarAgora.setOnClickListener(v->{
            selecoes.clear();
            selecoes.put(p.id,1);
            atualizarResumo();
            confirmarAplicacao();
        });
        LinearLayout.LayoutParams zp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(44));
        zp.setMargins(0,dp(6),0,0);
        card.addView(zerarAgora,zp);

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

    private void atualizarResumo(){
        int produtos=selecoes.size();

        resumo.setText(
                total+" produto(s) com CUSTO TOTAL acima de "+moeda.format(limite)+
                        "  •  exibidos "+Math.min(carregados,total)+"/"+total+
                        "\nSelecionados para zerar estoque: "+produtos);

        boolean pode=master && !ocupado && produtos>0;
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
                .setTitle("Alterar teto do custo total")
                .setMessage(
                        "O teto é aplicado sobre quantidade × custo unitário. "+
                        "Nada será zerado sem você marcar o produto.")
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
        if(selecoes.isEmpty())return;

        new AlertDialog.Builder(this)
                .setTitle("Zerar estoque dos selecionados?")
                .setMessage(
                        "Você selecionou "+selecoes.size()+" produto(s).\n\n"+
                                "Somente o ESTOQUE desses produtos será alterado para 0, desde que o custo total ainda esteja acima de "+
                                moeda.format(limite)+".\n\n"+
                                "O produto, custo unitário, preços e histórico de vendas serão preservados. "+
                                "Antes da alteração será criada uma cópia interna de segurança.")
                .setPositiveButton("Confirmar correção",(d,w)->executarAplicacao())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarAplicacao(){
        final java.util.ArrayList<Long> escolhidos=
                new java.util.ArrayList<>(selecoes.keySet());
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
                int corrigidos;
                try{
                    corrigidos=helper.zerarEstoquesSelecionadosPorValor(escolhidos,teto);
                }finally{
                    helper.close();
                }

                final File copiaFinal=copia;
                final int totalCorrigido=corrigidos;
                runOnUiThread(()->{
                    setOcupado(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Correção concluída")
                            .setMessage(
                                    totalCorrigido+" produto(s) tiveram o estoque zerado.\n\n"+
                                    "Custo unitário, preços, cadastro e histórico foram preservados.\n\n"+
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

    private String formatarQtd(double v){
        if(Math.abs(v-Math.rint(v))<0.000001)
            return String.format(new Locale("pt","BR"),"%,d",(long)Math.rint(v));
        return String.format(new Locale("pt","BR"),"%,.3f",v)
                .replaceAll("0+$","").replaceAll("[,.]$","");
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
