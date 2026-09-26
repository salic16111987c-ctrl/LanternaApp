package com.techcell.caixadaloja;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

public class GestaoDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "gestao_techcell.db";
    private static final int DB_VERSION = 16;

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
        public String favorecidoNome = "";
        public String favorecidoDocumento = "";
        public String documentoTipo = "RECIBO";
        public String documentoNumero = "";
        public String documentoSerie = "";
        public String documentoChave = "";
        public long documentoEmissaoMillis;
        public String documentoEmitenteNome = "";
        public String documentoEmitenteCnpj = "";
        public double documentoValor;
        public String documentoXml = "";
        public String documentoUri = "";
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

    public static class RelatorioProduto {
        public String nome = "";
        public String unidade = "";
        public double quantidade;
        public double faturamento;
        public double custo;
        public double lucro;
    }


    public static class ResumoProdutosPeriodo {
        public int quantidadeVendas;
        public double quantidadeProdutos;
        public double faturamento;
        public double custo;
        public double lucro;

        public double margemPercentual() {
            return faturamento > 0 ? (lucro / faturamento) * 100.0 : 0.0;
        }
    }

    public static class RelatorioItemVendido {
        public long vendaId;
        public long dataMillis;
        public String codigo = "";
        public String nome = "";
        public String unidade = "";
        public double quantidade;
        public double precoUnitario;
        public double custoUnitario;
        public double total;
        public double descontoRateio;
        public double totalLiquido;
        public double custoTotal;
        public double lucro;
    }

    public static class RelatorioGrupoValor {
        public String rotulo = "";
        public double valor;
    }

    public static class RelatorioEstoque {
        public String nome = "";
        public String unidade = "";
        public double estoque;
        public double minimo;
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

    public static class Fornecedor {
        public long id;
        public String tipo = "PJ";
        public String nome = "";
        public String fantasia = "";
        public String documento = "";
        public String ie = "";
        public String contato = "";
        public String logradouro = "";
        public String numero = "";
        public String complemento = "";
        public String bairro = "";
        public String cep = "";
        public String municipio = "";
        public String uf = "";
        public String telefone = "";
        public String email = "";
        public String observacao = "";
        public String status = "ATIVO";
    }

    public static class SyncContext {
        public String empresaUuid = "";
        public String filialUuid = "";
        public String dispositivoUuid = "";
        public String papelDispositivo = "LOCAL";
        public String nomeDispositivo = "Este aparelho";
        public String masterTipo = "NAO_CONFIGURADO";
        public String masterHost = "";
        public int masterPort = 8765;
        public String masterDeviceUuid = "";
        public String masterName = "";
        public long masterLastSeen;
        public String lanPairingCode = "";
        public long lastSnapshotAt;
        public int lastSnapshotProdutos;
        public int lastSnapshotClientes;
        public int lastSnapshotFornecedores;
        public String masterAuthToken = "";
        public long lastSalePushAt;
        public int lastSalePushCount;
        public String lastSalePushError = "";
        public boolean configurado;
        public boolean cloudAtiva;
    }

    public GestaoDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        criarSyncBase(db);
        inicializarSyncContext(db);
        criarProdutos(db);
        criarVendas(db);
        criarEmpresaConfig(db);
        criarClientes(db);
        criarDespesas(db);
        criarFornecedores(db);
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

        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE despesas ADD COLUMN favorecido_nome TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN favorecido_documento TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_tipo TEXT NOT NULL DEFAULT 'RECIBO'");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_numero TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_serie TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_chave TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_emissao_millis INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_emitente_nome TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_emitente_cnpj TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_valor REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_xml TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE despesas ADD COLUMN documento_uri TEXT NOT NULL DEFAULT ''");
        }

        if (oldVersion < 11) {
            criarFornecedores(db);
        }

        if (oldVersion < 12) {
            migrarParaV12(db);
        }

        if (oldVersion < 13) {
            migrarParaV13(db);
        }

        if (oldVersion < 14) {
            migrarParaV14(db);
        }

        if (oldVersion < 15) {
            migrarParaV15(db);
        }

        if (oldVersion < 16) {
            migrarParaV16(db);
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
                syncColumnsSql() +
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
                "estorno_motivo TEXT NOT NULL DEFAULT ''," +
                syncColumnsSqlSemVirgulaFinal() +
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
                syncColumnsSql() +
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
                "producao INTEGER NOT NULL DEFAULT 0," +
                syncColumnsSqlSemVirgulaFinal() +
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
                syncColumnsSql() +
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
                "favorecido_nome TEXT NOT NULL DEFAULT ''," +
                "favorecido_documento TEXT NOT NULL DEFAULT ''," +
                "documento_tipo TEXT NOT NULL DEFAULT 'RECIBO'," +
                "documento_numero TEXT NOT NULL DEFAULT ''," +
                "documento_serie TEXT NOT NULL DEFAULT ''," +
                "documento_chave TEXT NOT NULL DEFAULT ''," +
                "documento_emissao_millis INTEGER NOT NULL DEFAULT 0," +
                "documento_emitente_nome TEXT NOT NULL DEFAULT ''," +
                "documento_emitente_cnpj TEXT NOT NULL DEFAULT ''," +
                "documento_valor REAL NOT NULL DEFAULT 0," +
                "documento_xml TEXT NOT NULL DEFAULT ''," +
                "documento_uri TEXT NOT NULL DEFAULT ''," +
                "status TEXT NOT NULL DEFAULT 'ATIVA'," +
                "cancelada_em INTEGER NOT NULL DEFAULT 0," +
                "cancelamento_motivo TEXT NOT NULL DEFAULT ''," +
                syncColumnsSql() +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_despesas_data ON despesas(data_millis)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_despesas_status ON despesas(status)");
    }

    private void criarFornecedores(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fornecedores (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "tipo TEXT NOT NULL DEFAULT 'PJ'," +
                "nome TEXT NOT NULL," +
                "fantasia TEXT NOT NULL DEFAULT ''," +
                "documento TEXT NOT NULL DEFAULT ''," +
                "ie TEXT NOT NULL DEFAULT ''," +
                "contato TEXT NOT NULL DEFAULT ''," +
                "logradouro TEXT NOT NULL DEFAULT ''," +
                "numero TEXT NOT NULL DEFAULT ''," +
                "complemento TEXT NOT NULL DEFAULT ''," +
                "bairro TEXT NOT NULL DEFAULT ''," +
                "cep TEXT NOT NULL DEFAULT ''," +
                "municipio TEXT NOT NULL DEFAULT ''," +
                "uf TEXT NOT NULL DEFAULT ''," +
                "telefone TEXT NOT NULL DEFAULT ''," +
                "email TEXT NOT NULL DEFAULT ''," +
                "observacao TEXT NOT NULL DEFAULT ''," +
                "status TEXT NOT NULL DEFAULT 'ATIVO'," +
                syncColumnsSql() +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_fornecedores_documento " +
                "ON fornecedores(documento) WHERE documento<>''");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fornecedores_nome ON fornecedores(nome)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fornecedores_status ON fornecedores(status)");
    }

    private static String syncColumnsSql() {
        return "uuid TEXT NOT NULL DEFAULT ''," +
                "empresa_uuid TEXT NOT NULL DEFAULT ''," +
                "filial_uuid TEXT NOT NULL DEFAULT ''," +
                "dispositivo_uuid TEXT NOT NULL DEFAULT ''," +
                "sync_status TEXT NOT NULL DEFAULT 'LOCAL'," +
                "sync_version INTEGER NOT NULL DEFAULT 1," +
                "sync_updated_at INTEGER NOT NULL DEFAULT 0,";
    }

    private static String syncColumnsSqlSemVirgulaFinal() {
        return "uuid TEXT NOT NULL DEFAULT ''," +
                "empresa_uuid TEXT NOT NULL DEFAULT ''," +
                "filial_uuid TEXT NOT NULL DEFAULT ''," +
                "dispositivo_uuid TEXT NOT NULL DEFAULT ''," +
                "sync_status TEXT NOT NULL DEFAULT 'LOCAL'," +
                "sync_version INTEGER NOT NULL DEFAULT 1," +
                "sync_updated_at INTEGER NOT NULL DEFAULT 0";
    }

    private void criarSyncBase(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_context (" +
                "id INTEGER PRIMARY KEY CHECK(id=1)," +
                "empresa_uuid TEXT NOT NULL," +
                "filial_uuid TEXT NOT NULL," +
                "dispositivo_uuid TEXT NOT NULL," +
                "papel_dispositivo TEXT NOT NULL DEFAULT 'LOCAL'," +
                "nome_dispositivo TEXT NOT NULL DEFAULT 'Este aparelho'," +
                "master_tipo TEXT NOT NULL DEFAULT 'NAO_CONFIGURADO'," +
                "master_host TEXT NOT NULL DEFAULT ''," +
                "master_port INTEGER NOT NULL DEFAULT 8765," +
                "master_device_uuid TEXT NOT NULL DEFAULT ''," +
                "master_name TEXT NOT NULL DEFAULT ''," +
                "master_last_seen INTEGER NOT NULL DEFAULT 0," +
                "lan_pairing_code TEXT NOT NULL DEFAULT ''," +
                "last_snapshot_at INTEGER NOT NULL DEFAULT 0," +
                "last_snapshot_produtos INTEGER NOT NULL DEFAULT 0," +
                "last_snapshot_clientes INTEGER NOT NULL DEFAULT 0," +
                "last_snapshot_fornecedores INTEGER NOT NULL DEFAULT 0," +
                "master_auth_token TEXT NOT NULL DEFAULT ''," +
                "last_sale_push_at INTEGER NOT NULL DEFAULT 0," +
                "last_sale_push_count INTEGER NOT NULL DEFAULT 0," +
                "last_sale_push_error TEXT NOT NULL DEFAULT ''," +
                "configurado INTEGER NOT NULL DEFAULT 0," +
                "cloud_ativa INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entidade TEXT NOT NULL," +
                "entidade_uuid TEXT NOT NULL," +
                "operacao TEXT NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'PENDENTE'," +
                "tentativas INTEGER NOT NULL DEFAULT 0," +
                "ultimo_erro TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_outbox_status ON sync_outbox(status,created_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS sync_tombstones (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entidade TEXT NOT NULL," +
                "entidade_uuid TEXT NOT NULL," +
                "empresa_uuid TEXT NOT NULL," +
                "filial_uuid TEXT NOT NULL," +
                "dispositivo_uuid TEXT NOT NULL," +
                "deleted_at INTEGER NOT NULL," +
                "sync_status TEXT NOT NULL DEFAULT 'PENDENTE'" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS lan_authorized_devices (" +
                "device_uuid TEXT PRIMARY KEY," +
                "auth_token TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "last_seen INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_tombstones_uuid " +
                "ON sync_tombstones(entidade,entidade_uuid)");
    }

    private void inicializarSyncContext(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT empresa_uuid,filial_uuid,dispositivo_uuid FROM sync_context WHERE id=1", null);
        try {
            if (c.moveToFirst()) return;
        } finally {
            c.close();
        }

        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", 1);
        v.put("empresa_uuid", novoUuid());
        v.put("filial_uuid", novoUuid());
        v.put("dispositivo_uuid", novoUuid());
        v.put("papel_dispositivo", "LOCAL");
        v.put("nome_dispositivo", "Este aparelho");
        v.put("master_tipo", "NAO_CONFIGURADO");
        v.put("master_host", "");
        v.put("master_port", 8765);
        v.put("master_device_uuid", "");
        v.put("master_name", "");
        v.put("master_last_seen", 0);
        v.put("lan_pairing_code", novoCodigoPareamento());
        v.put("last_snapshot_at", 0);
        v.put("last_snapshot_produtos", 0);
        v.put("last_snapshot_clientes", 0);
        v.put("last_snapshot_fornecedores", 0);
        v.put("master_auth_token", "");
        v.put("last_sale_push_at", 0);
        v.put("last_sale_push_count", 0);
        v.put("last_sale_push_error", "");
        v.put("configurado", 0);
        v.put("cloud_ativa", 0);
        v.put("created_at", now);
        v.put("updated_at", now);
        db.insertOrThrow("sync_context", null, v);
    }

    private String novoUuid() {
        return UUID.randomUUID().toString();
    }

    private String novoCodigoPareamento() {
        long v = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000L;
        return String.format(java.util.Locale.US, "%06d", v);
    }

    public SyncContext getSyncContext() {
        SQLiteDatabase db = getWritableDatabase();
        criarSyncBase(db);
        inicializarSyncContext(db);
        return lerSyncContext(db);
    }

    private SyncContext lerSyncContext(SQLiteDatabase db) {
        SyncContext x = new SyncContext();
        Cursor c = db.rawQuery(
                "SELECT empresa_uuid,filial_uuid,dispositivo_uuid,papel_dispositivo," +
                        "nome_dispositivo,master_tipo,master_host,master_port,master_device_uuid," +
                        "master_name,master_last_seen,lan_pairing_code,last_snapshot_at," +
                        "last_snapshot_produtos,last_snapshot_clientes,last_snapshot_fornecedores," +
                        "master_auth_token,last_sale_push_at,last_sale_push_count,last_sale_push_error," +
                        "configurado,cloud_ativa FROM sync_context WHERE id=1",
                null);
        try {
            if (c.moveToFirst()) {
                x.empresaUuid = c.getString(0);
                x.filialUuid = c.getString(1);
                x.dispositivoUuid = c.getString(2);
                x.papelDispositivo = c.getString(3);
                x.nomeDispositivo = c.getString(4);
                x.masterTipo = c.getString(5);
                x.masterHost = c.getString(6);
                x.masterPort = c.getInt(7);
                x.masterDeviceUuid = c.getString(8);
                x.masterName = c.getString(9);
                x.masterLastSeen = c.getLong(10);
                x.lanPairingCode = c.getString(11);
                x.lastSnapshotAt = c.getLong(12);
                x.lastSnapshotProdutos = c.getInt(13);
                x.lastSnapshotClientes = c.getInt(14);
                x.lastSnapshotFornecedores = c.getInt(15);
                x.masterAuthToken = c.getString(16);
                x.lastSalePushAt = c.getLong(17);
                x.lastSalePushCount = c.getInt(18);
                x.lastSalePushError = c.getString(19);
                x.configurado = c.getInt(20) == 1;
                x.cloudAtiva = c.getInt(21) == 1;
            }
        } finally {
            c.close();
        }
        return x;
    }

    private void migrarParaV13(SQLiteDatabase db) {
        criarSyncBase(db);
        adicionarColunaSeAusente(db, "sync_context", "master_host", "TEXT NOT NULL DEFAULT ''");
        adicionarColunaSeAusente(db, "sync_context", "master_port", "INTEGER NOT NULL DEFAULT 8765");
        adicionarColunaSeAusente(db, "sync_context", "configurado", "INTEGER NOT NULL DEFAULT 0");
    }

    private void migrarParaV14(SQLiteDatabase db) {
        criarSyncBase(db);
        adicionarColunaSeAusente(db, "sync_context", "master_device_uuid", "TEXT NOT NULL DEFAULT ''");
        adicionarColunaSeAusente(db, "sync_context", "master_name", "TEXT NOT NULL DEFAULT ''");
        adicionarColunaSeAusente(db, "sync_context", "master_last_seen", "INTEGER NOT NULL DEFAULT 0");
    }

    private void migrarParaV15(SQLiteDatabase db) {
        criarSyncBase(db);
        adicionarColunaSeAusente(db, "sync_context", "lan_pairing_code", "TEXT NOT NULL DEFAULT ''");
        adicionarColunaSeAusente(db, "sync_context", "last_snapshot_at", "INTEGER NOT NULL DEFAULT 0");
        adicionarColunaSeAusente(db, "sync_context", "last_snapshot_produtos", "INTEGER NOT NULL DEFAULT 0");
        adicionarColunaSeAusente(db, "sync_context", "last_snapshot_clientes", "INTEGER NOT NULL DEFAULT 0");
        adicionarColunaSeAusente(db, "sync_context", "last_snapshot_fornecedores", "INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE sync_context SET lan_pairing_code=? WHERE id=1 AND TRIM(lan_pairing_code)=''",
                new Object[]{novoCodigoPareamento()});
    }

    private void migrarParaV16(SQLiteDatabase db) {
        criarSyncBase(db);
        adicionarColunaSeAusente(db, "sync_context", "master_auth_token", "TEXT NOT NULL DEFAULT ''");
        adicionarColunaSeAusente(db, "sync_context", "last_sale_push_at", "INTEGER NOT NULL DEFAULT 0");
        adicionarColunaSeAusente(db, "sync_context", "last_sale_push_count", "INTEGER NOT NULL DEFAULT 0");
        adicionarColunaSeAusente(db, "sync_context", "last_sale_push_error", "TEXT NOT NULL DEFAULT ''");
    }

    public void salvarConfiguracaoDispositivo(String nome, String papel, String masterHost, int masterPort) {
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.isEmpty()) throw new IllegalArgumentException("Informe um nome para este dispositivo.");

        String papelLimpo = papel == null ? "" : papel.trim().toUpperCase();
        if (!"MASTER".equals(papelLimpo) && !"CAIXA".equals(papelLimpo) &&
                !"ADMIN".equals(papelLimpo) && !"CONSULTA".equals(papelLimpo)) {
            throw new IllegalArgumentException("Selecione a função deste dispositivo.");
        }

        int porta = masterPort <= 0 || masterPort > 65535 ? 8765 : masterPort;
        String host = masterHost == null ? "" : masterHost.trim();

        SQLiteDatabase db = getWritableDatabase();
        criarSyncBase(db);
        inicializarSyncContext(db);

        ContentValues v = new ContentValues();
        v.put("nome_dispositivo", nomeLimpo);
        v.put("papel_dispositivo", papelLimpo);
        v.put("master_tipo", "MASTER".equals(papelLimpo) ? "ANDROID" : "REMOTO");
        v.put("master_host", "MASTER".equals(papelLimpo) ? "" : host);
        v.put("master_port", porta);
        if ("MASTER".equals(papelLimpo)) {
            v.put("master_device_uuid", "");
            v.put("master_name", "");
            v.put("master_last_seen", 0);
            v.put("master_auth_token", "");
        }
        v.put("configurado", 1);
        v.put("updated_at", System.currentTimeMillis());
        db.update("sync_context", v, "id=1", null);
    }

    public int countDadosLocaisParaVinculo() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " +
                        "(SELECT COUNT(*) FROM produtos)+" +
                        "(SELECT COUNT(*) FROM vendas)+" +
                        "(SELECT COUNT(*) FROM clientes)+" +
                        "(SELECT COUNT(*) FROM despesas)+" +
                        "(SELECT COUNT(*) FROM fornecedores)+" +
                        "(SELECT COUNT(*) FROM empresa_config WHERE " +
                        "TRIM(COALESCE(razao,''))<>'' OR TRIM(COALESCE(fantasia,''))<>'' OR TRIM(COALESCE(cnpj,''))<>'')",
                null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public void vincularAoMaster(String host, int port, String empresaUuid, String filialUuid,
                                 String masterDeviceUuid, String masterName) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("Endereço do Master inválido.");
        if (empresaUuid == null || empresaUuid.trim().isEmpty()) throw new IllegalArgumentException("Empresa do Master inválida.");
        if (filialUuid == null || filialUuid.trim().isEmpty()) throw new IllegalArgumentException("Filial do Master inválida.");
        if (masterDeviceUuid == null || masterDeviceUuid.trim().isEmpty()) throw new IllegalArgumentException("Identificação do Master inválida.");

        SQLiteDatabase db = getWritableDatabase();
        SyncContext atual = lerSyncContext(db);
        String empresaNova = empresaUuid.trim();
        String filialNova = filialUuid.trim();

        if (!empresaNova.equalsIgnoreCase(atual.empresaUuid) && countDadosLocaisParaVinculo() > 0) {
            throw new IllegalStateException(
                    "Este aparelho já possui dados locais de outra empresa. Para segurança, o vínculo automático foi bloqueado.");
        }

        ContentValues v = new ContentValues();
        v.put("empresa_uuid", empresaNova);
        v.put("filial_uuid", filialNova);
        v.put("master_tipo", "ANDROID");
        v.put("master_host", host.trim());
        v.put("master_port", port > 0 && port <= 65535 ? port : 8765);
        v.put("master_device_uuid", masterDeviceUuid.trim());
        v.put("master_name", masterName == null ? "" : masterName.trim());
        v.put("master_last_seen", System.currentTimeMillis());
        v.put("configurado", 1);
        v.put("updated_at", System.currentTimeMillis());
        db.update("sync_context", v, "id=1", null);
    }

    public void registrarMasterOnline(String host) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        if (host != null && !host.trim().isEmpty()) v.put("master_host", host.trim());
        v.put("master_last_seen", System.currentTimeMillis());
        v.put("updated_at", System.currentTimeMillis());
        db.update("sync_context", v, "id=1", null);
    }

    public static class SnapshotStats {
        public int produtos;
        public int clientes;
        public int fornecedores;
        public long sincronizadoEm;
    }

    public boolean validarCodigoPareamento(String codigo) {
        SyncContext ctx = getSyncContext();
        String recebido = codigo == null ? "" : codigo.trim();
        return !recebido.isEmpty() && recebido.equals(ctx.lanPairingCode);
    }

    public String exportarSnapshotInicial() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            SyncContext ctx = lerSyncContext(db);
            JSONObject root = new JSONObject();
            root.put("schema", 1);
            root.put("empresa_uuid", ctx.empresaUuid);
            root.put("filial_uuid", ctx.filialUuid);
            root.put("master_device_uuid", ctx.dispositivoUuid);
            root.put("gerado_em", System.currentTimeMillis());

            JSONObject tabelas = new JSONObject();
            tabelas.put("empresa_config", exportarTabela(db, "empresa_config"));
            tabelas.put("produtos", exportarTabela(db, "produtos"));
            tabelas.put("clientes", exportarTabela(db, "clientes"));
            tabelas.put("fornecedores", exportarTabela(db, "fornecedores"));
            root.put("tabelas", tabelas);
            return root.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar dados do Master: " + e.getMessage(), e);
        }
    }

    private JSONArray exportarTabela(SQLiteDatabase db, String tabela) throws Exception {
        JSONArray out = new JSONArray();
        Cursor c = db.rawQuery("SELECT * FROM " + tabela, null);
        try {
            String[] cols = c.getColumnNames();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cols.length; i++) {
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: row.put(cols[i], JSONObject.NULL); break;
                        case Cursor.FIELD_TYPE_INTEGER: row.put(cols[i], c.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: row.put(cols[i], c.getDouble(i)); break;
                        case Cursor.FIELD_TYPE_BLOB:
                            row.put(cols[i], android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP));
                            break;
                        default: row.put(cols[i], c.getString(i));
                    }
                }
                out.put(row);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public SnapshotStats aplicarSnapshotInicial(String json) {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("Snapshot vazio.");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schema", 0) != 1) throw new IllegalStateException("Versão de sincronização incompatível.");

            SyncContext ctx = lerSyncContext(db);
            String empresa = root.optString("empresa_uuid", "");
            String filial = root.optString("filial_uuid", "");
            if (!empresa.equalsIgnoreCase(ctx.empresaUuid) || !filial.equalsIgnoreCase(ctx.filialUuid)) {
                throw new IllegalStateException("Os dados recebidos pertencem a outra empresa/filial.");
            }
            if ("MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                throw new IllegalStateException("O Master não pode importar o próprio snapshot.");
            }

            JSONObject tabelas = root.getJSONObject("tabelas");
            importarEmpresaConfig(db, tabelas.optJSONArray("empresa_config"));
            int produtos = importarTabelaPorUuid(db, "produtos", tabelas.optJSONArray("produtos"));
            int clientes = importarTabelaPorUuid(db, "clientes", tabelas.optJSONArray("clientes"));
            int fornecedores = importarTabelaPorUuid(db, "fornecedores", tabelas.optJSONArray("fornecedores"));

            long agora = System.currentTimeMillis();
            ContentValues sc = new ContentValues();
            sc.put("last_snapshot_at", agora);
            sc.put("last_snapshot_produtos", produtos);
            sc.put("last_snapshot_clientes", clientes);
            sc.put("last_snapshot_fornecedores", fornecedores);
            sc.put("master_last_seen", agora);
            sc.put("updated_at", agora);
            db.update("sync_context", sc, "id=1", null);

            db.setTransactionSuccessful();

            SnapshotStats st = new SnapshotStats();
            st.produtos = produtos;
            st.clientes = clientes;
            st.fornecedores = fornecedores;
            st.sincronizadoEm = agora;
            return st;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException)e;
            throw new IllegalStateException("Falha ao aplicar dados do Master: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
    }

    private void importarEmpresaConfig(SQLiteDatabase db, JSONArray arr) throws Exception {
        if (arr == null || arr.length() == 0) return;
        JSONObject row = arr.getJSONObject(0);
        ContentValues v = jsonParaValues(row, false);
        v.put("id", 1);
        v.put("sync_status", "MASTER_SYNCED");
        db.insertWithOnConflict("empresa_config", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private int importarTabelaPorUuid(SQLiteDatabase db, String tabela, JSONArray arr) throws Exception {
        if (arr == null) return 0;
        int total = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.getJSONObject(i);
            String uuid = row.optString("uuid", "").trim();
            if (uuid.isEmpty()) continue;

            ContentValues v = jsonParaValues(row, true);
            v.put("sync_status", "MASTER_SYNCED");

            long existente = idPorUuid(db, tabela, uuid);
            if (existente > 0) {
                db.update(tabela, v, "id=?", new String[]{String.valueOf(existente)});
            } else {
                db.insertOrThrow(tabela, null, v);
            }
            total++;
        }
        return total;
    }

    private long idPorUuid(SQLiteDatabase db, String tabela, String uuid) {
        Cursor c = db.rawQuery("SELECT id FROM " + tabela + " WHERE uuid=? LIMIT 1", new String[]{uuid});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private ContentValues jsonParaValues(JSONObject row, boolean removerId) throws Exception {
        ContentValues v = new ContentValues();
        JSONArray nomes = row.names();
        if (nomes == null) return v;
        for (int i = 0; i < nomes.length(); i++) {
            String nome = nomes.getString(i);
            if (removerId && "id".equalsIgnoreCase(nome)) continue;
            Object valor = row.opt(nome);
            if (valor == null || valor == JSONObject.NULL) {
                v.putNull(nome);
            } else if (valor instanceof Boolean) {
                v.put(nome, ((Boolean)valor) ? 1 : 0);
            } else if (valor instanceof Integer) {
                v.put(nome, (Integer)valor);
            } else if (valor instanceof Long) {
                v.put(nome, (Long)valor);
            } else if (valor instanceof Float) {
                v.put(nome, (Float)valor);
            } else if (valor instanceof Double) {
                v.put(nome, (Double)valor);
            } else {
                v.put(nome, String.valueOf(valor));
            }
        }
        return v;
    }

    public void salvarTokenMaster(String token) {
        ContentValues v = new ContentValues();
        v.put("master_auth_token", token == null ? "" : token.trim());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("sync_context", v, "id=1", null);
    }

    public String autorizarDispositivoLan(String deviceUuid) {
        String device = deviceUuid == null ? "" : deviceUuid.trim();
        if (device.isEmpty()) throw new IllegalArgumentException("Dispositivo inválido.");

        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT auth_token FROM lan_authorized_devices WHERE device_uuid=?",
                new String[]{device});
        try {
            if (c.moveToFirst()) {
                String token = c.getString(0);
                ContentValues seen = new ContentValues();
                seen.put("last_seen", System.currentTimeMillis());
                db.update("lan_authorized_devices", seen, "device_uuid=?", new String[]{device});
                return token;
            }
        } finally {
            c.close();
        }

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("device_uuid", device);
        v.put("auth_token", token);
        v.put("created_at", now);
        v.put("last_seen", now);
        db.insertOrThrow("lan_authorized_devices", null, v);
        return token;
    }

    public boolean validarTokenLan(String deviceUuid, String token) {
        String device = deviceUuid == null ? "" : deviceUuid.trim();
        String recebido = token == null ? "" : token.trim();
        if (device.isEmpty() || recebido.isEmpty()) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM lan_authorized_devices WHERE device_uuid=? AND auth_token=? LIMIT 1",
                new String[]{device, recebido});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public int countVendasPendentesMaster() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM vendas WHERE sync_status='PENDENTE'", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public List<Long> listarIdsVendasPendentesMaster() {
        List<Long> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM vendas WHERE sync_status='PENDENTE' ORDER BY data_millis,id", null);
        try {
            while (c.moveToNext()) out.add(c.getLong(0));
        } finally {
            c.close();
        }
        return out;
    }

    public String exportarVendaParaMaster(long vendaId) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            JSONObject root = new JSONObject();
            root.put("schema", 1);

            SyncContext ctx = lerSyncContext(db);
            root.put("empresa_uuid", ctx.empresaUuid);
            root.put("filial_uuid", ctx.filialUuid);
            root.put("dispositivo_uuid", ctx.dispositivoUuid);

            Cursor vc = db.rawQuery("SELECT * FROM vendas WHERE id=?", new String[]{String.valueOf(vendaId)});
            try {
                if (!vc.moveToFirst()) throw new IllegalStateException("Venda pendente não encontrada.");
                root.put("venda", cursorRowJson(vc));
            } finally {
                vc.close();
            }

            JSONArray itens = new JSONArray();
            Cursor ic = db.rawQuery(
                    "SELECT vi.*,p.uuid AS produto_uuid FROM venda_itens vi " +
                            "LEFT JOIN produtos p ON p.id=vi.produto_id WHERE vi.venda_id=? ORDER BY vi.id",
                    new String[]{String.valueOf(vendaId)});
            try {
                while (ic.moveToNext()) itens.put(cursorRowJson(ic));
            } finally {
                ic.close();
            }
            if (itens.length() == 0) throw new IllegalStateException("Venda sem itens para sincronizar.");
            root.put("itens", itens);
            return root.toString();
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException)e;
            throw new IllegalStateException("Falha ao preparar venda pendente: " + e.getMessage(), e);
        }
    }

    private JSONObject cursorRowJson(Cursor c) throws Exception {
        JSONObject row = new JSONObject();
        String[] cols = c.getColumnNames();
        for (int i = 0; i < cols.length; i++) {
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: row.put(cols[i], JSONObject.NULL); break;
                case Cursor.FIELD_TYPE_INTEGER: row.put(cols[i], c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: row.put(cols[i], c.getDouble(i)); break;
                case Cursor.FIELD_TYPE_BLOB:
                    row.put(cols[i], android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP));
                    break;
                default: row.put(cols[i], c.getString(i));
            }
        }
        return row;
    }

    public static class RecebimentoVenda {
        public String vendaUuid = "";
        public long vendaIdMaster;
        public boolean jaExistia;
    }

    public RecebimentoVenda receberVendaDoTerminal(String json) {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("Venda vazia.");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schema", 0) != 1) throw new IllegalStateException("Versão da venda incompatível.");

            SyncContext ctx = lerSyncContext(db);
            if (!"MASTER".equalsIgnoreCase(ctx.papelDispositivo)) {
                throw new IllegalStateException("Este aparelho não é o Master.");
            }
            if (!ctx.empresaUuid.equalsIgnoreCase(root.optString("empresa_uuid", "")) ||
                    !ctx.filialUuid.equalsIgnoreCase(root.optString("filial_uuid", ""))) {
                throw new IllegalStateException("Venda pertence a outra empresa/filial.");
            }

            JSONObject vendaJson = root.getJSONObject("venda");
            String vendaUuid = vendaJson.optString("uuid", "").trim();
            if (vendaUuid.isEmpty()) throw new IllegalStateException("Venda sem UUID.");

            long existente = idPorUuid(db, "vendas", vendaUuid);
            RecebimentoVenda out = new RecebimentoVenda();
            out.vendaUuid = vendaUuid;
            if (existente > 0) {
                out.vendaIdMaster = existente;
                out.jaExistia = true;
                db.setTransactionSuccessful();
                return out;
            }

            ContentValues venda = jsonParaValues(vendaJson, true);
            venda.put("empresa_uuid", ctx.empresaUuid);
            venda.put("filial_uuid", ctx.filialUuid);
            venda.put("sync_status", "PENDENTE");
            venda.put("sync_updated_at", System.currentTimeMillis());
            long masterVendaId = db.insertOrThrow("vendas", null, venda);

            JSONArray itens = root.getJSONArray("itens");
            if (itens.length() == 0) throw new IllegalStateException("Venda recebida sem itens.");

            for (int i = 0; i < itens.length(); i++) {
                JSONObject itemJson = itens.getJSONObject(i);
                String produtoUuid = itemJson.optString("produto_uuid", "").trim();
                if (produtoUuid.isEmpty()) throw new IllegalStateException("Item sem vínculo com o produto.");

                long produtoId = idPorUuid(db, "produtos", produtoUuid);
                if (produtoId <= 0) {
                    throw new IllegalStateException("Produto da venda não existe no Master: " + itemJson.optString("nome", produtoUuid));
                }

                String unidade;
                double estoque;
                Cursor pc = db.rawQuery("SELECT unidade,estoque FROM produtos WHERE id=?",
                        new String[]{String.valueOf(produtoId)});
                try {
                    if (!pc.moveToFirst()) throw new IllegalStateException("Produto não encontrado no Master.");
                    unidade = pc.getString(0);
                    estoque = pc.getDouble(1);
                } finally {
                    pc.close();
                }

                double quantidade = itemJson.optDouble("quantidade", 0);
                if (quantidade <= 0) throw new IllegalStateException("Quantidade inválida na venda recebida.");
                boolean servico = unidade != null && unidade.trim().equalsIgnoreCase("SERVIÇO");
                if (!servico && estoque + 0.000001 < quantidade) {
                    throw new IllegalStateException("Estoque insuficiente no Master para " +
                            itemJson.optString("nome", "produto") + ". Disponível: " + estoque);
                }

                ContentValues item = jsonParaValues(itemJson, true);
                item.remove("produto_uuid");
                item.put("venda_id", masterVendaId);
                item.put("produto_id", produtoId);
                item.put("empresa_uuid", ctx.empresaUuid);
                item.put("filial_uuid", ctx.filialUuid);
                item.put("sync_status", "PENDENTE");
                item.put("sync_updated_at", System.currentTimeMillis());
                db.insertOrThrow("venda_itens", null, item);

                if (!servico) {
                    long now = System.currentTimeMillis();
                    ContentValues estoqueV = new ContentValues();
                    estoqueV.put("estoque", estoque - quantidade);
                    estoqueV.put("updated_at", now);
                    estoqueV.put("sync_status", "PENDENTE");
                    estoqueV.put("sync_updated_at", now);
                    db.update("produtos", estoqueV, "id=?", new String[]{String.valueOf(produtoId)});
                    db.execSQL("UPDATE produtos SET sync_version=sync_version+1 WHERE id=?",
                            new Object[]{produtoId});
                }
            }

            out.vendaIdMaster = masterVendaId;
            out.jaExistia = false;
            db.setTransactionSuccessful();
            return out;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException)e;
            throw new IllegalStateException("Falha ao registrar venda no Master: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
    }

    public void marcarVendaSincronizadaMaster(long vendaId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("sync_status", "MASTER_SYNCED");
        v.put("sync_updated_at", now);
        db.update("vendas", v, "id=?", new String[]{String.valueOf(vendaId)});
        db.update("venda_itens", v, "venda_id=?", new String[]{String.valueOf(vendaId)});
    }

    public void registrarResultadoEnvioVendas(int quantidade, String erro) {
        ContentValues v = new ContentValues();
        v.put("last_sale_push_at", System.currentTimeMillis());
        v.put("last_sale_push_count", Math.max(0, quantidade));
        v.put("last_sale_push_error", erro == null ? "" : erro);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("sync_context", v, "id=1", null);
    }

    public int countSyncPendentes() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " +
                        "(SELECT COUNT(*) FROM produtos WHERE sync_status='PENDENTE')+" +
                        "(SELECT COUNT(*) FROM vendas WHERE sync_status='PENDENTE')+" +
                        "(SELECT COUNT(*) FROM clientes WHERE sync_status='PENDENTE')+" +
                        "(SELECT COUNT(*) FROM despesas WHERE sync_status='PENDENTE')+" +
                        "(SELECT COUNT(*) FROM fornecedores WHERE sync_status='PENDENTE')+" +
                        "(SELECT COUNT(*) FROM sync_tombstones WHERE sync_status='PENDENTE')",
                null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private void migrarParaV12(SQLiteDatabase db) {
        criarSyncBase(db);
        inicializarSyncContext(db);

        String[] tabelas = {
                "produtos", "vendas", "venda_itens",
                "empresa_config", "clientes", "despesas", "fornecedores"
        };
        for (String tabela : tabelas) {
            adicionarColunaSeAusente(db, tabela, "uuid", "TEXT NOT NULL DEFAULT ''");
            adicionarColunaSeAusente(db, tabela, "empresa_uuid", "TEXT NOT NULL DEFAULT ''");
            adicionarColunaSeAusente(db, tabela, "filial_uuid", "TEXT NOT NULL DEFAULT ''");
            adicionarColunaSeAusente(db, tabela, "dispositivo_uuid", "TEXT NOT NULL DEFAULT ''");
            adicionarColunaSeAusente(db, tabela, "sync_status", "TEXT NOT NULL DEFAULT 'LOCAL'");
            adicionarColunaSeAusente(db, tabela, "sync_version", "INTEGER NOT NULL DEFAULT 1");
            adicionarColunaSeAusente(db, tabela, "sync_updated_at", "INTEGER NOT NULL DEFAULT 0");
            preencherMetadadosExistentes(db, tabela);
        }

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_produtos_uuid ON produtos(uuid) WHERE uuid<>''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_vendas_uuid ON vendas(uuid) WHERE uuid<>''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_clientes_uuid ON clientes(uuid) WHERE uuid<>''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_despesas_uuid ON despesas(uuid) WHERE uuid<>''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_fornecedores_uuid ON fornecedores(uuid) WHERE uuid<>''");
    }

    private void adicionarColunaSeAusente(SQLiteDatabase db, String tabela, String coluna, String definicao) {
        if (!temColuna(db, tabela, coluna)) {
            db.execSQL("ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicao);
        }
    }

    private boolean temColuna(SQLiteDatabase db, String tabela, String coluna) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + tabela + ")", null);
        try {
            int idx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (coluna.equalsIgnoreCase(c.getString(idx))) return true;
            }
            return false;
        } finally {
            c.close();
        }
    }

    private void preencherMetadadosExistentes(SQLiteDatabase db, String tabela) {
        SyncContext ctx = lerSyncContext(db);
        Cursor c = db.rawQuery("SELECT id,uuid FROM " + tabela, null);
        long now = System.currentTimeMillis();
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String uuid = c.getString(1);
                ContentValues v = new ContentValues();
                if (uuid == null || uuid.trim().isEmpty()) v.put("uuid", novoUuid());
                v.put("empresa_uuid", ctx.empresaUuid);
                v.put("filial_uuid", ctx.filialUuid);
                v.put("dispositivo_uuid", ctx.dispositivoUuid);
                v.put("sync_status", "LOCAL");
                v.put("sync_version", 1);
                v.put("sync_updated_at", now);
                db.update(tabela, v, "id=?", new String[]{String.valueOf(id)});
            }
        } finally {
            c.close();
        }
    }

    private void aplicarMetadadosNovo(SQLiteDatabase db, ContentValues v) {
        SyncContext ctx = lerSyncContext(db);
        long now = System.currentTimeMillis();
        v.put("uuid", novoUuid());
        v.put("empresa_uuid", ctx.empresaUuid);
        v.put("filial_uuid", ctx.filialUuid);
        v.put("dispositivo_uuid", ctx.dispositivoUuid);
        v.put("sync_status", "PENDENTE");
        v.put("sync_version", 1);
        v.put("sync_updated_at", now);
    }

    private void marcarAlteracao(SQLiteDatabase db, String tabela, long id) {
        SyncContext ctx = lerSyncContext(db);
        ContentValues v = new ContentValues();
        v.put("dispositivo_uuid", ctx.dispositivoUuid);
        v.put("sync_status", "PENDENTE");
        v.put("sync_updated_at", System.currentTimeMillis());
        db.update(tabela, v, "id=?", new String[]{String.valueOf(id)});
        db.execSQL("UPDATE " + tabela + " SET sync_version=sync_version+1 WHERE id=?",
                new Object[]{id});
    }

    private void registrarExclusao(SQLiteDatabase db, String tabela, long id, String entidade) {
        Cursor c = db.rawQuery(
                "SELECT uuid,empresa_uuid,filial_uuid,dispositivo_uuid FROM " + tabela + " WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (!c.moveToFirst()) return;
            String uuid = c.getString(0);
            if (uuid == null || uuid.trim().isEmpty()) return;

            ContentValues v = new ContentValues();
            v.put("entidade", entidade);
            v.put("entidade_uuid", uuid);
            v.put("empresa_uuid", c.getString(1));
            v.put("filial_uuid", c.getString(2));
            v.put("dispositivo_uuid", c.getString(3));
            v.put("deleted_at", System.currentTimeMillis());
            v.put("sync_status", "PENDENTE");
            db.insertWithOnConflict("sync_tombstones", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } finally {
            c.close();
        }
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
            marcarAlteracao(db, "produtos", p.id);
            return p.id;
        } else {
            v.put("created_at", now);
            aplicarMetadadosNovo(db, v);
            p.id = db.insertOrThrow("produtos", null, v);
            return p.id;
        }
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        registrarExclusao(db, "produtos", id, "PRODUTO");
        db.delete("produtos", "id=?", new String[]{String.valueOf(id)});
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

            aplicarMetadadosNovo(db, venda);
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
                aplicarMetadadosNovo(db, vi);
                db.insertOrThrow("venda_itens", null, vi);

                if (!p.ehServico()) {
                    ContentValues est = new ContentValues();
                    est.put("estoque", p.estoque - item.quantidade);
                    est.put("updated_at", System.currentTimeMillis());
                    db.update("produtos", est, "id=?", new String[]{String.valueOf(p.id)});
                    marcarAlteracao(db, "produtos", p.id);
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
                    if (alterados == 1) marcarAlteracao(db, "produtos", produtoId);
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
            if (alteradas == 1) marcarAlteracao(db, "vendas", vendaId);
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
        SQLiteDatabase db = getWritableDatabase();
        db.update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
        marcarAlteracao(db, "vendas", vendaId);
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
        SQLiteDatabase db = getWritableDatabase();
        db.update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
        marcarAlteracao(db, "vendas", vendaId);
    }

    public void atualizarNfce(long vendaId, String status, String numero,
                              String chave, String protocolo, String xml) {
        ContentValues values = new ContentValues();
        values.put("nota_status", status == null ? "" : status);
        values.put("nota_numero", numero == null ? "" : numero);
        values.put("nota_chave", chave == null ? "" : chave);
        values.put("nota_protocolo", protocolo == null ? "" : protocolo);
        values.put("nota_xml", xml == null ? "" : xml);
        SQLiteDatabase db = getWritableDatabase();
        db.update(
                "vendas", values, "id=?",
                new String[]{String.valueOf(vendaId)});
        marcarAlteracao(db, "vendas", vendaId);
    }

    public void saveEmpresaConfig(EmpresaConfig e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
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

        int alteradas = db.update("empresa_config", v, "id=1", null);
        if (alteradas == 0) {
            v.put("id", 1);
            aplicarMetadadosNovo(db, v);
            db.insertOrThrow("empresa_config", null, v);
        } else {
            marcarAlteracao(db, "empresa_config", 1);
        }
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
            marcarAlteracao(db, "clientes", c.id);
            return c.id;
        }
        v.put("created_at", now);
        aplicarMetadadosNovo(db, v);
        c.id = db.insertOrThrow("clientes", null, v);
        return c.id;
    }

    public void deleteCliente(long id) {
        SQLiteDatabase db = getWritableDatabase();
        registrarExclusao(db, "clientes", id, "CLIENTE");
        db.delete("clientes", "id=?", new String[]{String.valueOf(id)});
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

    public long saveFornecedor(Fornecedor f) {
        if (f == null) throw new IllegalArgumentException("Fornecedor inválido.");
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("tipo", f.tipo == null ? "PJ" : f.tipo);
        v.put("nome", f.nome == null ? "" : f.nome.trim());
        v.put("fantasia", f.fantasia == null ? "" : f.fantasia.trim());
        v.put("documento", f.documento == null ? "" : f.documento.trim());
        v.put("ie", f.ie == null ? "" : f.ie.trim());
        v.put("contato", f.contato == null ? "" : f.contato.trim());
        v.put("logradouro", f.logradouro == null ? "" : f.logradouro.trim());
        v.put("numero", f.numero == null ? "" : f.numero.trim());
        v.put("complemento", f.complemento == null ? "" : f.complemento.trim());
        v.put("bairro", f.bairro == null ? "" : f.bairro.trim());
        v.put("cep", f.cep == null ? "" : f.cep.trim());
        v.put("municipio", f.municipio == null ? "" : f.municipio.trim());
        v.put("uf", f.uf == null ? "" : f.uf.trim().toUpperCase());
        v.put("telefone", f.telefone == null ? "" : f.telefone.trim());
        v.put("email", f.email == null ? "" : f.email.trim());
        v.put("observacao", f.observacao == null ? "" : f.observacao.trim());
        v.put("status", f.status == null || f.status.trim().isEmpty() ? "ATIVO" : f.status.trim());
        v.put("updated_at", now);
        if (f.id > 0) {
            db.update("fornecedores", v, "id=?", new String[]{String.valueOf(f.id)});
            marcarAlteracao(db, "fornecedores", f.id);
            return f.id;
        }
        v.put("created_at", now);
        aplicarMetadadosNovo(db, v);
        f.id = db.insertOrThrow("fornecedores", null, v);
        return f.id;
    }

    public List<Fornecedor> listFornecedores(String busca, boolean incluirInativos) {
        List<Fornecedor> out = new ArrayList<>();
        String q = busca == null ? "" : busca.trim();
        String whereStatus = incluirInativos ? "" : " AND status='ATIVO'";
        Cursor c;
        if (q.isEmpty()) {
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM fornecedores WHERE 1=1" + whereStatus +
                            " ORDER BY nome COLLATE NOCASE LIMIT 200", null);
        } else {
            String like = "%" + q + "%";
            c = getReadableDatabase().rawQuery(
                    "SELECT * FROM fornecedores WHERE (nome LIKE ? OR fantasia LIKE ? OR documento LIKE ? OR contato LIKE ?)" +
                            whereStatus + " ORDER BY nome COLLATE NOCASE LIMIT 200",
                    new String[]{like, like, like, like});
        }
        try {
            while (c.moveToNext()) out.add(fornecedorFromCursor(c));
        } finally { c.close(); }
        return out;
    }

    public void setFornecedorAtivo(long id, boolean ativo) {
        ContentValues v = new ContentValues();
        v.put("status", ativo ? "ATIVO" : "INATIVO");
        v.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.update("fornecedores", v, "id=?", new String[]{String.valueOf(id)});
        marcarAlteracao(db, "fornecedores", id);
    }

    public Fornecedor getFornecedorPorDocumento(String documento) {
        String d = documento == null ? "" : documento.trim();
        if (d.isEmpty()) return null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM fornecedores WHERE documento=? LIMIT 1", new String[]{d});
        try {
            return c.moveToFirst() ? fornecedorFromCursor(c) : null;
        } finally { c.close(); }
    }

    private Fornecedor fornecedorFromCursor(Cursor c) {
        Fornecedor f = new Fornecedor();
        f.id = c.getLong(c.getColumnIndexOrThrow("id"));
        f.tipo = c.getString(c.getColumnIndexOrThrow("tipo"));
        f.nome = c.getString(c.getColumnIndexOrThrow("nome"));
        f.fantasia = c.getString(c.getColumnIndexOrThrow("fantasia"));
        f.documento = c.getString(c.getColumnIndexOrThrow("documento"));
        f.ie = c.getString(c.getColumnIndexOrThrow("ie"));
        f.contato = c.getString(c.getColumnIndexOrThrow("contato"));
        f.logradouro = c.getString(c.getColumnIndexOrThrow("logradouro"));
        f.numero = c.getString(c.getColumnIndexOrThrow("numero"));
        f.complemento = c.getString(c.getColumnIndexOrThrow("complemento"));
        f.bairro = c.getString(c.getColumnIndexOrThrow("bairro"));
        f.cep = c.getString(c.getColumnIndexOrThrow("cep"));
        f.municipio = c.getString(c.getColumnIndexOrThrow("municipio"));
        f.uf = c.getString(c.getColumnIndexOrThrow("uf"));
        f.telefone = c.getString(c.getColumnIndexOrThrow("telefone"));
        f.email = c.getString(c.getColumnIndexOrThrow("email"));
        f.observacao = c.getString(c.getColumnIndexOrThrow("observacao"));
        f.status = c.getString(c.getColumnIndexOrThrow("status"));
        return f;
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
        v.put("favorecido_nome", d.favorecidoNome == null ? "" : d.favorecidoNome.trim());
        v.put("favorecido_documento", d.favorecidoDocumento == null ? "" : d.favorecidoDocumento.trim());
        v.put("documento_tipo", d.documentoTipo == null ? "RECIBO" : d.documentoTipo.trim());
        v.put("documento_numero", d.documentoNumero == null ? "" : d.documentoNumero.trim());
        v.put("documento_serie", d.documentoSerie == null ? "" : d.documentoSerie.trim());
        v.put("documento_chave", d.documentoChave == null ? "" : d.documentoChave.trim());
        v.put("documento_emissao_millis", d.documentoEmissaoMillis);
        v.put("documento_emitente_nome", d.documentoEmitenteNome == null ? "" : d.documentoEmitenteNome.trim());
        v.put("documento_emitente_cnpj", d.documentoEmitenteCnpj == null ? "" : d.documentoEmitenteCnpj.trim());
        v.put("documento_valor", d.documentoValor);
        v.put("documento_xml", d.documentoXml == null ? "" : d.documentoXml);
        v.put("documento_uri", d.documentoUri == null ? "" : d.documentoUri);
        v.put("status", d.status == null ? "ATIVA" : d.status.trim());
        v.put("cancelada_em", d.canceladaEm);
        v.put("cancelamento_motivo", d.cancelamentoMotivo == null ? "" : d.cancelamentoMotivo.trim());
        v.put("updated_at", now);

        if (d.id > 0) {
            db.update("despesas", v, "id=? AND status='ATIVA'",
                    new String[]{String.valueOf(d.id)});
            marcarAlteracao(db, "despesas", d.id);
            return d.id;
        }

        v.put("created_at", now);
        aplicarMetadadosNovo(db, v);
        d.id = db.insertOrThrow("despesas", null, v);
        return d.id;
    }

    public List<Despesa> listDespesas(long inicio, long fim, int limite) {
        List<Despesa> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 500));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,descricao,categoria,tipo,forma_pagamento,valor,observacao," +
                        "favorecido_nome,favorecido_documento,documento_tipo,documento_numero,documento_serie," +
                        "documento_chave,documento_emissao_millis,documento_emitente_nome,documento_emitente_cnpj," +
                        "documento_valor,documento_xml,documento_uri,status,cancelada_em,cancelamento_motivo FROM despesas " +
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
                d.favorecidoNome = c.getString(8);
                d.favorecidoDocumento = c.getString(9);
                d.documentoTipo = c.getString(10);
                d.documentoNumero = c.getString(11);
                d.documentoSerie = c.getString(12);
                d.documentoChave = c.getString(13);
                d.documentoEmissaoMillis = c.getLong(14);
                d.documentoEmitenteNome = c.getString(15);
                d.documentoEmitenteCnpj = c.getString(16);
                d.documentoValor = c.getDouble(17);
                d.documentoXml = c.getString(18);
                d.documentoUri = c.getString(19);
                d.status = c.getString(20);
                d.canceladaEm = c.getLong(21);
                d.cancelamentoMotivo = c.getString(22);
                out.add(d);
            }
        } finally { c.close(); }
        return out;
    }

    public Despesa getDespesa(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,data_millis,descricao,categoria,tipo,forma_pagamento,valor,observacao," +
                        "favorecido_nome,favorecido_documento,documento_tipo,documento_numero,documento_serie," +
                        "documento_chave,documento_emissao_millis,documento_emitente_nome,documento_emitente_cnpj," +
                        "documento_valor,documento_xml,documento_uri,status,cancelada_em,cancelamento_motivo " +
                        "FROM despesas WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (!c.moveToFirst()) return null;
            Despesa d = new Despesa();
            d.id = c.getLong(0);
            d.dataMillis = c.getLong(1);
            d.descricao = c.getString(2);
            d.categoria = c.getString(3);
            d.tipo = c.getString(4);
            d.formaPagamento = c.getString(5);
            d.valor = c.getDouble(6);
            d.observacao = c.getString(7);
            d.favorecidoNome = c.getString(8);
            d.favorecidoDocumento = c.getString(9);
            d.documentoTipo = c.getString(10);
            d.documentoNumero = c.getString(11);
            d.documentoSerie = c.getString(12);
            d.documentoChave = c.getString(13);
            d.documentoEmissaoMillis = c.getLong(14);
            d.documentoEmitenteNome = c.getString(15);
            d.documentoEmitenteCnpj = c.getString(16);
            d.documentoValor = c.getDouble(17);
            d.documentoXml = c.getString(18);
            d.documentoUri = c.getString(19);
            d.status = c.getString(20);
            d.canceladaEm = c.getLong(21);
            d.cancelamentoMotivo = c.getString(22);
            return d;
        } finally { c.close(); }
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
        SQLiteDatabase db = getWritableDatabase();
        int alteradas = db.update(
                "despesas", v, "id=? AND status='ATIVA'", new String[]{String.valueOf(id)});
        if (alteradas == 1) marcarAlteracao(db, "despesas", id);
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

    public List<RelatorioProduto> topProdutosPeriodo(long inicio, long fim, int limite) {
        List<RelatorioProduto> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 100));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT vi.nome,COALESCE(vi.unidade,''),COALESCE(SUM(vi.quantidade),0)," +
                        "COALESCE(SUM(vi.total_liquido),0),COALESCE(SUM(vi.custo_total),0)," +
                        "COALESCE(SUM(vi.lucro_liquido),0) " +
                        "FROM venda_itens vi INNER JOIN vendas v ON v.id=vi.venda_id " +
                        "WHERE v.data_millis>=? AND v.data_millis<? AND v.status_venda<>'ESTORNADA' " +
                        "GROUP BY vi.produto_id,vi.nome,vi.unidade " +
                        "ORDER BY SUM(vi.quantidade) DESC,SUM(vi.total_liquido) DESC LIMIT " + max,
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                RelatorioProduto x = new RelatorioProduto();
                x.nome = c.getString(0);
                x.unidade = c.getString(1);
                x.quantidade = c.getDouble(2);
                x.faturamento = c.getDouble(3);
                x.custo = c.getDouble(4);
                x.lucro = c.getDouble(5);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
    }


    public ResumoProdutosPeriodo resumoProdutosPeriodo(long inicio, long fim) {
        ResumoProdutosPeriodo r = new ResumoProdutosPeriodo();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT v.id),COALESCE(SUM(vi.quantidade),0)," +
                        "COALESCE(SUM(vi.total_liquido),0),COALESCE(SUM(vi.custo_total),0)," +
                        "COALESCE(SUM(vi.lucro_liquido),0) " +
                        "FROM venda_itens vi INNER JOIN vendas v ON v.id=vi.venda_id " +
                        "WHERE v.data_millis>=? AND v.data_millis<? " +
                        "AND v.status_venda<>'ESTORNADA' " +
                        "AND UPPER(TRIM(COALESCE(vi.unidade,'')))<>'SERVIÇO'",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            if (c.moveToFirst()) {
                r.quantidadeVendas = c.getInt(0);
                r.quantidadeProdutos = c.getDouble(1);
                r.faturamento = c.getDouble(2);
                r.custo = c.getDouble(3);
                r.lucro = c.getDouble(4);
            }
        } finally { c.close(); }
        return r;
    }

    public List<RelatorioProduto> produtosVendidosPeriodo(long inicio, long fim) {
        List<RelatorioProduto> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT vi.nome,COALESCE(vi.unidade,''),COALESCE(SUM(vi.quantidade),0)," +
                        "COALESCE(SUM(vi.total_liquido),0),COALESCE(SUM(vi.custo_total),0)," +
                        "COALESCE(SUM(vi.lucro_liquido),0) " +
                        "FROM venda_itens vi INNER JOIN vendas v ON v.id=vi.venda_id " +
                        "WHERE v.data_millis>=? AND v.data_millis<? " +
                        "AND v.status_venda<>'ESTORNADA' " +
                        "AND UPPER(TRIM(COALESCE(vi.unidade,'')))<>'SERVIÇO' " +
                        "GROUP BY vi.produto_id,vi.nome,vi.unidade " +
                        "ORDER BY SUM(vi.total_liquido) DESC,SUM(vi.quantidade) DESC,vi.nome COLLATE NOCASE",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                RelatorioProduto x = new RelatorioProduto();
                x.nome = c.getString(0);
                x.unidade = c.getString(1);
                x.quantidade = c.getDouble(2);
                x.faturamento = c.getDouble(3);
                x.custo = c.getDouble(4);
                x.lucro = c.getDouble(5);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
    }

    public List<RelatorioItemVendido> itensProdutosVendidosPeriodo(long inicio, long fim) {
        List<RelatorioItemVendido> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT v.id,v.data_millis,COALESCE(vi.codigo,''),vi.nome,COALESCE(vi.unidade,'')," +
                        "vi.quantidade,vi.preco_unitario,vi.custo_unitario,vi.total,vi.desconto_rateio," +
                        "vi.total_liquido,vi.custo_total,vi.lucro_liquido " +
                        "FROM venda_itens vi INNER JOIN vendas v ON v.id=vi.venda_id " +
                        "WHERE v.data_millis>=? AND v.data_millis<? " +
                        "AND v.status_venda<>'ESTORNADA' " +
                        "AND UPPER(TRIM(COALESCE(vi.unidade,'')))<>'SERVIÇO' " +
                        "ORDER BY v.data_millis DESC,v.id DESC,vi.id DESC",
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                RelatorioItemVendido x = new RelatorioItemVendido();
                x.vendaId = c.getLong(0);
                x.dataMillis = c.getLong(1);
                x.codigo = c.getString(2);
                x.nome = c.getString(3);
                x.unidade = c.getString(4);
                x.quantidade = c.getDouble(5);
                x.precoUnitario = c.getDouble(6);
                x.custoUnitario = c.getDouble(7);
                x.total = c.getDouble(8);
                x.descontoRateio = c.getDouble(9);
                x.totalLiquido = c.getDouble(10);
                x.custoTotal = c.getDouble(11);
                x.lucro = c.getDouble(12);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
    }

    public List<RelatorioGrupoValor> despesasPorCategoriaPeriodo(long inicio, long fim, int limite) {
        List<RelatorioGrupoValor> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 100));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT CASE WHEN TRIM(categoria)='' THEN 'Sem categoria' ELSE categoria END," +
                        "COALESCE(SUM(valor),0) FROM despesas " +
                        "WHERE data_millis>=? AND data_millis<? AND status='ATIVA' " +
                        "AND tipo<>'COMPRA_ESTOQUE' GROUP BY 1 ORDER BY SUM(valor) DESC LIMIT " + max,
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                RelatorioGrupoValor x = new RelatorioGrupoValor();
                x.rotulo = c.getString(0);
                x.valor = c.getDouble(1);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
    }

    public List<RelatorioGrupoValor> saidasPorFavorecidoPeriodo(long inicio, long fim, int limite) {
        List<RelatorioGrupoValor> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 100));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT CASE WHEN TRIM(favorecido_nome)='' THEN " +
                        "CASE WHEN TRIM(documento_emitente_nome)='' THEN 'Sem favorecido' ELSE documento_emitente_nome END " +
                        "ELSE favorecido_nome END,COALESCE(SUM(valor),0) " +
                        "FROM despesas WHERE data_millis>=? AND data_millis<? AND status='ATIVA' " +
                        "GROUP BY 1 ORDER BY SUM(valor) DESC LIMIT " + max,
                new String[]{String.valueOf(inicio), String.valueOf(fim)});
        try {
            while (c.moveToNext()) {
                RelatorioGrupoValor x = new RelatorioGrupoValor();
                x.rotulo = c.getString(0);
                x.valor = c.getDouble(1);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
    }

    public List<RelatorioEstoque> produtosEstoqueBaixo(int limite) {
        List<RelatorioEstoque> out = new ArrayList<>();
        int max = Math.max(1, Math.min(limite, 200));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT nome,COALESCE(unidade,''),estoque,estoque_minimo FROM produtos " +
                        "WHERE UPPER(TRIM(COALESCE(unidade,'')))<>'SERVIÇO' " +
                        "AND ((estoque_minimo>0 AND estoque<=estoque_minimo) OR estoque<=0) " +
                        "ORDER BY CASE WHEN estoque<=0 THEN 0 ELSE 1 END,estoque ASC,nome COLLATE NOCASE LIMIT " + max,
                null);
        try {
            while (c.moveToNext()) {
                RelatorioEstoque x = new RelatorioEstoque();
                x.nome = c.getString(0);
                x.unidade = c.getString(1);
                x.estoque = c.getDouble(2);
                x.minimo = c.getDouble(3);
                out.add(x);
            }
        } finally { c.close(); }
        return out;
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
