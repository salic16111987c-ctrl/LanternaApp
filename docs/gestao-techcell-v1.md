# Gestão Tech Cell — v1

Branch de desenvolvimento: `gestao-techcell-v1`.

## Regra principal
O **Caixa da Loja** continua separado e não deve ter seus dados alterados pela Gestão Tech Cell.

## Tela inicial
Um único aplicativo abre com duas opções:
1. **Caixa da Loja** — módulo existente.
2. **Gestão Tech Cell** — novo módulo.

## Gestão Tech Cell
Módulos previstos:
- PDV / frente de caixa
- Produtos
- Estoque
- Clientes
- Fornecedores
- Financeiro
- Relatórios
- Importação do SMB

## Lucros
- Lucro bruto = valor efetivamente vendido − custo histórico da mercadoria
- Lucro líquido = lucro bruto − despesas

Para histórico importado do SMB, usar o custo registrado na venda quando disponível, e não o custo atual do cadastro.

## Segurança da migração
A importação do SMB será feita somente depois de:
1. extrair e conferir os registros;
2. mapear campos;
3. gerar backup;
4. importar para coleções próprias da Gestão Tech Cell;
5. reconciliar totais antes/depois.
