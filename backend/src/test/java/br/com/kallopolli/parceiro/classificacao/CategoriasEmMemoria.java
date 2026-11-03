package br.com.kallopolli.parceiro.classificacao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Versão em memória da tabela public.categorias, com os mesmos valores da migração
 * 20261102190100_taxonomia_categorias.sql. O JdbcCategoriaRepositoryTest confere contra o banco.
 */
public class CategoriasEmMemoria implements CategoriaRepository {

    private final Map<String, Categoria> categorias = new LinkedHashMap<>();

    public CategoriasEmMemoria() {
        for (String c : new String[] {"CANSACO", "SOBRECARGA", "DESMOTIVACAO", "DUVIDA", "CONFLITO_COLEGA"}) {
            colocar(new Categoria(c, "GERENTE", false));
        }
        colocar(new Categoria("VONTADE_DE_SAIR", "GERENTE_E_RH", false));
        colocar(new Categoria("CONFLITO_CHEFIA", "RH", false));
        colocar(new Categoria("ASSEDIO", "RH", true));
        colocar(new Categoria("HUMILHACAO", "RH", true));
        colocar(new Categoria("SITUACAO_GRAVE", "RH", true));
        colocar(new Categoria("ELOGIO", "AGREGADO", false));
        colocar(new Categoria("NAO_CLASSIFICADO", "RH", false));
    }

    public void colocar(Categoria categoria) {
        categorias.put(categoria.codigo(), categoria);
    }

    public Map<String, Categoria> todas() {
        return Map.copyOf(categorias);
    }

    @Override
    public Optional<Categoria> buscar(String codigo) {
        return Optional.ofNullable(categorias.get(codigo));
    }
}
