package br.com.kallopolli.parceiro.classificacao;

/** Linha da tabela public.categorias: para onde a categoria vai e se é urgente. */
public record Categoria(String codigo, String destino, boolean urgente) {}
