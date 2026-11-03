package br.com.kallopolli.parceiro.classificacao.jdbc;

import br.com.kallopolli.parceiro.classificacao.Categoria;
import br.com.kallopolli.parceiro.classificacao.CategoriaRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Leitura da tabela public.categorias (migração 20261102190100_taxonomia_categorias.sql). */
@Repository
public class JdbcCategoriaRepository implements CategoriaRepository {

    private final JdbcClient jdbc;

    public JdbcCategoriaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Categoria> buscar(String codigo) {
        return jdbc.sql("select codigo, destino, urgente from public.categorias where codigo = :codigo")
                .param("codigo", codigo)
                .query((rs, n) -> new Categoria(rs.getString("codigo"), rs.getString("destino"), rs.getBoolean("urgente")))
                .optional();
    }
}
