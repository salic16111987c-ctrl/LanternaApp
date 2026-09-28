package com.techcell.caixadaloja;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupRestoreActivity extends Activity {
    private static final int REQ_CREATE_BACKUP = 7301;
    private static final int REQ_OPEN_BACKUP = 7302;
    private static final String DB_NAME = "gestao_techcell.db";
    private static final int DB_VERSION = 19;

    private final SimpleDateFormat nomeData =
            new SimpleDateFormat("yyyyMMdd_HHmmss", new Locale("pt","BR"));
    private final SimpleDateFormat telaData =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("pt","BR"));

    private TextView status;
    private Button criarBackup;
    private Button restaurarBackup;
    private File restoreTemp;

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

        TextView titulo=txt("Backup e Restauração",27,true);
        titulo.setPadding(0,dp(16),0,0);
        root.addView(titulo);

        TextView sub=txt(
                "Proteção do banco do Tech Cell • Alpha 41",
                13,false);
        sub.setTextColor(TechCellUi.MUTED);
        root.addView(sub);

        LinearLayout aviso=TechCellUi.card(this);
        aviso.setBackground(TechCellUi.solid(this,TechCellUi.PALE_BLUE,14));
        aviso.setLayoutParams(TechCellUi.fullCardParams(this,12));

        TextView at=txt("BACKUP DO TECH CELL",12,true);
        at.setTextColor(TechCellUi.NAVY);
        aviso.addView(at);

        TextView av=txt(
                "Este módulo salva e restaura o banco do próprio Tech Cell. " +
                "Ele não restaura backups .fbk do SMB. A importação SMB continua sendo uma migração separada.",
                13,false);
        av.setTextColor(Color.parseColor("#475467"));
        av.setPadding(0,dp(6),0,0);
        aviso.addView(av);
        root.addView(aviso);

        criarBackup=action("💾  Criar backup agora");
        TechCellUi.stylePrimary(this,criarBackup,TechCellUi.GREEN);
        criarBackup.setOnClickListener(v->escolherDestinoBackup());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        bp.setMargins(0,dp(14),0,0);
        root.addView(criarBackup,bp);

        restaurarBackup=action("↻  Restaurar um backup");
        TechCellUi.styleSecondary(this,restaurarBackup);
        restaurarBackup.setOnClickListener(v->escolherBackupParaRestaurar());
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        rp.setMargins(0,dp(8),0,0);
        root.addView(restaurarBackup,rp);

        LinearLayout protecao=TechCellUi.card(this);
        protecao.setLayoutParams(TechCellUi.fullCardParams(this,12));

        TextView pt=txt("Proteções da restauração",14,true);
        protecao.addView(pt);

        TextView pv=txt(
                "• valida o arquivo antes de alterar o banco\n" +
                "• confere integridade e versão do SQLite\n" +
                "• mostra produtos, vendas, clientes, fornecedores e saídas\n" +
                "• cria backup automático do banco atual antes de restaurar\n" +
                "• se a restauração falhar, tenta devolver o banco anterior",
                12,false);
        pv.setTextColor(TechCellUi.MUTED);
        pv.setPadding(0,dp(7),0,0);
        protecao.addView(pv);
        root.addView(protecao);

        status=txt("Pronto para criar ou restaurar um backup.",12,true);
        status.setTextColor(TechCellUi.NAVY);
        status.setGravity(Gravity.CENTER);
        status.setBackground(TechCellUi.pillBackground(this));
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.addView(status,TechCellUi.fullCardParams(this,12));

        setContentView(scroll);
    }

    private void escolherDestinoBackup(){
        if(TechCellSyncCoordinator.estaSincronizando()){
            Toast.makeText(this,
                    "Aguarde a sincronização terminar e tente novamente.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE,
                "TechCell_Backup_"+nomeData.format(new Date())+".techcellbackup");
        startActivityForResult(i,REQ_CREATE_BACKUP);
    }

    private void escolherBackupParaRestaurar(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_OPEN_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_CREATE_BACKUP)criarBackup(uri);
        else if(requestCode==REQ_OPEN_BACKUP)prepararRestauracao(uri);
    }

    private void criarBackup(Uri destino){
        setOcupado(true,"Criando backup…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "A sincronização está ocupada. Tente novamente em alguns segundos."));
                return;
            }
            try{
                TechCellBackgroundSync.parar(getApplicationContext());
                File banco=prepararBancoParaCopia();
                if(!banco.exists()||banco.length()<100){
                    throw new IllegalStateException("Banco local não encontrado.");
                }

                try(OutputStream out=getContentResolver().openOutputStream(destino,"w")){
                    if(out==null)throw new IllegalStateException("Não foi possível abrir o arquivo de destino.");
                    copiar(new FileInputStream(banco),out);
                }

                long tamanho=banco.length();
                runOnUiThread(()->{
                    setOcupado(false,
                            "Backup criado com sucesso • "+formatarBytes(tamanho)+
                                    " • "+telaData.format(new Date()));
                    Toast.makeText(this,"Backup criado com sucesso.",Toast.LENGTH_LONG).show();
                });
            }catch(Throwable e){
                runOnUiThread(()->setOcupado(false,
                        "Falha ao criar backup: "+mensagem(e)));
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-Backup").start();
    }

    private void prepararRestauracao(Uri origem){
        setOcupado(true,"Validando backup selecionado…");
        new Thread(()->{
            try{
                File temp=new File(getCacheDir(),"restore_"+System.currentTimeMillis()+".techcellbackup");
                try(InputStream in=getContentResolver().openInputStream(origem);
                    OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IllegalStateException("Não foi possível ler o arquivo.");
                    copiar(in,out);
                }

                BackupInfo info=validarBackup(temp);
                restoreTemp=temp;

                runOnUiThread(()->{
                    setOcupado(false,"Backup válido. Confira os dados antes de restaurar.");
                    confirmarRestauracao(info);
                });
            }catch(Throwable e){
                runOnUiThread(()->setOcupado(false,
                        "Arquivo não aceito: "+mensagem(e)));
            }
        },"TechCell-Backup-Check").start();
    }

    private void confirmarRestauracao(BackupInfo info){
        String msg=
                "Versão do banco: "+info.versao+"\n"+
                "Produtos: "+info.produtos+"\n"+
                "Vendas: "+info.vendas+"\n"+
                "Clientes: "+info.clientes+"\n"+
                "Fornecedores: "+info.fornecedores+"\n"+
                "Saídas/despesas: "+info.despesas+"\n"+
                "Tamanho: "+formatarBytes(info.tamanho)+"\n\n"+
                "O banco atual será salvo automaticamente antes da restauração.";

        new AlertDialog.Builder(this)
                .setTitle("Restaurar este backup?")
                .setMessage(msg)
                .setPositiveButton("Restaurar",(d,w)->executarRestauracao())
                .setNegativeButton("Cancelar",null)
                .show();
    }

    private void executarRestauracao(){
        final File selecionado=restoreTemp;
        if(selecionado==null||!selecionado.exists()){
            setOcupado(false,"O arquivo temporário não está mais disponível. Selecione o backup novamente.");
            return;
        }

        setOcupado(true,"Criando cópia de segurança e restaurando…");
        new Thread(()->{
            boolean lock=TechCellSyncCoordinator.iniciarManutencao();
            if(!lock){
                runOnUiThread(()->setOcupado(false,
                        "A sincronização está ocupada. Tente novamente em alguns segundos."));
                return;
            }

            File seguranca=null;
            try{
                TechCellBackgroundSync.parar(getApplicationContext());
                File atual=prepararBancoParaCopia();

                if(atual.exists()&&atual.length()>0){
                    File pasta=new File(getFilesDir(),"recovery");
                    if(!pasta.exists()&&!pasta.mkdirs())
                        throw new IllegalStateException("Não foi possível criar a pasta de segurança.");
                    seguranca=new File(pasta,
                            "pre_restore_"+nomeData.format(new Date())+".techcellbackup");
                    copiarArquivo(atual,seguranca);
                }

                GestaoDbHelper helper=new GestaoDbHelper(getApplicationContext());
                helper.close();

                apagarSidecars(atual);
                copiarArquivo(selecionado,atual);
                apagarSidecars(atual);

                // Abre o banco restaurado com o helper atual. Se for de versão anterior,
                // o próprio SQLiteOpenHelper executa as migrações existentes.
                GestaoDbHelper teste=new GestaoDbHelper(getApplicationContext());
                teste.getWritableDatabase();
                teste.count();
                teste.close();

                final File copiaSeguranca=seguranca;
                runOnUiThread(()->{
                    setOcupado(false,"Restauração concluída com sucesso.");
                    new AlertDialog.Builder(this)
                            .setTitle("Backup restaurado")
                            .setMessage("O banco foi restaurado com sucesso."+
                                    (copiaSeguranca==null?"":"\n\nUma cópia automática do banco anterior foi preservada internamente."))
                            .setPositiveButton("Reabrir Tech Cell",(d,w)->reabrirApp())
                            .setCancelable(false)
                            .show();
                });
            }catch(Throwable e){
                try{
                    File atual=getDatabasePath(DB_NAME);
                    if(seguranca!=null&&seguranca.exists()){
                        apagarSidecars(atual);
                        copiarArquivo(seguranca,atual);
                        apagarSidecars(atual);
                    }
                }catch(Throwable ignorado){}
                runOnUiThread(()->setOcupado(false,
                        "Restauração cancelada: "+mensagem(e)+
                                "\nO banco anterior foi preservado sempre que possível."));
            }finally{
                TechCellSyncCoordinator.finalizarManutencao();
                TechCellBackgroundSync.garantir(getApplicationContext());
            }
        },"TechCell-Restore").start();
    }

    private File prepararBancoParaCopia() throws Exception{
        GestaoDbHelper helper=new GestaoDbHelper(getApplicationContext());
        SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor c=db.rawQuery("PRAGMA wal_checkpoint(FULL)",null)){
            if(c.moveToFirst()){
                // Executar a consulta até o fim garante a conclusão do checkpoint.
                while(c.moveToNext()){}
            }
        }
        helper.close();
        File banco=getDatabasePath(DB_NAME);
        if(!banco.exists())throw new IllegalStateException("Banco local não encontrado.");
        return banco;
    }

    private BackupInfo validarBackup(File arquivo) throws Exception{
        if(arquivo==null||!arquivo.exists()||arquivo.length()<100)
            throw new IllegalArgumentException("Arquivo vazio ou incompleto.");

        byte[] cabecalho=new byte[16];
        try(InputStream in=new FileInputStream(arquivo)){
            int l=in.read(cabecalho);
            if(l<16)throw new IllegalArgumentException("Cabeçalho do arquivo incompleto.");
        }
        String assinatura=new String(cabecalho,0,15,"US-ASCII");
        if(!"SQLite format 3".equals(assinatura))
            throw new IllegalArgumentException("Este arquivo não é um backup SQLite do Tech Cell.");

        SQLiteDatabase db=SQLiteDatabase.openDatabase(
                arquivo.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            BackupInfo info=new BackupInfo();
            info.tamanho=arquivo.length();

            try(Cursor c=db.rawQuery("PRAGMA user_version",null)){
                if(c.moveToFirst())info.versao=c.getInt(0);
            }
            if(info.versao<=0)
                throw new IllegalArgumentException("Versão do banco não identificada.");
            if(info.versao>DB_VERSION)
                throw new IllegalArgumentException(
                        "Backup criado por uma versão de banco mais nova ("+info.versao+").");

            String integridade="";
            try(Cursor c=db.rawQuery("PRAGMA quick_check(1)",null)){
                if(c.moveToFirst())integridade=c.getString(0);
            }
            if(!"ok".equalsIgnoreCase(integridade))
                throw new IllegalArgumentException("Falha na verificação de integridade do banco.");

            if(!temTabela(db,"produtos")||!temTabela(db,"vendas"))
                throw new IllegalArgumentException("Estrutura obrigatória do Tech Cell não encontrada.");

            info.produtos=contar(db,"produtos");
            info.vendas=contar(db,"vendas");
            info.clientes=contarSeExiste(db,"clientes");
            info.fornecedores=contarSeExiste(db,"fornecedores");
            info.despesas=contarSeExiste(db,"despesas");
            return info;
        }finally{
            db.close();
        }
    }

    private boolean temTabela(SQLiteDatabase db,String tabela){
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                new String[]{tabela})){
            return c.moveToFirst();
        }
    }

    private long contar(SQLiteDatabase db,String tabela){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+tabela,null)){
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    private long contarSeExiste(SQLiteDatabase db,String tabela){
        return temTabela(db,tabela)?contar(db,tabela):0;
    }

    private void apagarSidecars(File banco){
        if(banco==null)return;
        try{ new File(banco.getAbsolutePath()+"-wal").delete(); }catch(Throwable ignored){}
        try{ new File(banco.getAbsolutePath()+"-shm").delete(); }catch(Throwable ignored){}
        try{ new File(banco.getAbsolutePath()+"-journal").delete(); }catch(Throwable ignored){}
    }

    private void copiarArquivo(File origem,File destino) throws Exception{
        File parent=destino.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())
            throw new IllegalStateException("Não foi possível preparar o destino.");
        try(InputStream in=new FileInputStream(origem);
            FileOutputStream out=new FileOutputStream(destino,false)){
            copiar(in,out);
            out.getFD().sync();
        }
    }

    private void copiar(InputStream entrada,OutputStream saida) throws Exception{
        BufferedInputStream in=new BufferedInputStream(entrada);
        BufferedOutputStream out=new BufferedOutputStream(saida);
        byte[] buffer=new byte[64*1024];
        int n;
        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        out.flush();
    }

    private void setOcupado(boolean ocupado,String texto){
        criarBackup.setEnabled(!ocupado);
        restaurarBackup.setEnabled(!ocupado);
        criarBackup.setAlpha(ocupado?0.55f:1f);
        restaurarBackup.setAlpha(ocupado?0.55f:1f);
        status.setText(texto);
    }

    private void reabrirApp(){
        Intent i=new Intent(this,TechCellHomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private String formatarBytes(long bytes){
        if(bytes<1024)return bytes+" B";
        double kb=bytes/1024.0;
        if(kb<1024)return String.format(new Locale("pt","BR"),"%.1f KB",kb);
        return String.format(new Locale("pt","BR"),"%.2f MB",kb/1024.0);
    }

    private String mensagem(Throwable e){
        if(e==null)return "erro desconhecido";
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m.trim();
    }

    private static class BackupInfo{
        int versao;
        long produtos;
        long vendas;
        long clientes;
        long fornecedores;
        long despesas;
        long tamanho;
    }
}
