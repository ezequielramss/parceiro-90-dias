package br.com.kallopolli.parceiro.classificacao;

import java.util.Optional;

public interface CategoriaRepository {

    /** Busca na tabela public.categorias pelo código (ex.: "ASSEDIO"). */
    Optional<Categoria> buscar(String codigo);
}
