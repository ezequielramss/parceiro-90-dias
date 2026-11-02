package br.com.kallopolli.parceiro.jornada.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repositórios JDBC contra um Postgres com as migrações aplicadas (o Supabase local).
 * Roda só com TEST_DATABASE_URL, TEST_DATABASE_USER e TEST_DATABASE_PASSWORD definidas.
 * Cada teste roda numa transação desfeita no fim; dados fictícios.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriosTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final String TELEFONE = "+5500000000901";

    private static JdbcClient jdbc;
    private static TransactionTemplate transacao;

    @BeforeAll
    static void conectar() {
        var ds = new DriverManagerDataSource(
                System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = JdbcClient.create(ds);
        transacao = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    /** Roda o teste com um colaborador fictício e desfaz tudo no fim. */
    private void comColaborador(Consumer<JdbcRepositorios> teste) {
        transacao.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.sql("""
                    insert into public.colaboradores (id, nome, telefone, loja, data_inicio)
                    values (:id, 'Pessoa Ficticia', :telefone, 'Loja Teste', current_date)""")
                    .param("id", ID).param("telefone", TELEFONE).update();
            teste.accept(new JdbcRepositorios(jdbc));
        });
    }

    @Test
    void buscaColaboradorAtivoPeloTelefone() {
        comColaborador(repos -> {
            assertThat(repos.buscarAtivoPorTelefone(TELEFONE)).contains(new Colaborador(ID, TELEFONE));
            assertThat(repos.buscarAtivoPorTelefone("+5500000000999")).isEmpty();

            jdbc.sql("update public.colaboradores set ativo = false where id = :id").param("id", ID).update();
            assertThat(repos.buscarAtivoPorTelefone(TELEFONE)).isEmpty();
        });
    }

    @Test
    void wamidRepetidoNaoGravaDeNovo() {
        comColaborador(repos -> {
            assertThat(repos.registrarEntrada("wamid.JDBC-1", ID, "text", "texto ficticio")).isTrue();
            assertThat(repos.registrarEntrada("wamid.JDBC-1", ID, "text", "texto ficticio")).isFalse();
            assertThat(jdbc.sql("select count(*) from public.mensagens where wamid = 'wamid.JDBC-1'")
                    .query(Integer.class).single()).isEqualTo(1);
        });
    }

    @Test
    void aceiteERetirada() {
        comColaborador(repos -> {
            assertThat(repos.temAtivo(ID)).isFalse();
            assertThat(repos.registrarRetirada(ID)).isEmpty();

            long id = repos.registrarAceite(ID, "Termo ficticio");
            assertThat(repos.temAtivo(ID)).isTrue();

            assertThat(repos.registrarRetirada(ID)).contains(id);
            assertThat(repos.temAtivo(ID)).isFalse();
        });
    }

    @Test
    void segundoAceiteAtivoEhRecusadoPeloBanco() {
        comColaborador(repos -> {
            repos.registrarAceite(ID, "Termo ficticio");
            assertThatThrownBy(() -> repos.registrarAceite(ID, "Termo ficticio"))
                    .isInstanceOf(DataAccessException.class);
        });
    }

    @Test
    void auditoriaAceitaInsercao() {
        comColaborador(repos -> {
            repos.registrar("teste", "CONSENTIMENTO_ACEITO", "consentimento/1");
            assertThat(jdbc.sql("select count(*) from public.auditoria where quem = 'teste'")
                    .query(Integer.class).single()).isEqualTo(1);
        });
    }
}
