package com.techcell.caixadaloja;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Permissões granulares. O perfil é só um modelo inicial; o Master pode personalizar. */
public final class TechCellPermissions {
    public static final String VENDER = "VENDER";
    public static final String CLIENTES = "CLIENTES";
    public static final String PRODUTOS = "PRODUTOS";
    public static final String PRODUTOS_EDITAR = "PRODUTOS_EDITAR";
    public static final String PRODUTOS_CUSTO = "PRODUTOS_CUSTO";
    public static final String ESTOQUE = "ESTOQUE";
    public static final String RESUMO_DIA = "RESUMO_DIA";
    public static final String LUCRO_CUSTO = "LUCRO_CUSTO";
    public static final String FINANCEIRO = "FINANCEIRO";
    public static final String RELATORIOS = "RELATORIOS";
    public static final String FORNECEDORES = "FORNECEDORES";
    public static final String HISTORICO = "HISTORICO";
    public static final String USUARIOS = "USUARIOS";

    private TechCellPermissions() {}

    public static Set<String> padrao(String perfil) {
        String p = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if ("MASTER".equals(p) || "ADMINISTRADOR".equals(p) || "PROPRIETARIO".equals(p) || "PROPRIETÁRIO".equals(p)) {
            adicionarTudo(out); return out;
        }
        if ("GERENTE".equals(p)) {
            out.add(VENDER); out.add(CLIENTES); out.add(PRODUTOS); out.add(PRODUTOS_EDITAR);
            out.add(PRODUTOS_CUSTO); out.add(ESTOQUE); out.add(RESUMO_DIA); out.add(LUCRO_CUSTO);
            out.add(FINANCEIRO); out.add(RELATORIOS); out.add(FORNECEDORES); out.add(HISTORICO);
            return out;
        }
        out.add(VENDER); out.add(CLIENTES); out.add(PRODUTOS); out.add(PRODUTOS_EDITAR); out.add(PRODUTOS_CUSTO); out.add(RESUMO_DIA);
        return out;
    }

    public static Set<String> ler(Object raw, String perfil) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw instanceof List) {
            for (Object v : (List<?>) raw) { String x=normalizar(v); if(!x.isEmpty()) out.add(x); }
            return out;
        }
        if (raw instanceof Map) {
            for (Map.Entry<?,?> e : ((Map<?,?>)raw).entrySet()) {
                Object v=e.getValue(); if(v instanceof Boolean && (Boolean)v){String x=normalizar(e.getKey());if(!x.isEmpty())out.add(x);}
            }
            return out;
        }
        return padrao(perfil);
    }

    public static List<String> lista(Set<String> permissoes) { return new ArrayList<>(permissoes==null?new LinkedHashSet<>():permissoes); }
    public static boolean contem(Set<String> permissoes,String permissao){return permissoes!=null&&permissoes.contains(normalizar(permissao));}

    public static String resumo(Set<String> p) {
        if (p == null || p.isEmpty()) return "Sem permissões extras";
        List<String> nomes=new ArrayList<>();
        if(p.contains(VENDER))nomes.add("PDV");if(p.contains(PRODUTOS))nomes.add("Produtos");if(p.contains(PRODUTOS_EDITAR))nomes.add("Editar produtos");
        if(p.contains(PRODUTOS_CUSTO))nomes.add("Custo");if(p.contains(RESUMO_DIA))nomes.add("Resumo do dia");if(p.contains(ESTOQUE))nomes.add("Estoque");
        if(p.contains(FINANCEIRO))nomes.add("Financeiro");if(p.contains(RELATORIOS))nomes.add("Relatórios");if(p.contains(FORNECEDORES))nomes.add("Fornecedores");if(p.contains(HISTORICO))nomes.add("Vendas");
        StringBuilder b=new StringBuilder();for(String n:nomes){if(b.length()>0)b.append(" • ");b.append(n);}return b.toString();
    }

    private static void adicionarTudo(Set<String> out) {
        out.add(VENDER); out.add(CLIENTES); out.add(PRODUTOS); out.add(PRODUTOS_EDITAR); out.add(PRODUTOS_CUSTO); out.add(ESTOQUE);
        out.add(RESUMO_DIA); out.add(LUCRO_CUSTO); out.add(FINANCEIRO); out.add(RELATORIOS); out.add(FORNECEDORES); out.add(HISTORICO); out.add(USUARIOS);
    }
    private static String normalizar(Object v){return v==null?"":String.valueOf(v).trim().toUpperCase(Locale.ROOT);}
}
