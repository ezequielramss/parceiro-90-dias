package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Agendador de check-in (H04) com relógio fixo; o @Scheduled fica desligado nos testes. */
@ExtendWith(OutputCaptureExtension.class)
class AgendadorCheckinTest extends FluxoTestBase {

    private static final LocalDate INICIO = RepositoriosEmMemoria.INICIO;

    @Test
    void diasDe1A10EnviamUmaVezPorDiaMesmoComVariosTicks(CapturedOutput saida) throws Exception {
        cenario(saida);

        for (int dia = 1; dia <= 10; dia++) {
            var agendador = agendador(INICIO.plusDays(dia - 1));
            agendador.tick();
            agendador.tick();
            agendador.tick();
        }

        assertThat(repositorios.checkins()).extracting("dia")
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        var enviadas = enviadas();
        assertThat(enviadas).hasSize(10).allSatisfy(e -> assertThat(e.texto()).contains("nota de 1 a 5"));
        assertThat(enviadas.get(0).texto()).contains("Parceiro dos 90 dias");
        assertThat(repositorios.estadoDe(colaborador.id()).tipo()).isEqualTo(EstadoConversa.Tipo.AGUARDANDO_HUMOR);
    }

    @Test
    void dia11NaoEnvia(CapturedOutput saida) throws Exception {
        cenario(saida);

        agendador(INICIO.plusDays(10)).tick();

        assertThat(repositorios.checkins()).isEmpty();
        assertThat(enviadas()).isEmpty();
    }

    @Test
    void antesDoInicioNaoEnvia(CapturedOutput saida) throws Exception {
        cenario(saida);

        agendador(INICIO.minusDays(1)).tick();

        assertThat(repositorios.checkins()).isEmpty();
    }

    @Test
    void semAceiteNaoEnvia() throws Exception {
        repositorios.cadastrar("+" + COLABORADOR);

        agendador(INICIO).tick();

        assertThat(repositorios.checkins()).isEmpty();
        assertThat(enviadas()).isEmpty();
    }

    @Test
    void janelaFechadaRegistraFalhaENaoTentaDeNovoNoMesmoDia(CapturedOutput saida) throws Exception {
        cenario(saida);
        GRAPH_API.fecharJanela(COLABORADOR);
        var agendador = agendador(INICIO);

        agendador.tick();
        agendador.tick();

        assertThat(repositorios.checkins()).hasSize(1);
        assertThat(enviadas()).as("só uma tentativa no dia").hasSize(1);
        assertThat(acoesAuditadas()).containsOnlyOnce("CHECKIN_FALHA_ENVIO").doesNotContain("CHECKIN_ENVIADO");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
    }

    @Test
    void quemEstaConfirmandoRelatoRecebeOCheckinDepois(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "relato ficticio");
        enviadas();
        var agendador = agendador(INICIO);

        agendador.tick();
        assertThat(repositorios.checkins()).isEmpty();

        texto(saida, "não");
        enviadas();
        agendador.tick();
        assertThat(repositorios.checkins()).hasSize(1);
    }
}
