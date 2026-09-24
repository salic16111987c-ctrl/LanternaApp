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
    private static final int DB_VERSION = 3;

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
            // Cria direto no formato atual, evitando ALTER duplicado.
            criarVendas(db);
        } else if (oldVersion < 3) {
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
                "troco REAL NOT NULL DEFAULT 0" +
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
