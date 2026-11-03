package br.com.kallopolli.parceiro.painel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Retenção de 90 dias contados da data do relato, com relógio fixo e dados fictícios. */
class RetencaoRelatosTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Instant AGORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, SP);
    private static final Acesso RH = new Acesso(java.util.UUID.randomUUID(), Acesso.RH, null, "RH Ficticio");

    private final PainelEmMemoria painel = new PainelEmMemoria();
    private final RepositoriosEmMemoria auditoria = new RepositoriosEmMemoria();
    private final RetencaoRelatos retencao = new RetencaoRelatos(painel, auditoria, RELOGIO);
    private final ServicoPainel servico = new ServicoPainel(painel, RELOGIO);

    @Test
    void relatoCom89DiasMantemETextoCom91DiasPerde() {
        var colaborador = painel.colaborador("Pessoa Ficticia", "Loja Teste", LocalDate.of(2026, 7, 1), null);
        var novo = painel.relato(colaborador.id(), "CANSACO", AGORA.minus(Duration.ofDays(89)),
                "TEXTO-FICTICIO-89", "MOTIVO-FICTICIO-89", "Vale uma conversa");
        painel.alerta(novo, "GERENTE", false, "ATENDIDO", AGORA.minus(Duration.ofDays(89)), "CONVERSEI",
                AGORA.minus(Duration.ofDays(88)));
        var antigo = painel.relato(colaborador.id(), "CANSACO", AGORA.minus(Duration.ofDays(91)),
                "TEXTO-FICTICIO-91", "MOTIVO-FICTICIO-91", "Vale uma conversa");
        painel.alerta(antigo, "GERENTE", false, "ATENDIDO", AGORA.minus(Duration.ofDays(91)), "CONVERSEI",
                AGORA.minus(Duration.ofDays(90)));
        var distribuicaoAntes = servico.bemEstar(RH).distribuicaoCategorias();

        var apagados = retencao.executar();

        assertThat(apagados).isEqualTo(new RetencaoRepository.Apagados(1, 1));
        assertThat(novo.texto).isEqualTo("TEXTO-FICTICIO-89");
        assertThat(novo.motivo).isEqualTo("MOTIVO-FICTICIO-89");
        assertThat(antigo.texto).isNull();
        assertThat(antigo.motivo).isNull();
        assertThat(antigo.categoria).isEqualTo("CANSACO");
        assertThat(antigo.criadoEm).isEqualTo(AGORA.minus(Duration.ofDays(91)));
        assertThat(painel.mensagens()).extracting(PainelEmMemoria.Mensagem::texto)
                .containsExactlyInAnyOrder("TEXTO-FICTICIO-89", null);

        // Continua contando nos indicadores.
        assertThat(servico.bemEstar(RH).distribuicaoCategorias()).isEqualTo(distribuicaoAntes);
        assertThat(distribuicaoAntes).singleElement()
                .satisfies(c -> assertThat(c.quantidade()).isEqualTo(2));
        assertThat(servico.alertas(RH, null, null)).hasSize(2);

        // A auditoria guarda só as quantidades.
        assertThat(auditoria.auditoria()).singleElement().satisfies(a -> {
            assertThat(a.acao()).isEqualTo("RETENCAO_90_DIAS");
            assertThat(a.objeto()).isEqualTo("relatos=1;mensagens=1");
        });
    }

    @Test
    void rodarDuasVezesNaoMudaNada() {
        var colaborador = painel.colaborador("Pessoa Ficticia", "Loja Teste", LocalDate.of(2026, 7, 1), null);
        painel.relato(colaborador.id(), "DUVIDA", AGORA.minus(Duration.ofDays(120)), "TEXTO-FICTICIO", "MOTIVO", null);

        assertThat(retencao.executar()).isEqualTo(new RetencaoRepository.Apagados(1, 1));
        assertThat(retencao.executar()).isEqualTo(new RetencaoRepository.Apagados(0, 0));
        assertThat(auditoria.auditoria()).hasSize(1);
    }

    @Test
    void prazoContaDaDataDoRelato() {
        assertThat(RetencaoRelatos.PRAZO).isEqualTo(Duration.ofDays(90));
        var colaborador = painel.colaborador("Pessoa Ficticia", "Loja Teste", LocalDate.of(2026, 7, 1), null);
        var noLimite = painel.relato(colaborador.id(), "CANSACO", AGORA.minus(Duration.ofDays(90)).plusSeconds(1),
                "TEXTO-FICTICIO", "MOTIVO", null);

        retencao.executar();

        assertThat(noLimite.texto).isEqualTo("TEXTO-FICTICIO");
    }
}
