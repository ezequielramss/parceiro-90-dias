package br.com.kallopolli.parceiro.classificacao.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.classificacao.CategoriasEmMemoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Lê a tabela categorias de um Postgres com as migrações aplicadas (o Supabase local) e confere
 * que a versão em memória usada nos testes tem os mesmos valores. Só leitura.
 * Roda só com TEST_DATABASE_URL, TEST_DATABASE_USER e TEST_DATABASE_PASSWORD definidas.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcCategoriaRepositoryTest {

    @Test
    void tabelaTemOsMesmosValoresDaVersaoEmMemoria() {
        var repo = new JdbcCategoriaRepository(JdbcClient.create(new DriverManagerDataSource(
                System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"))));

        new CategoriasEmMemoria().todas().forEach((codigo, esperada) ->
                assertThat(repo.buscar(codigo)).as(codigo).contains(esperada));
        assertThat(repo.buscar("INEXISTENTE")).isEmpty();
    }
}
