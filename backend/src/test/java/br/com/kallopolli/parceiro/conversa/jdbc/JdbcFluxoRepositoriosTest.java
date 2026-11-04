package br.com.kallopolli.parceiro.conversa.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.alerta.AlertaEntregaRepository;
import br.com.kallopolli.parceiro.alerta.jdbc.JdbcAlertaRepositorios;
import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.RelatoRepository;
import br.com.kallopolli.parceiro.jornada.jdbc.JdbcRepositorios;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL dos repositórios do fluxo contra um Postgres com as migrações (o Supabase local).
 * Roda só com TEST_DATABASE_URL, TEST_DATABASE_USER e TEST_DATABASE_PASSWORD; cada caso é
 * desfeito no fim. Dados fictícios.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcFluxoRepositoriosTest {

    private static final UUID COLABORADOR = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 2);

    private static JdbcClient jdbc;
    private static TransactionTemplate transacao;

    @BeforeAll
    static void conectar() {
        var ds = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = JdbcClient.create(ds);
        transacao = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    private void emTransacaoDesfeita(Runnable teste) {
        transacao.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.sql("""
                    insert into public.colaboradores (id, nome, telefone, loja, turno, data_inicio)
                    values (:id, 'Pessoa Ficticia', '+5511999990801', ' Loja  Teste ', 'Noite', :inicio)""")
                    .param("id", COLABORADOR).param("inicio", INICIO).update();
            teste.run();
        });
    }

    @Test
    void conversaCheckinERelato() {
        emTransacaoDesfeita(() -> {
            var conversa = new JdbcConversaRepositorios(jdbc);
            var jornada = new JdbcRepositorios(jdbc);

            assertThat(conversa.estadoDe(COLABORADOR)).isEqualTo(EstadoConversa.LIVRE);
            assertThat(jornada.ativosComAceiteIniciadosEntre(INICIO, INICIO)).isEmpty();
            jornada.registrarAceite(COLABORADOR, "Termo ficticio");
            assertThat(jornada.ativosComAceiteIniciadosEntre(INICIO.minusDays(9), INICIO))
                    .singleElement().satisfies(c -> {
                        assertThat(c.loja()).isEqualTo(" Loja  Teste ");
                        assertThat(c.dataInicio()).isEqualTo(INICIO);
                        assertThat(c.primeiroNome()).isEqualTo("Pessoa");
                    });

            long checkin = conversa.criarSeNovo(COLABORADOR, 1).orElseThrow();
            assertThat(conversa.criarSeNovo(COLABORADOR, 1)).isEmpty();
            conversa.definir(COLABORADOR, EstadoConversa.aguardandoHumor(checkin));
            assertThat(conversa.estadoDe(COLABORADOR)).isEqualTo(EstadoConversa.aguardandoHumor(checkin));
            conversa.registrarHumor(checkin, 4);

            long mensagem = jornada.registrarEntrada("wamid.JDBC-FLX-1", COLABORADOR, "text", "relato ficticio")
                    .orElseThrow();
            long relato = conversa.criar(COLABORADOR, mensagem, "relato ficticio");
            conversa.gravarClassificacao(relato, new Classificacao("CANSACO", "m", "a", "alta", false, false));
            conversa.atualizarTexto(relato, "relato ficticio corrigido");
            conversa.definir(COLABORADOR, EstadoConversa.aguardandoConfirmacao(relato));
            assertThat(conversa.buscarRelato(relato)).get().satisfies(r -> {
                assertThat(r.classificacao().categoria()).isEqualTo("CANSACO");
                assertThat(r.confirmacao()).isEqualTo(RelatoRepository.AGUARDANDO);
            });
            assertThat(conversa.descartarAguardando(COLABORADOR)).isEqualTo(1);
            conversa.definir(COLABORADOR, EstadoConversa.LIVRE);

            long falha = conversa.criar(COLABORADOR, mensagem, "outro relato ficticio");
            conversa.gravarClassificacao(falha, Classificacao.falha());
            conversa.definirConfirmacao(falha, RelatoRepository.CONFIRMADO);
            assertThat(conversa.buscarRelato(falha)).get().extracting(r -> r.classificacao().falhaIa()).isEqualTo(true);
        });
    }

    @Test
    void responsaveisAlertasEEntregas() {
        emTransacaoDesfeita(() -> {
            var repos = new JdbcAlertaRepositorios(jdbc);
            var conversa = new JdbcConversaRepositorios(jdbc);
            var jornada = new JdbcRepositorios(jdbc);
            jdbc.sql("""
                    insert into public.responsaveis (nome, telefone, papel, loja) values
                      ('Gerente Ficticio', '+5511999990802', 'GERENTE', 'LOJA TESTE'),
                      ('Gerente Outra', '+5511999990803', 'GERENTE', 'Loja Norte'),
                      ('RH Ficticio', '+5511999990802', 'RH', null)""").update();

            assertThat(repos.gerentesAtivosDaLoja(" Loja  Teste ")).extracting("telefone").containsExactly("+5511999990802");
            assertThat(repos.rhAtivos()).extracting("papel").containsExactly("RH");
            assertThat(repos.ativosPorTelefones(List.of("+5511999990802", "+551199990802"))).hasSize(2);
            assertThat(repos.ativosPorTelefones(List.of())).isEmpty();
            var rh = repos.rhAtivos().get(0);
            assertThat(repos.buscarResponsavel(rh.id())).contains(rh);

            long mensagem = jornada.registrarEntrada("wamid.JDBC-FLX-2", COLABORADOR, "text", "relato ficticio")
                    .orElseThrow();
            long relato = conversa.criar(COLABORADOR, mensagem, "relato ficticio");
            long alerta = repos.criar(relato, "RH", true);
            long entrega = repos.criar(alerta, rh.id());
            repos.registrarEnvio(entrega, "wamid.JDBC-ENTREGA");
            repos.responder(entrega, AlertaEntregaRepository.NAO_CONSEGUI);
            repos.responder(entrega, AlertaEntregaRepository.CONVERSEI);
            repos.marcarAtendido(alerta);
            assertThat(repos.buscarEntrega(entrega)).get().satisfies(e -> {
                assertThat(e.resposta()).isEqualTo("CONVERSEI");
                assertThat(e.falhaEnvio()).isFalse();
            });
            assertThat(jdbc.sql("select estado from public.alertas where id = :id").param("id", alerta)
                    .query(String.class).single()).isEqualTo("ATENDIDO");

            var gerente = repos.gerentesAtivosDaLoja("Loja Teste").get(0);
            long falhou = repos.criar(alerta, gerente.id());
            repos.marcarFalha(falhou);
            assertThat(repos.buscarEntrega(falhou)).get().extracting(AlertaEntregaRepository.Entrega::falhaEnvio)
                    .isEqualTo(true);
        });
    }

    @Test
    void entregaMaisRecenteAbertaEListasDoSimulador() {
        emTransacaoDesfeita(() -> {
            var repos = new JdbcAlertaRepositorios(jdbc);
            var conversa = new JdbcConversaRepositorios(jdbc);
            var jornada = new JdbcRepositorios(jdbc);
            jdbc.sql("""
                    insert into public.responsaveis (nome, telefone, papel, loja) values
                      ('Gerente Ficticio', '+5511999990804', 'GERENTE', 'Loja Teste'),
                      ('RH Ficticio', '+5511999990804', 'RH', null),
                      ('Inativo Ficticio', '+5511999990805', 'RH', null)""").update();
            jdbc.sql("update public.responsaveis set ativo = false where telefone = '+5511999990805'").update();
            var doNumero = repos.ativosPorTelefones(List.of("+5511999990804"));
            var ids = doNumero.stream().map(r -> r.id()).toList();
            assertThat(repos.maisRecenteAberta(ids)).isEmpty();
            assertThat(repos.maisRecenteAberta(List.of())).isEmpty();

            long mensagem = jornada.registrarEntrada("wamid.JDBC-FLX-3", COLABORADOR, "text", "relato ficticio")
                    .orElseThrow();
            long relato = conversa.criar(COLABORADOR, mensagem, "relato ficticio");
            long alerta = repos.criar(relato, "GERENTE_E_RH", false);
            long antiga = repos.criar(alerta, doNumero.get(0).id());
            long nova = repos.criar(alerta, doNumero.get(1).id());
            jdbc.sql("update public.alerta_entregas set enviado_em = now() - interval '1 hour' where id = :id")
                    .param("id", antiga).update();

            assertThat(repos.maisRecenteAberta(ids)).contains(nova);
            repos.responder(nova, AlertaEntregaRepository.NAO_CONSEGUI);
            assertThat(repos.maisRecenteAberta(ids)).as("NÃO CONSEGUI continua aberta").contains(nova);
            repos.responder(nova, AlertaEntregaRepository.CONVERSEI);
            assertThat(repos.maisRecenteAberta(ids)).contains(antiga);
            repos.marcarFalha(antiga);
            assertThat(repos.maisRecenteAberta(ids)).as("entrega com falha não recebe resposta").isEmpty();

            assertThat(repos.responsaveisAtivos()).extracting("telefone")
                    .contains("+5511999990804").doesNotContain("+5511999990805");
            assertThat(jornada.colaboradoresAtivos()).extracting("id").contains(COLABORADOR);
        });
    }
}
