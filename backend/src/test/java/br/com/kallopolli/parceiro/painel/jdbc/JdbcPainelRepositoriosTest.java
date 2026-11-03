package br.com.kallopolli.parceiro.painel.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.painel.Acesso;
import br.com.kallopolli.parceiro.painel.PainelRepository.Contagem;
import br.com.kallopolli.parceiro.painel.RetencaoRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL do painel e da retenção contra um Postgres com as migrações (o Supabase local). Roda só com
 * TEST_DATABASE_URL, TEST_DATABASE_USER e TEST_DATABASE_PASSWORD; cada caso é desfeito no fim.
 * Dados fictícios de duas lojas.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcPainelRepositoriosTest {

    private static final UUID COLAB_A = UUID.fromString("00000000-0000-0000-0000-00000000fa01");
    private static final UUID COLAB_B = UUID.fromString("00000000-0000-0000-0000-00000000fb01");
    private static final UUID SEM_ACEITE = UUID.fromString("00000000-0000-0000-0000-00000000fa02");
    private static final UUID GERENTE_A = UUID.fromString("00000000-0000-0000-0000-00000000f0a1");
    private static final UUID RH = UUID.fromString("00000000-0000-0000-0000-00000000f0a2");
    private static final Acesso ACESSO_RH = new Acesso(UUID.randomUUID(), Acesso.RH, null, "RH Ficticio");
    private static final Acesso ACESSO_GERENTE_A =
            new Acesso(UUID.randomUUID(), Acesso.GERENTE, "loja centro", "Gerente Ficticio");
    private static final Instant AGORA = Instant.now();

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
            cenario();
            teste.run();
        });
    }

    private static OffsetDateTime ha(Duration duracao) {
        return AGORA.minus(duracao).atOffset(ZoneOffset.UTC);
    }

    private long relato(UUID colaborador, String wamid, String categoria, Duration idade, boolean sinaisGraves) {
        long mensagem = jdbc.sql("""
                        insert into public.mensagens (wamid, colaborador_id, direcao, tipo, texto, criada_em)
                        values (:wamid, :colaborador, 'ENTRADA', 'text', 'TEXTO-FICTICIO', :quando) returning id""")
                .param("wamid", wamid).param("colaborador", colaborador).param("quando", ha(idade))
                .query(Long.class).single();
        return jdbc.sql("""
                        insert into public.relatos (colaborador_id, mensagem_id, texto, categoria, motivo, acao_sugerida,
                                                    sinais_graves, criado_em)
                        values (:colaborador, :mensagem, 'TEXTO-FICTICIO', :categoria, 'MOTIVO-FICTICIO', 'ACAO-FICTICIA',
                                :graves, :quando) returning id""")
                .param("colaborador", colaborador).param("mensagem", mensagem).param("categoria", categoria)
                .param("graves", sinaisGraves).param("quando", ha(idade))
                .query(Long.class).single();
    }

    private long alerta(long relato, String destino, boolean urgente, Duration idade) {
        return jdbc.sql("""
                        insert into public.alertas (relato_id, destino, urgente, criado_em)
                        values (:relato, :destino, :urgente, :quando) returning id""")
                .param("relato", relato).param("destino", destino).param("urgente", urgente).param("quando", ha(idade))
                .query(Long.class).single();
    }

    private void entrega(long alerta, UUID responsavel, String resposta, Duration idadeEnvio, Duration idadeResposta) {
        jdbc.sql("""
                        insert into public.alerta_entregas (alerta_id, responsavel_id, enviado_em, resposta, respondido_em)
                        values (:alerta, :responsavel, :enviado, :resposta, :respondido)""")
                .param("alerta", alerta).param("responsavel", responsavel).param("enviado", ha(idadeEnvio))
                .param("resposta", resposta).param("respondido", resposta == null ? null : ha(idadeResposta))
                .update();
    }

    private void cenario() {
        LocalDate hoje = LocalDate.now();
        jdbc.sql("""
                        insert into public.colaboradores (id, nome, telefone, loja, turno, data_inicio) values
                          (:a, 'Pessoa Ficticia Centro', '+5511999990901', ' Loja  CENTRO ', 'Noite', :inicio),
                          (:b, 'Pessoa Ficticia Norte', '+5511999990902', 'Loja Norte', 'Manhã', :inicio),
                          (:sem, 'Pessoa Ficticia Sem Aceite', '+5511999990903', 'Loja Centro', null, :inicio)""")
                .param("a", COLAB_A).param("b", COLAB_B).param("sem", SEM_ACEITE).param("inicio", hoje.minusDays(3))
                .update();
        jdbc.sql("""
                        insert into public.consentimentos (colaborador_id, texto_aceito) values
                          (:a, 'Termo ficticio'), (:b, 'Termo ficticio')""")
                .param("a", COLAB_A).param("b", COLAB_B).update();
        jdbc.sql("""
                        insert into public.responsaveis (id, nome, telefone, papel, loja) values
                          (:g, 'Gerente Ficticio', '+5511999990911', 'GERENTE', 'Loja Centro'),
                          (:rh, 'RH Ficticio', '+5511999990912', 'RH', null)""")
                .param("g", GERENTE_A).param("rh", RH).update();
        jdbc.sql("""
                        insert into public.checkins (colaborador_id, dia_jornada, enviado_em, respondido_em, humor) values
                          (:a, 1, :ontem, :ontem, 2), (:b, 1, :ontem, :ontem, 5), (:sem, 1, :ontem, null, null)""")
                .param("a", COLAB_A).param("b", COLAB_B).param("sem", SEM_ACEITE).param("ontem", ha(Duration.ofDays(1)))
                .update();

        long cansacoA = alerta(relato(COLAB_A, "wamid.PNL-1", "CANSACO", Duration.ofHours(30), false), "GERENTE",
                false, Duration.ofHours(30));
        entrega(cansacoA, GERENTE_A, null, Duration.ofHours(30), null);
        long assedioA = alerta(relato(COLAB_A, "wamid.PNL-2", "ASSEDIO", Duration.ofHours(5), false), "RH", true,
                Duration.ofHours(5));
        entrega(assedioA, RH, "CONVERSEI", Duration.ofHours(5), Duration.ofHours(4));
        alerta(relato(COLAB_A, "wamid.PNL-3", "SOBRECARGA", Duration.ofHours(4), true), "RH", true,
                Duration.ofHours(4));
        relato(COLAB_A, "wamid.PNL-4", "ELOGIO", Duration.ofHours(3), false);
        relato(COLAB_A, "wamid.PNL-5", "DUVIDA", Duration.ofHours(2), false);
        alerta(relato(COLAB_B, "wamid.PNL-6", "VONTADE_DE_SAIR", Duration.ofHours(6), false), "GERENTE_E_RH", false,
                Duration.ofHours(6));
    }

    @Test
    void gerenteVeSoASuaLojaSemMotivoNemRestritas() {
        emTransacaoDesfeita(() -> {
            var repo = new JdbcPainelRepositorios(jdbc);

            assertThat(repo.colaboradores(ACESSO_GERENTE_A)).extracting(c -> c.id()).containsExactly(COLAB_A);
            assertThat(repo.colaborador(ACESSO_GERENTE_A, COLAB_B)).isEmpty();
            assertThat(repo.colaborador(ACESSO_GERENTE_A, SEM_ACEITE)).isEmpty();
            assertThat(repo.colaborador(ACESSO_GERENTE_A, COLAB_A)).get()
                    .satisfies(c -> assertThat(c.ultimoContato()).isNotNull());
            assertThat(repo.checkins(ACESSO_GERENTE_A, null)).extracting(k -> k.colaboradorId())
                    .containsOnly(COLAB_A);

            var alertas = repo.alertas(ACESSO_GERENTE_A, AGORA, null, null, null);
            assertThat(alertas).singleElement().satisfies(a -> {
                assertThat(a.categoria()).isEqualTo("CANSACO");
                assertThat(a.status()).isEqualTo("SEM_RETORNO_24H");
                assertThat(a.motivo()).isNull();
                assertThat(a.colaboradorId()).isNull();
                assertThat(a.acaoSugerida()).isEqualTo("ACAO-FICTICIA");
                assertThat(a.destinatarios()).isEqualTo("Gerente Ficticio");
            });
            assertThat(repo.alertas(ACESSO_GERENTE_A, AGORA, "ABERTO", null, null)).isEmpty();
            assertThat(repo.alertas(ACESSO_GERENTE_A, AGORA, null, "RH", null)).isEmpty();

            assertThat(repo.relatos(ACESSO_GERENTE_A, null)).isEmpty();
            assertThat(repo.relatosBemEstar(ACESSO_GERENTE_A)).isEmpty();
            assertThat(repo.distribuicao(ACESSO_GERENTE_A))
                    .containsExactlyInAnyOrder(new Contagem("CANSACO", 1), new Contagem("ELOGIO", 1));

            var semLoja = new Acesso(UUID.randomUUID(), Acesso.GERENTE, null, null);
            assertThat(repo.colaboradores(semLoja)).isEmpty();
            assertThat(repo.alertas(semLoja, AGORA, null, null, null)).isEmpty();
        });
    }

    @Test
    void rhVeTodasAsLojas() {
        emTransacaoDesfeita(() -> {
            var repo = new JdbcPainelRepositorios(jdbc);

            assertThat(repo.colaboradores(ACESSO_RH)).extracting(c -> c.id())
                    .containsExactlyInAnyOrder(COLAB_A, COLAB_B);
            var alertas = repo.alertas(ACESSO_RH, AGORA, null, null, null);
            assertThat(alertas).hasSize(4);
            assertThat(alertas).filteredOn(a -> a.categoria().equals("ASSEDIO")).singleElement().satisfies(a -> {
                assertThat(a.colaboradorId()).isNull();
                assertThat(a.motivo()).isEqualTo("MOTIVO-FICTICIO");
                assertThat(a.retorno()).isEqualTo("CONVERSEI");
                assertThat(a.respondidoEm()).isNotNull();
            });
            assertThat(alertas).filteredOn(a -> a.categoria().equals("VONTADE_DE_SAIR")).singleElement()
                    .satisfies(a -> assertThat(a.colaboradorId()).isEqualTo(COLAB_B));
            assertThat(repo.alertas(ACESSO_RH, AGORA, null, "GERENTE", null)).hasSize(2);
            assertThat(repo.alertas(ACESSO_RH, AGORA, null, null, COLAB_B)).hasSize(1);

            assertThat(repo.relatos(ACESSO_RH, null)).extracting(r -> r.categoria())
                    .containsExactlyInAnyOrder("ASSEDIO", "SOBRECARGA", "VONTADE_DE_SAIR");
            assertThat(repo.relatos(ACESSO_RH, "CONCLUIDO")).isEmpty();
            assertThat(repo.relatosBemEstar(ACESSO_RH)).extracting(r -> r.categoria())
                    .containsExactlyInAnyOrder("CANSACO", "SOBRECARGA", "ELOGIO", "VONTADE_DE_SAIR")
                    .doesNotContain("ASSEDIO", "DUVIDA");
            assertThat(repo.relatosBemEstar(ACESSO_RH)).allSatisfy(r -> assertThat(r.texto()).isNull());
            assertThat(repo.distribuicao(ACESSO_RH)).extracting(Contagem::categoria)
                    .containsExactlyInAnyOrder("CANSACO", "ASSEDIO", "SOBRECARGA", "ELOGIO", "VONTADE_DE_SAIR");
        });
    }

    @Test
    void acessoVemDaTabela() {
        emTransacaoDesfeita(() -> {
            var repo = new JdbcPainelRepositorios(jdbc);
            UUID usuario = UUID.fromString("00000000-0000-0000-0000-00000000f0b1");
            jdbc.sql("insert into auth.users (id, email) values (:id, 'gerente.jdbc@exemplo.invalid')")
                    .param("id", usuario).update();
            assertThat(repo.buscarAcesso(usuario)).isEmpty();
            jdbc.sql("""
                            insert into public.acessos_dashboard (user_id, papel, loja, nome)
                            values (:id, 'GERENTE', public.normalizar_loja(' Loja  Centro '), 'Gerente Ficticio')""")
                    .param("id", usuario).update();
            assertThat(repo.buscarAcesso(usuario))
                    .contains(new Acesso(usuario, Acesso.GERENTE, "loja centro", "Gerente Ficticio"));
        });
    }

    @Test
    void retencaoApagaTextoEMotivoDepoisDe90DiasEContinuaContando() {
        emTransacaoDesfeita(() -> {
            var repo = new JdbcPainelRepositorios(jdbc);
            long novo = relato(COLAB_A, "wamid.PNL-89", "CANSACO", Duration.ofDays(89), false);
            alerta(novo, "GERENTE", false, Duration.ofDays(89));
            long antigo = relato(COLAB_A, "wamid.PNL-91", "CANSACO", Duration.ofDays(91), false);
            alerta(antigo, "GERENTE", false, Duration.ofDays(91));
            var antes = repo.distribuicao(ACESSO_GERENTE_A);

            var apagados = repo.apagarTextosAntesDe(AGORA.minus(Duration.ofDays(90)));

            assertThat(apagados).isEqualTo(new RetencaoRepository.Apagados(1, 1));
            assertThat(textoEMotivo(novo)).containsExactly("TEXTO-FICTICIO", "MOTIVO-FICTICIO");
            assertThat(textoEMotivo(antigo)).containsExactly(null, null);
            assertThat(jdbc.sql("select categoria from public.relatos where id = :id").param("id", antigo)
                    .query(String.class).single()).isEqualTo("CANSACO");
            assertThat(jdbc.sql("""
                            select m.texto is null from public.mensagens m join public.relatos r on r.mensagem_id = m.id
                            where r.id = :id""").param("id", antigo).query(Boolean.class).single()).isTrue();
            assertThat(repo.distribuicao(ACESSO_GERENTE_A)).isEqualTo(antes);
            assertThat(antes).contains(new Contagem("CANSACO", 3));

            assertThat(repo.apagarTextosAntesDe(AGORA.minus(Duration.ofDays(90))))
                    .isEqualTo(new RetencaoRepository.Apagados(0, 0));
        });
    }

    private static java.util.List<String> textoEMotivo(long relato) {
        return jdbc.sql("select texto, motivo from public.relatos where id = :id")
                .param("id", relato)
                .query((rs, n) -> java.util.Arrays.asList(rs.getString("texto"), rs.getString("motivo")))
                .single();
    }
}
