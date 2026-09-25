package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class GestaoDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "gestao_techcell.db";
    private static final int DB_VERSION = 5;

    public static class Produto {
        public long id;
        public String codigo = "";
        public String nome = "";
        public String codigoBarras = "";
        public String grupo = "";
        public String fornecedor = "";
        public String unidade = "";
        public String fabricante = "";
        public double custo;
        public double precoVenda;
        public double precoPrazo;
        public double estoque;
        public double estoqueMinimo;

        public double lucroUnitario() { return precoVenda - custo; }
        public double lucroPercentualSobreCusto() {
            return custo > 0 ? ((precoVenda - custo) / custo) * 100.0 : 0.0;
        }
        public double valorEstoqueCusto() { return custo * estoque; }
        public double valorEstoqueVenda() { return precoVenda * estoque; }
        public double lucroPotencial() { return (precoVenda - custo) * estoque; }
        public boolean ehServico() {
            return unidade != null && unidade.trim().equalsIgnoreCase("SERVIÇO");
        }
    }

    public static class VendaItem {
        public Produto produto;
        public double quantidade;
        public double precoUnitario;

        public double total() { return precoUnitario * quantidade; }
        public double custoTotal() { return produto.custo * quantidade; }
        public double lucro() { return total() - custoTotal(); }
    }

    public static class Pagamento {
        public String forma = "";
        public double dinheiro;
        public double pix;
        public double cartao;
        public double recebido;
        public double troco;
    }

    public static class ResumoVendas {
        public int quantidadeVendas;
        public double total;
        public double custo;
        public double lucro;
        public double dinheiro;
        public double pix;
        public double cartao;
        public double desconto;
    }

    public static class VendaItemRegistro {
        public String codigo = "";
        public String nome = "";
        public String unidade = "";
        public double quantidade;
        public double precoUnitario;
        public double total;
        public double descontoRateio;
        public double totalLiquido;
    }

    public static class VendaDetalhe {
        public long id;
        public long dataMillis;
        public double subtotal;
        public double desconto;
        public double total;
        public String formaPagamento = "";
        public double dinheiro;
        public double pix;
        public double cartao;
        public double recebido;
        public double troco;
        public String consumidorDocumento = "";
        public String notaStatus = "NAO_EMITIDA";
        public String notaNumero = "";
        public String notaChave = "";
        public String notaProtocolo = "";
        public String notaXml = "";
        public final List<VendaItemRegistro> itens = new ArrayList<>();
    }

    public GestaoDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        criarProdutos(db);
        criarVendas(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Bancos anteriores ao PDV não tinham as tabelas de venda.
            // Cria direto no formato atual e encerra para evitar ALTER duplicado.
            criarVendas(db);
            return;
        }

        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE vendas ADD COLUMN subtotal REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE vendas ADD COLUMN desconto REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE vendas ADD COLUMN desconto_tipo TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN desconto_referencia REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE venda_itens ADD COLUMN desconto_rateio REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE venda_itens ADD COLUMN total_liquido REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE venda_itens ADD COLUMN lucro_liquido REAL NOT NULL DEFAULT 0");
            db.execSQL("UPDATE vendas SET subtotal=total WHERE subtotal=0");
            db.execSQL("UPDATE venda_itens SET total_liquido=total, lucro_liquido=lucro WHERE total_liquido=0");
        }

        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE vendas ADD COLUMN consumidor_documento TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_status TEXT NOT NULL DEFAULT 'NAO_EMITIDA'");
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_numero TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_chave TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_protocolo TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_xml TEXT NOT NULL DEFAULT ''");
        }

        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE vendas ADD COLUMN nota_tipo TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_nome TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_documento TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_ie TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_logradouro TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_numero TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_complemento TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_bairro TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_cep TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_municipio TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_uf TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_telefone TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE vendas ADD COLUMN dest_email TEXT NOT NULL DEFAULT ''");
        }
    }

    private void criarProdutos(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS produtos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "codigo TEXT," +
                "nome TEXT NOT NULL," +
                "codigo_barras TEXT," +
                "grupo TEXT," +
                "fornecedor TEXT," +
                "unidade TEXT," +
                "fabricante TEXT," +
                "custo REAL NOT NULL DEFAULT 0," +
                "preco_venda REAL NOT NULL DEFAULT 0," +
                "preco_prazo REAL NOT NULL DEFAULT 0," +
                "estoque REAL NOT NULL DEFAULT 0," +
                "estoque_minimo REAL NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_produtos_nome ON produtos(nome)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_produtos_codigo ON produtos(codigo)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_produtos_barras ON produtos(codigo_barras)");
    }

    private void criarVendas(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vendas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "data_millis INTEGER NOT NULL," +
                "subtotal REAL NOT NULL DEFAULT 0," +
                "desconto REAL NOT NULL DEFAULT 0," +
                "desconto_tipo TEXT NOT NULL DEFAULT ''," +
                "desconto_referencia REAL NOT NULL DEFAULT 0," +
                "total REAL NOT NULL," +
                "custo_total REAL NOT NULL," +
                "lucro_bruto REAL NOT NULL," +
                "forma_pagamento TEXT NOT NULL," +
                "dinheiro REAL NOT NULL DEFAULT 0," +
                "pix REAL NOT NULL DEFAULT 0," +
                "cartao REAL NOT NULL DEFAULT 0," +
                "recebido REAL NOT NULL DEFAULT 0," +
                "troco REAL NOT NULL DEFAULT 0," +
                "consumidor_documento TEXT NOT NULL DEFAULT ''," +
                "nota_status TEXT NOT NULL DEFAULT 'NAO_EMITIDA'," +
                "nota_numero TEXT NOT NULL DEFAULT ''," +
                "nota_chave TEXT NOT NULL DEFAULT ''," +
                "nota_protocolo TEXT NOT NULL DEFAULT ''," +
                "nota_xml TEXT NOT NULL DEFAULT ''," +
                "nota_tipo TEXT NOT NULL DEFAULT ''," +
                "dest_nome TEXT NOT NULL DEFAULT ''," +
                "dest_documento TEXT NOT NULL DEFAULT ''," +
                "dest_ie TEXT NOT NULL DEFAULT ''," +
                "dest_logradouro TEXT NOT NULL DEFAULT ''," +
                "dest_numero TEXT NOT NULL DEFAULT ''," +
                "dest_complemento TEXT NOT NULL DEFAULT ''," +
                "dest_bairro TEXT NOT NULL DEFAULT ''," +
                "dest_cep TEXT NOT NULL DEFAULT ''," +
                "dest_municipio TEXT NOT NULL DEFAULT ''," +
                "dest_uf TEXT NOT NULL DEFAULT ''," +
                "dest_telefone TEXT NOT NULL DEFAULT ''," +
                "dest_email TEXT NOT NULL DEFAULT ''" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vendas_data ON vendas(data_millis)");

        db.execSQL("CREATE TABLE IF NOT EXISTS venda_itens (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "venda_id INTEGER NOT NULL," +
                "produto_id INTEGER NOT NULL," +
                "codigo TEXT," +
                "nome TEXT NOT NULL," +
                "unidade TEXT," +
                "quantidade REAL NOT NULL," +
                "preco_unitario REAL NOT NULL," +
                "custo_unitario REAL NOT NULL," +
                "total REAL NOT NULL," +
                "desconto_rateio REAL NOT NULL DEFAULT 0," +
                "total_liquido REAL NOT NULL DEFAULT 0," +
                "custo_total REAL NOT NULL," +
                "lucro REAL NOT NULL," +
                "lucro_liquido REAL NOT NULL DEFAULT 0," +
                "FOREIGN KEY(venda_id) REFERENCES vendas(id)" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_venda_itens_venda ON venda_itens(venda_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_venda_itens_produto ON venda_itens(produto_id)");
    }

    private ContentValues values(Produto p) {
        ContentValues v = new ContentValues();
        v.put("codigo", p.codigo);
        v.put("nome", p.nome);
        v.put("codigo_barras", p.codigoBarras);
        v.put("grupo", p.grupo);
        v.put("fornecedor", p.fornecedor);
        v.put("unidade", p.unidade);
        v.put("fabricante", p.fabricante);
        v.put("custo", p.custo);
        v.put("preco_venda", p.precoVenda);
        v.put("preco_prazo", p.precoPrazo);
        v.put("estoque", p.estoque);
        v.put("estoque_minimo", p.estoqueMinimo);
        return v;
    }

    public long save(Produto p) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = values(p);
        v.put("updated_at", now);
        if (p.id > 0) {
            db.update("produtos", v, "id=?", new String[]{String.valueOf(p.id)});
            return p.id;
        } else {
            v.put("created_at", now);
            p.id = db.insertOrThrow("produtos", null, v);
            return p.id;
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("produtos", "id=?", new String[]{String.valueOf(id)});
    }

    public Produto get(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM produtos WHERE id=?", new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally { c.close(); }
    }

    public List<Produto> list(String busca) {
        List<Produto> out = new ArrayList<>();
        String q = busca == null ? "" : busca.trim();
        Cursor c;
        if (q.isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM produtos ORDER BY nome COLLATE NOCASE", null);
        } else {
            String like = "%" + q + "%";
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM produtos WHERE nome LIKE ? OR codigo LIKE ? OR codigo_barras LIKE ? " +
                            "ORDER BY nome COLLATE NOCASE LIMIT 50",
                    new String[]{like, like, like});
        }
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally { c.close(); }
        return out;
    }

    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM produtos", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    public long finalizarVenda(List<VendaItem> itens, Pagamento pagamento) {
        return finalizarVenda(itens, pagamento, 0, "", 0);
    }

    public long finalizarVenda(List<VendaItem> itens, Pagamento pagamento,
                               double desconto, String descontoTipo, double descontoReferencia) {
        if (itens == null || itens.isEmpty()) throw new IllegalArgumentException("Venda sem itens.");

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            double subtotal = 0;
            double custoTotal = 0;

            for (VendaItem item : itens) {
                if (item == null || item.produto == null || item.quantidade <= 0 || item.precoUnitario < 0) {
                    throw new IllegalArgumentException("Item de venda inválido.");
                }

                Produto atual = getProdutoNaTransacao(db, item.produto.id);
                if (atual == null) throw new IllegalStateException("Produto não encontrado: " + item.produto.nome);

                if (!atual.ehServico() && atual.estoque + 0.000001 < item.quantidade) {
                    throw new IllegalStateException("Estoque insuficiente para " + atual.nome +
                            ". Disponível: " + atual.estoque);
                }

                item.produto = atual;
                subtotal += item.total();
                custoTotal += item.custoTotal();
            }

            if (desconto < 0 || desconto > subtotal + 0.001) {
                throw new IllegalArgumentException("Desconto inválido.");
            }

            double total = subtotal - desconto;
            if (total < 0) total = 0;

            double somaPagamentos = pagamento.dinheiro + pagamento.pix + pagamento.cartao;
            if (Math.abs(somaPagamentos - total) > 0.011) {
                throw new IllegalArgumentException("Os pagamentos não conferem com o total da venda.");
            }

            ContentValues venda = new ContentValues();
            venda.put("data_millis", System.currentTimeMillis());
            venda.put("subtotal", subtotal);
            venda.put("desconto", desconto);
            venda.put("desconto_tipo", descontoTipo == null ? "" : descontoTipo);
            venda.put("desconto_referencia", descontoReferencia);
            venda.put("total", total);
            venda.put("custo_total", custoTotal);
            venda.put("lucro_bruto", total - custoTotal);
            venda.put("forma_pagamento", pagamento.forma);
            venda.put("dinheiro", pagamento.dinheiro);
            venda.put("pix", pagamento.pix);
            venda.put("cartao", pagamento.cartao);
            venda.put("recebido", pagamento.recebido);
            venda.put("troco", pagamento.troco);
            venda.put("consumidor_documento", "");
            venda.put("nota_status", "NAO_EMITIDA");
            venda.put("nota_numero", "");
            venda.put("nota_chave", "");
            venda.put("nota_protocolo", "");
            venda.put("nota_xml", "");
            venda.put("nota_tipo", "");
            venda.put("dest_nome", "");
            venda.put("dest_documento", "");
            venda.put("dest_ie", "");
            venda.put("dest_logradouro", "");
            venda.put("dest_numero", "");
            venda.put("dest_complemento", "");
            venda.put("dest_bairro", "");
            venda.put("dest_cep", "");
            venda.put("dest_municipio", "");
            venda.put("dest_uf", "");
            venda.put("dest_telefone", "");
            venda.put("dest_email", "");

            long vendaId = db.insertOrThrow("vendas", null, venda);

            double descontoRestante = desconto;
            for (int index=0; index<itens.size(); index++) {
                VendaItem item = itens.get(index);
                Produto p = item.produto;

                double descontoRateio;
                if (desconto <= 0 || subtotal <= 0) {
                    descontoRateio = 0;
                } else if (index == itens.size() - 1) {
                    descontoRateio = descontoRestante;
                } else {
                    descontoRateio = desconto * (item.total() / subtotal);
                    descontoRestante -= descontoRateio;
                }

                double totalLiquido = item.total() - descontoRateio;
                double lucroLiquido = totalLiquido - item.custoTotal();

                ContentValues vi = new ContentValues();
                vi.put("venda_id", vendaId);
                vi.put("produto_id", p.id);
                vi.put("codigo", p.codigo);
                vi.put("nome", p.nome);
                vi.put("unidade", p.unidade);
                vi.put("quantidade", item.quantidade);
                vi.put("preco_unitario", item.precoUnitario);
                vi.put("custo_unitario", p.custo);
                vi.put("total", item.total());
                vi.put("desconto_rateio", descontoRateio);
                vi.put("total_liquido", totalLiquido);
                vi.put("custo_total", item.custoTotal());
                vi.put("lucro", item.lucro());
                vi.put("lucro_liquido", lucroLiquido);
                db.insertOrThrow("venda_itens", null, vi);

                if (!p.ehServico()) {
                    ContentValues est = new ContentValues();
                    est.put("estoque", p.estoque - item.quantidade);
                    est.put("updated_at", System.currentTimeMillis());
                    db.update("produtos", est, "id=?", new String[]{String.valueOf(p.id)});
                }
            }

            db.setTransactionSuccessful();
            return vendaId;
        } finally {
            db.endTransaction();
        }
    }

    public VendaDetalhe getVendaDetalhe(long vendaId) {
        VendaDetalhe v = null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,subtotal,desconto,total,forma_pagamento,dinheiro,pix,cartao," +
                        "recebido,troco,consumidor_documento,nota_status,nota_numero,nota_chave,nota_protocolo,nota_xml " +
                        "FROM vendas WHERE id=?",
                new String[]{String.valueOf(vendaId)});
        try {
            if (c.moveToFirst()) {
                v = new VendaDetalhe();
                v.id = c.getLong(0);
                v.dataMillis = c.getLong(1);
                v.subtotal = c.getDouble(2);
                v.desconto = c.getDouble(3);
                v.total = c.getDouble(4);
                v.formaPagamento = c.getString(5);
                v.dinheiro = c.getDouble(6);
                v.pix = c.getDouble(7);
                v.cartao = c.getDouble(8);
                v.recebido = c.getDouble(9);
                v.troco = c.getDouble(10);
                v.consumidorDocumento = c.getString(11);
                v.notaStatus = c.getString(12);
                v.notaNumero = c.getString(13);
                v.notaChave = c.getString(14);
                v.notaProtocolo = c.getString(15);
                v.notaXml = c.getString(16);
            }
        } finally { c.close(); }

        if (v == null) return null;

        Cursor i = getReadableDatabase().rawQuery(
                "SELECT codigo,nome,unidade,quantidade,preco_unitario,total,desconto_rateio,total_liquido " +
                        "FROM venda_itens WHERE venda_id=? ORDER BY id",
                new String[]{String.valueOf(vendaId)});
        try {
            while (i.moveToNext()) {
                VendaItemRegistro item = new VendaItemRegistro();
                item.codigo = i.getString(0);
                item.nome = i.getString(1);
                item.unidade = i.getString(2);
                item.quantidade = i.getDouble(3);
                item.precoUnitario = i.getDouble(4);
                item.total = i.getDouble(5);
                item.descontoRateio = i.getDouble(6);
                item.totalLiquido = i.getDouble(7);
                v.itens.add(item);
            }
        } finally { i.close(); }

        return v;
    }

    public void registrarSolicitacaoNfce(long vendaId, String documento) {
        String doc = documento == null ? "" : documento.trim();
        ContentValues values = new ContentValues();
        values.put("nota_tipo", "NFC-e");
        values.put("consumidor_documento", doc);
        values.put("dest_documento", doc);
        values.put("nota_status", "PENDENTE_CONFIGURACAO");
        getWritableDatabase().update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
    }

    public void registrarSolicitacaoNfe(long vendaId,
                                        String nome, String documento, String ie,
                                        String logradouro, String numero, String complemento,
                                        String bairro, String cep, String municipio, String uf,
                                        String telefone, String email) {
        ContentValues values = new ContentValues();
        values.put("nota_tipo", "NF-e");
        values.put("consumidor_documento", documento == null ? "" : documento.trim());
        values.put("dest_nome", nome == null ? "" : nome.trim());
        values.put("dest_documento", documento == null ? "" : documento.trim());
        values.put("dest_ie", ie == null ? "" : ie.trim());
        values.put("dest_logradouro", logradouro == null ? "" : logradouro.trim());
        values.put("dest_numero", numero == null ? "" : numero.trim());
        values.put("dest_complemento", complemento == null ? "" : complemento.trim());
        values.put("dest_bairro", bairro == null ? "" : bairro.trim());
        values.put("dest_cep", cep == null ? "" : cep.trim());
        values.put("dest_municipio", municipio == null ? "" : municipio.trim());
        values.put("dest_uf", uf == null ? "" : uf.trim().toUpperCase());
        values.put("dest_telefone", telefone == null ? "" : telefone.trim());
        values.put("dest_email", email == null ? "" : email.trim());
        values.put("nota_status", "PENDENTE_CONFIGURACAO");
        getWritableDatabase().update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
    }

    public void atualizarNfce(long vendaId, String status, String numero,
                              String chave, String protocolo, String xml) {
        ContentValues values = new ContentValues();
        values.put("nota_status", status == null ? "" : status);
        values.put("nota_numero", numero == null ? "" : numero);
        values.put("nota_chave", chave == null ? "" : chave);
        values.put("nota_protocolo", protocolo == null ? "" : protocolo);
        values.put("nota_xml", xml == null ? "" : xml);
        getWritableDatabase().update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
    }

    public ResumoVendas resumoHoje() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long inicio = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        long fim = c.getTimeInMillis();
        return resumoPeriodo(inicio, fim);
    }

    public ResumoVendas resumoPeriodo(long inicio, long fim) {
        ResumoVendas r = new ResumoVendas();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(total),0), COALESCE(SUM(custo_total),0), " +
                        "COALESCE(SUM(lucro_bruto),0), COALESCE(SUM(dinheiro),0), " +
                        "COALESCE(SUM(pix),0), COALESCE(SUM(cartao),0), COALESCE(SUM(desconto),0) " +
                        "FROM vendas WHERE data_millis>=? AND data_millis<?",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            if (c.moveToFirst()) {
                r.quantidadeVendas = c.getInt(0);
                r.total = c.getDouble(1);
                r.custo = c.getDouble(2);
                r.lucro = c.getDouble(3);
                r.dinheiro = c.getDouble(4);
                r.pix = c.getDouble(5);
                r.cartao = c.getDouble(6);
                r.desconto = c.getDouble(7);
            }
        } finally { c.close(); }
        return r;
    }

    private Produto getProdutoNaTransacao(SQLiteDatabase db, long id) {
        Cursor c = db.rawQuery("SELECT * FROM produtos WHERE id=?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? fromCursor(c) : null;
        } finally { c.close(); }
    }

    private Produto fromCursor(Cursor c) {
        Produto p = new Produto();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.codigo = c.getString(c.getColumnIndexOrThrow("codigo"));
        p.nome = c.getString(c.getColumnIndexOrThrow("nome"));
        p.codigoBarras = c.getString(c.getColumnIndexOrThrow("codigo_barras"));
        p.grupo = c.getString(c.getColumnIndexOrThrow("grupo"));
        p.fornecedor = c.getString(c.getColumnIndexOrThrow("fornecedor"));
        p.unidade = c.getString(c.getColumnIndexOrThrow("unidade"));
        p.fabricante = c.getString(c.getColumnIndexOrThrow("fabricante"));
        p.custo = c.getDouble(c.getColumnIndexOrThrow("custo"));
        p.precoVenda = c.getDouble(c.getColumnIndexOrThrow("preco_venda"));
        p.precoPrazo = c.getDouble(c.getColumnIndexOrThrow("preco_prazo"));
        p.estoque = c.getDouble(c.getColumnIndexOrThrow("estoque"));
        p.estoqueMinimo = c.getDouble(c.getColumnIndexOrThrow("estoque_minimo"));
        return p;
    }
}
