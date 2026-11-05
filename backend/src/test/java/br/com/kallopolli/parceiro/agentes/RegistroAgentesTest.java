package br.com.kallopolli.parceiro.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/** Registro de agentes: ordem, filtro, escolha e código duplicado. Sem banco e sem rede. */
class RegistroAgentesTest {

    private record AgenteDeTeste(String codigo, String nome, int ordem, boolean ativo) implements Agente {
        @Override
        public String abertura() {
            return "abertura";
        }

        @Override
        public Desfecho tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
            return Desfecho.livre("OK");
        }
    }

    private static final Agente A = new AgenteDeTeste("A", "Primeira opção", 10, true);
    private static final Agente B = new AgenteDeTeste("B", "Segunda opção", 20, true);
    private static final Agente DESLIGADO = new AgenteDeTeste("D", "Desligada", 5, false);

    private static RegistroAgentes registro(MockEnvironment ambiente, Agente... agentes) {
        return new RegistroAgentes(List.of(agentes), ambiente);
    }

    @Test
    void ordenaPelaOrdemDoAgenteENaoPelaOrdemDoSpring() {
        var r = registro(new MockEnvironment(), B, A);

        assertThat(r.ativos()).containsExactly(A, B);
        assertThat(r.menu()).endsWith("1 - Primeira opção\n2 - Segunda opção");
        assertThat(r.menuCurto()).endsWith("1 - Primeira opção\n2 - Segunda opção");
    }

    @Test
    void filtraPeloProprioAgenteEPelaConfiguracao() {
        var ambiente = new MockEnvironment().withProperty("parceiro.agentes.b.ativo", "false");

        var r = registro(ambiente, A, B, DESLIGADO);

        assertThat(r.ativos()).containsExactly(A);
        assertThat(r.porCodigo("B")).isEmpty();
        assertThat(r.escolher("Segunda opção")).isEmpty();
        assertThat(r.escolher("2")).isEmpty();
    }

    @Test
    void escolhePeloNumeroEPeloNomeIgnorandoAcentoEPontuacao() {
        var r = registro(new MockEnvironment(), A, B);

        assertThat(r.escolher("2")).contains(B);
        assertThat(r.escolher(" 2. ")).contains(B);
        assertThat(r.escolher("SEGUNDA OPCAO!")).contains(B);
        assertThat(r.escolher("b")).as("o código também vale").contains(B);
        assertThat(r.escolher("a segunda opcao por favor")).isEmpty();
        assertThat(r.numeroInvalido("3")).isTrue();
        assertThat(r.numeroInvalido("0")).isTrue();
        assertThat(r.numeroInvalido("oi")).isFalse();
        assertThat(r.numeroInvalido("2")).isFalse();
    }

    @Test
    void semNenhumAgenteAtivoOMenuAvisa() {
        var ambiente = new MockEnvironment().withProperty("parceiro.agentes.a.ativo", "false");

        var r = registro(ambiente, A);

        assertThat(r.menu()).contains("não há opções");
        assertThat(r.menuCurto()).contains("não há opções");
    }

    @Test
    void codigoDuplicadoRecusaComMensagemClara() {
        var outroA = new AgenteDeTeste("A", "Outra", 30, true);

        assertThatThrownBy(() -> registro(new MockEnvironment(), A, outroA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mesmo código A");
    }

    @Test
    void codigoDuplicadoEntreAgenteDesligadoTambemRecusa() {
        var ativoComOMesmoCodigo = new AgenteDeTeste("D", "Outra", 30, true);

        assertThatThrownBy(() -> registro(new MockEnvironment(), DESLIGADO, ativoComOMesmoCodigo))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void codigoDuplicadoDerrubaASubidaDoContexto() {
        new ApplicationContextRunner()
                .withBean("a", Agente.class, () -> A)
                .withBean("outroA", Agente.class, () -> new AgenteDeTeste("A", "Outra", 30, true))
                .withUserConfiguration(RegistroAgentes.class)
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining("mesmo código A");
                });
    }
}
