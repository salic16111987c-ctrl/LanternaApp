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
    private static final int DB_VERSION = 9;

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

        // Dados fiscais do produto. Não recebem valores tributários automáticos:
        // devem ser conferidos com a contabilidade antes da emissão fiscal real.
        public String ncm = "";
        public String cest = "";
        public String cfop = "";
        public String origem = "";
        public String tributacaoIcms = "";
        public double aliquotaIcms;
        public String cstPis = "";
        public double aliquotaPis;
        public String cstCofins = "";
        public double aliquotaCofins;
        public String unidadeTributavel = "";
        public String gtinTributavel = "";

        public boolean fiscalMinimoPreenchido() {
            if (ehServico()) return true;
            return ncm != null && ncm.replaceAll("[^0-9]", "").length() == 8 &&
                    cfop != null && cfop.replaceAll("[^0-9]", "").length() == 4 &&
                    origem != null && !origem.trim().isEmpty() &&
                    tributacaoIcms != null && !tributacaoIcms.trim().isEmpty() &&
                    cstPis != null && !cstPis.trim().isEmpty() &&
                    cstCofins != null && !cstCofins.trim().isEmpty();
        }

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

    public static class Despesa {
        public long id;
        public long dataMillis;
        public String descricao = "";
        public String categoria = "";
        public String tipo = "OPERACIONAL";
        public String formaPagamento = "";
        public double valor;
        public String observacao = "";
        public String status = "ATIVA";
        public long canceladaEm;
        public String cancelamentoMotivo = "";
    }

    public static class ResumoFinanceiro {
        public ResumoVendas vendas = new ResumoVendas();
        public double despesasOperacionais;
        public double comprasEstoque;
        public double outrasSaidas;
        public double totalSaidas;
        public double lucroLiquido;
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
        public String notaTipo = "";
        public String destNome = "";
        public String destDocumento = "";
        public String statusVenda = "CONCLUIDA";
        public long estornoEm;
        public String estornoMotivo = "";
        public final List<VendaItemRegistro> itens = new ArrayList<>();
    }

    public static class VendaResumo {
        public long id;
        public long dataMillis;
        public double total;
        public double desconto;
        public String formaPagamento = "";
        public String notaTipo = "";
        public String notaStatus = "NAO_EMITIDA";
        public String destNome = "";
        public String destDocumento = "";
        public String statusVenda = "CONCLUIDA";
        public long estornoEm;
        public String estornoMotivo = "";
    }

    public static class EmpresaConfig {
        public long id = 1;
        public String razao = "";
        public String fantasia = "";
        public String cnpj = "";
        public String ie = "";
        public String regime = "";
        public String cep = "";
        public String logradouro = "";
        public String numero = "";
        public String complemento = "";
        public String bairro = "";
        public String municipio = "";
        public String uf = "";
        public String telefone = "";
        public String email = "";
        public String serieNfce = "1";
        public String serieNfe = "1";
        public boolean producao;
    }

    public static class Cliente {
        public long id;
        public String tipo = "PF";
        public String nome = "";
        public String documento = "";
        public String ie = "";
        public String logradouro = "";
        public String numero = "";
        public String complemento = "";
        public String bairro = "";
        public String cep = "";
        public String municipio = "";
        public String uf = "";
        public String telefone = "";
        public String email = "";
    }

    public GestaoDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        criarProdutos(db);
        criarVendas(db);
        criarEmpresaConfig(db);
        criarClientes(db);
        criarDespesas(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Bancos anteriores ao PDV não tinham as tabelas de venda.
            criarVendas(db);
            criarEmpresaConfig(db);
            criarClientes(db);
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

        if (oldVersion < 6) {
            criarEmpresaConfig(db);
            criarClientes(db);
        }

        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE vendas ADD COLUMN status_venda TEXT NOT NULL DEFAULT 'CONCLUIDA'");
            db.execSQL("ALTER TABLE vendas ADD COLUMN estorno_em INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE vendas ADD COLUMN estorno_motivo TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_vendas_status ON vendas(status_venda)");
        }

        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE produtos ADD COLUMN ncm TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN cest TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN cfop TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN origem TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN tributacao_icms TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN aliquota_icms REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE produtos ADD COLUMN cst_pis TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN aliquota_pis REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE produtos ADD COLUMN cst_cofins TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN aliquota_cofins REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE produtos ADD COLUMN unidade_tributavel TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE produtos ADD COLUMN gtin_tributavel TEXT NOT NULL DEFAULT ''");
        }

        if (oldVersion < 9) {
            criarDespesas(db);
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
                "ncm TEXT NOT NULL DEFAULT ''," +
                "cest TEXT NOT NULL DEFAULT ''," +
                "cfop TEXT NOT NULL DEFAULT ''," +
                "origem TEXT NOT NULL DEFAULT ''," +
                "tributacao_icms TEXT NOT NULL DEFAULT ''," +
                "aliquota_icms REAL NOT NULL DEFAULT 0," +
                "cst_pis TEXT NOT NULL DEFAULT ''," +
                "aliquota_pis REAL NOT NULL DEFAULT 0," +
                "cst_cofins TEXT NOT NULL DEFAULT ''," +
                "aliquota_cofins REAL NOT NULL DEFAULT 0," +
                "unidade_tributavel TEXT NOT NULL DEFAULT ''," +
                "gtin_tributavel TEXT NOT NULL DEFAULT ''," +
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
                "dest_email TEXT NOT NULL DEFAULT ''," +
                "status_venda TEXT NOT NULL DEFAULT 'CONCLUIDA'," +
                "estorno_em INTEGER NOT NULL DEFAULT 0," +
                "estorno_motivo TEXT NOT NULL DEFAULT ''" +
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

    private void criarEmpresaConfig(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS empresa_config (" +
                "id INTEGER PRIMARY KEY CHECK(id=1)," +
                "razao TEXT NOT NULL DEFAULT ''," +
                "fantasia TEXT NOT NULL DEFAULT ''," +
                "cnpj TEXT NOT NULL DEFAULT ''," +
                "ie TEXT NOT NULL DEFAULT ''," +
                "regime TEXT NOT NULL DEFAULT ''," +
                "cep TEXT NOT NULL DEFAULT ''," +
                "logradouro TEXT NOT NULL DEFAULT ''," +
                "numero TEXT NOT NULL DEFAULT ''," +
                "complemento TEXT NOT NULL DEFAULT ''," +
                "bairro TEXT NOT NULL DEFAULT ''," +
                "municipio TEXT NOT NULL DEFAULT ''," +
                "uf TEXT NOT NULL DEFAULT ''," +
                "telefone TEXT NOT NULL DEFAULT ''," +
                "email TEXT NOT NULL DEFAULT ''," +
                "serie_nfce TEXT NOT NULL DEFAULT '1'," +
                "serie_nfe TEXT NOT NULL DEFAULT '1'," +
                "producao INTEGER NOT NULL DEFAULT 0" +
                ")");
    }

    private void criarClientes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS clientes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "tipo TEXT NOT NULL DEFAULT 'PF'," +
                "nome TEXT NOT NULL," +
                "documento TEXT NOT NULL DEFAULT ''," +
                "ie TEXT NOT NULL DEFAULT ''," +
                "logradouro TEXT NOT NULL DEFAULT ''," +
                "numero TEXT NOT NULL DEFAULT ''," +
                "complemento TEXT NOT NULL DEFAULT ''," +
                "bairro TEXT NOT NULL DEFAULT ''," +
                "cep TEXT NOT NULL DEFAULT ''," +
                "municipio TEXT NOT NULL DEFAULT ''," +
                "uf TEXT NOT NULL DEFAULT ''," +
                "telefone TEXT NOT NULL DEFAULT ''," +
                "email TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_clientes_documento ON clientes(documento) WHERE documento<>''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clientes_nome ON clientes(nome)");
    }

    private void criarDespesas(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS despesas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "data_millis INTEGER NOT NULL," +
                "descricao TEXT NOT NULL," +
                "categoria TEXT NOT NULL DEFAULT ''," +
                "tipo TEXT NOT NULL DEFAULT 'OPERACIONAL'," +
                "forma_pagamento TEXT NOT NULL DEFAULT ''," +
                "valor REAL NOT NULL DEFAULT 0," +
                "observacao TEXT NOT NULL DEFAULT ''," +
                "status TEXT NOT NULL DEFAULT 'ATIVA'," +
                "cancelada_em INTEGER NOT NULL DEFAULT 0," +
                "cancelamento_motivo TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_despesas_data ON despesas(data_millis)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_despesas_status ON despesas(status)");
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
        v.put("ncm", p.ncm == null ? "" : p.ncm);
        v.put("cest", p.cest == null ? "" : p.cest);
        v.put("cfop", p.cfop == null ? "" : p.cfop);
        v.put("origem", p.origem == null ? "" : p.origem);
        v.put("tributacao_icms", p.tributacaoIcms == null ? "" : p.tributacaoIcms);
        v.put("aliquota_icms", p.aliquotaIcms);
        v.put("cst_pis", p.cstPis == null ? "" : p.cstPis);
        v.put("aliquota_pis", p.aliquotaPis);
        v.put("cst_cofins", p.cstCofins == null ? "" : p.cstCofins);
        v.put("aliquota_cofins", p.aliquotaCofins);
        v.put("unidade_tributavel", p.unidadeTributavel == null ? "" : p.unidadeTributavel);
        v.put("gtin_tributavel", p.gtinTributavel == null ? "" : p.gtinTributavel);
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

    public int countProdutosFiscalPendente() {
        int pendentes = 0;
        for (Produto p : list("")) {
            if (!p.fiscalMinimoPreenchido()) pendentes++;
        }
        return pendentes;
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
            venda.put("status_venda", "CONCLUIDA");
            venda.put("estorno_em", 0);
            venda.put("estorno_motivo", "");

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

    public List<VendaResumo> listVendas(int limite) {
        List<VendaResumo> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 500));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,total,desconto,forma_pagamento,nota_tipo,nota_status,dest_nome,dest_documento," +
                        "status_venda,estorno_em,estorno_motivo " +
                        "FROM vendas ORDER BY data_millis DESC,id DESC LIMIT " + max,
                null);
        try {
            while (c.moveToNext()) {
                VendaResumo v = new VendaResumo();
                v.id = c.getLong(0);
                v.dataMillis = c.getLong(1);
                v.total = c.getDouble(2);
                v.desconto = c.getDouble(3);
                v.formaPagamento = c.getString(4);
                v.notaTipo = c.getString(5);
                v.notaStatus = c.getString(6);
                v.destNome = c.getString(7);
                v.destDocumento = c.getString(8);
                v.statusVenda = c.getString(9);
                v.estornoEm = c.getLong(10);
                v.estornoMotivo = c.getString(11);
                out.add(v);
            }
        } finally { c.close(); }
        return out;
    }

    public VendaDetalhe getVendaDetalhe(long vendaId) {
        VendaDetalhe v = null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,subtotal,desconto,total,forma_pagamento,dinheiro,pix,cartao," +
                        "recebido,troco,consumidor_documento,nota_status,nota_numero,nota_chave,nota_protocolo,nota_xml," +
                        "nota_tipo,dest_nome,dest_documento,status_venda,estorno_em,estorno_motivo FROM vendas WHERE id=?",
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
                v.notaTipo = c.getString(17);
                v.destNome = c.getString(18);
                v.destDocumento = c.getString(19);
                v.statusVenda = c.getString(20);
                v.estornoEm = c.getLong(21);
                v.estornoMotivo = c.getString(22);
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


    public void estornarVenda(long vendaId, String motivo) {
        String motivoLimpo = motivo == null ? "" : motivo.trim();
        if (motivoLimpo.isEmpty()) {
            throw new IllegalArgumentException("Informe o motivo do estorno.");
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String statusVenda;
            String notaStatus;
            Cursor venda = db.rawQuery(
                    "SELECT status_venda,nota_status FROM vendas WHERE id=?",
                    new String[]{String.valueOf(vendaId)});
            try {
                if (!venda.moveToFirst()) {
                    throw new IllegalStateException("Venda não encontrada.");
                }
                statusVenda = venda.getString(0);
                notaStatus = venda.getString(1);
            } finally {
                venda.close();
            }

            if ("ESTORNADA".equalsIgnoreCase(statusVenda)) {
                throw new IllegalStateException("Esta venda já foi estornada.");
            }
            if ("AUTORIZADA".equalsIgnoreCase(notaStatus)) {
                throw new IllegalStateException(
                        "A venda possui nota fiscal autorizada. Cancele o documento fiscal antes de estornar a venda.");
            }

            Cursor itens = db.rawQuery(
                    "SELECT produto_id,nome,unidade,quantidade FROM venda_itens WHERE venda_id=?",
                    new String[]{String.valueOf(vendaId)});
            try {
                while (itens.moveToNext()) {
                    long produtoId = itens.getLong(0);
                    String nome = itens.getString(1);
                    String unidade = itens.getString(2);
                    double quantidade = itens.getDouble(3);

                    if (unidade != null && unidade.trim().equalsIgnoreCase("SERVIÇO")) {
                        continue;
                    }

                    Cursor produto = db.rawQuery(
                            "SELECT estoque FROM produtos WHERE id=?",
                            new String[]{String.valueOf(produtoId)});
                    double estoqueAtual;
                    try {
                        if (!produto.moveToFirst()) {
                            throw new IllegalStateException(
                                    "O produto \"" + nome + "\" não existe mais no cadastro. O estorno não foi realizado.");
                        }
                        estoqueAtual = produto.getDouble(0);
                    } finally {
                        produto.close();
                    }

                    ContentValues estoque = new ContentValues();
                    estoque.put("estoque", estoqueAtual + quantidade);
                    estoque.put("updated_at", System.currentTimeMillis());
                    int alterados = db.update(
                            "produtos", estoque, "id=?",
                            new String[]{String.valueOf(produtoId)});
                    if (alterados != 1) {
                        throw new IllegalStateException(
                                "Não foi possível devolver ao estoque o produto \"" + nome + "\".");
                    }
                }
            } finally {
                itens.close();
            }

            ContentValues values = new ContentValues();
            values.put("status_venda", "ESTORNADA");
            values.put("estorno_em", System.currentTimeMillis());
            values.put("estorno_motivo", motivoLimpo);
            int alteradas = db.update(
                    "vendas", values, "id=? AND status_venda<>'ESTORNADA'",
                    new String[]{String.valueOf(vendaId)});
            if (alteradas != 1) {
                throw new IllegalStateException("Não foi possível registrar o estorno.");
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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

    public void saveEmpresaConfig(EmpresaConfig e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", 1);
        v.put("razao", e.razao);
        v.put("fantasia", e.fantasia);
        v.put("cnpj", e.cnpj);
        v.put("ie", e.ie);
        v.put("regime", e.regime);
        v.put("cep", e.cep);
        v.put("logradouro", e.logradouro);
        v.put("numero", e.numero);
        v.put("complemento", e.complemento);
        v.put("bairro", e.bairro);
        v.put("municipio", e.municipio);
        v.put("uf", e.uf);
        v.put("telefone", e.telefone);
        v.put("email", e.email);
        v.put("serie_nfce", e.serieNfce);
        v.put("serie_nfe", e.serieNfe);
        v.put("producao", e.producao ? 1 : 0);
        db.insertWithOnConflict("empresa_config", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public EmpresaConfig getEmpresaConfig() {
        EmpresaConfig e = new EmpresaConfig();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT razao,fantasia,cnpj,ie,regime,cep,logradouro,numero,complemento,bairro,municipio,uf,telefone,email,serie_nfce,serie_nfe,producao FROM empresa_config WHERE id=1",
                null);
        try {
            if (c.moveToFirst()) {
                e.razao = c.getString(0);
                e.fantasia = c.getString(1);
                e.cnpj = c.getString(2);
                e.ie = c.getString(3);
                e.regime = c.getString(4);
                e.cep = c.getString(5);
                e.logradouro = c.getString(6);
                e.numero = c.getString(7);
                e.complemento = c.getString(8);
                e.bairro = c.getString(9);
                e.municipio = c.getString(10);
                e.uf = c.getString(11);
                e.telefone = c.getString(12);
                e.email = c.getString(13);
                e.serieNfce = c.getString(14);
                e.serieNfe = c.getString(15);
                e.producao = c.getInt(16) == 1;
            }
        } finally { c.close(); }
        return e;
    }

    public long saveCliente(Cliente c) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("tipo", c.tipo);
        v.put("nome", c.nome);
        v.put("documento", c.documento);
        v.put("ie", c.ie);
        v.put("logradouro", c.logradouro);
        v.put("numero", c.numero);
        v.put("complemento", c.complemento);
        v.put("bairro", c.bairro);
        v.put("cep", c.cep);
        v.put("municipio", c.municipio);
        v.put("uf", c.uf);
        v.put("telefone", c.telefone);
        v.put("email", c.email);
        v.put("updated_at", now);
        if (c.id > 0) {
            db.update("clientes", v, "id=?", new String[]{String.valueOf(c.id)});
            return c.id;
        }
        v.put("created_at", now);
        c.id = db.insertOrThrow("clientes", null, v);
        return c.id;
    }

    public void deleteCliente(long id) {
        getWritableDatabase().delete("clientes", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Cliente> listClientes(String busca) {
        List<Cliente> out = new ArrayList<>();
        String q = busca == null ? "" : busca.trim();
        Cursor c;
        if (q.isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM clientes ORDER BY nome COLLATE NOCASE LIMIT 100", null);
        } else {
            String like = "%" + q + "%";
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM clientes WHERE nome LIKE ? OR documento LIKE ? ORDER BY nome COLLATE NOCASE LIMIT 100",
                    new String[]{like, like});
        }
        try {
            while (c.moveToNext()) out.add(clienteFromCursor(c));
        } finally { c.close(); }
        return out;
    }

    private Cliente clienteFromCursor(Cursor c) {
        Cliente x = new Cliente();
        x.id = c.getLong(c.getColumnIndexOrThrow("id"));
        x.tipo = c.getString(c.getColumnIndexOrThrow("tipo"));
        x.nome = c.getString(c.getColumnIndexOrThrow("nome"));
        x.documento = c.getString(c.getColumnIndexOrThrow("documento"));
        x.ie = c.getString(c.getColumnIndexOrThrow("ie"));
        x.logradouro = c.getString(c.getColumnIndexOrThrow("logradouro"));
        x.numero = c.getString(c.getColumnIndexOrThrow("numero"));
        x.complemento = c.getString(c.getColumnIndexOrThrow("complemento"));
        x.bairro = c.getString(c.getColumnIndexOrThrow("bairro"));
        x.cep = c.getString(c.getColumnIndexOrThrow("cep"));
        x.municipio = c.getString(c.getColumnIndexOrThrow("municipio"));
        x.uf = c.getString(c.getColumnIndexOrThrow("uf"));
        x.telefone = c.getString(c.getColumnIndexOrThrow("telefone"));
        x.email = c.getString(c.getColumnIndexOrThrow("email"));
        return x;
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
                        "FROM vendas WHERE data_millis>=? AND data_millis<? AND status_venda<>'ESTORNADA'",
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

    public long saveDespesa(Despesa d) {
        if (d == null) throw new IllegalArgumentException("Despesa inválida.");
        if (d.descricao == null || d.descricao.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe a descrição da despesa.");
        }
        if (d.valor <= 0) throw new IllegalArgumentException("Informe um valor maior que zero.");

        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("data_millis", d.dataMillis > 0 ? d.dataMillis : now);
        v.put("descricao", d.descricao.trim());
        v.put("categoria", d.categoria == null ? "" : d.categoria.trim());
        v.put("tipo", d.tipo == null ? "OPERACIONAL" : d.tipo.trim());
        v.put("forma_pagamento", d.formaPagamento == null ? "" : d.formaPagamento.trim());
        v.put("valor", d.valor);
        v.put("observacao", d.observacao == null ? "" : d.observacao.trim());
        v.put("status", d.status == null ? "ATIVA" : d.status.trim());
        v.put("cancelada_em", d.canceladaEm);
        v.put("cancelamento_motivo", d.cancelamentoMotivo == null ? "" : d.cancelamentoMotivo.trim());
        v.put("updated_at", now);

        if (d.id > 0) {
            db.update("despesas", v, "id=? AND status='ATIVA'",
                    new String[]{String.valueOf(d.id)});
            return d.id;
        }

        v.put("created_at", now);
        d.id = db.insertOrThrow("despesas", null, v);
        return d.id;
    }

    public List<Despesa> listDespesas(long inicio, long fim, int limite) {
        List<Despesa> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 500));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,descricao,categoria,tipo,forma_pagamento,valor,observacao," +
                        "status,cancelada_em,cancelamento_motivo FROM despesas " +
                        "WHERE data_millis>=? AND data_millis<? ORDER BY data_millis DESC,id DESC LIMIT " + max,
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                Despesa d = new Despesa();
                d.id = c.getLong(0);
                d.dataMillis = c.getLong(1);
                d.descricao = c.getString(2);
                d.categoria = c.getString(3);
                d.tipo = c.getString(4);
                d.formaPagamento = c.getString(5);
                d.valor = c.getDouble(6);
                d.observacao = c.getString(7);
                d.status = c.getString(8);
                d.canceladaEm = c.getLong(9);
                d.cancelamentoMotivo = c.getString(10);
                out.add(d);
            }
        } finally { c.close(); }
        return out;
    }

    public void cancelarDespesa(long id, String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o motivo do cancelamento.");
        }
        ContentValues v = new ContentValues();
        v.put("status", "CANCELADA");
        v.put("cancelada_em", System.currentTimeMillis());
        v.put("cancelamento_motivo", motivo.trim());
        v.put("updated_at", System.currentTimeMillis());
        int alteradas = getWritableDatabase().update(
                "despesas", v, "id=? AND status='ATIVA'", new String[]{String.valueOf(id)});
        if (alteradas == 0) throw new IllegalStateException("Despesa já cancelada ou não encontrada.");
    }

    public ResumoFinanceiro resumoFinanceiro(long inicio, long fim) {
        ResumoFinanceiro r = new ResumoFinanceiro();
        r.vendas = resumoPeriodo(inicio, fim);

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT tipo,COALESCE(SUM(valor),0) FROM despesas " +
                        "WHERE data_millis>=? AND data_millis<? AND status='ATIVA' GROUP BY tipo",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                String tipo = c.getString(0);
                double valor = c.getDouble(1);
                if ("COMPRA_ESTOQUE".equalsIgnoreCase(tipo)) r.comprasEstoque += valor;
                else if ("OUTRA_SAIDA".equalsIgnoreCase(tipo)) r.outrasSaidas += valor;
                else r.despesasOperacionais += valor;
            }
        } finally { c.close(); }

        r.totalSaidas = r.despesasOperacionais + r.comprasEstoque + r.outrasSaidas;
        // Compra de estoque NÃO é descontada novamente do lucro: o custo da mercadoria
        // já entra no resultado quando o item é vendido.
        r.lucroLiquido = r.vendas.lucro - r.despesasOperacionais - r.outrasSaidas;
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
        p.ncm = c.getString(c.getColumnIndexOrThrow("ncm"));
        p.cest = c.getString(c.getColumnIndexOrThrow("cest"));
        p.cfop = c.getString(c.getColumnIndexOrThrow("cfop"));
        p.origem = c.getString(c.getColumnIndexOrThrow("origem"));
        p.tributacaoIcms = c.getString(c.getColumnIndexOrThrow("tributacao_icms"));
        p.aliquotaIcms = c.getDouble(c.getColumnIndexOrThrow("aliquota_icms"));
        p.cstPis = c.getString(c.getColumnIndexOrThrow("cst_pis"));
        p.aliquotaPis = c.getDouble(c.getColumnIndexOrThrow("aliquota_pis"));
        p.cstCofins = c.getString(c.getColumnIndexOrThrow("cst_cofins"));
        p.aliquotaCofins = c.getDouble(c.getColumnIndexOrThrow("aliquota_cofins"));
        p.unidadeTributavel = c.getString(c.getColumnIndexOrThrow("unidade_tributavel"));
        p.gtinTributavel = c.getString(c.getColumnIndexOrThrow("gtin_tributavel"));
        return p;
    }
}
