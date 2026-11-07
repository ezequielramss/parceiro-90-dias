package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.agentes.Agente;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

/**
 * Um agente novo é só um bean: este, registrado só no teste, aparece no menu e recebe as mensagens
 * sem mudança no orquestrador. O Elogio, desligado por configuração, some do menu. A triagem também
 * está desligada aqui: o classificador proibido prova que nenhum texto solto vai à IA.
 */
@TestPropertySource(properties = {"parceiro.agentes.elogio.ativo=false", "parceiro.menu.texto-longo-caracteres=20",
        "parceiro.menu.triagem-sinais-graves=false"})
@ExtendWith(OutputCaptureExtension.class)
class AgentesExtensibilidadeTest extends FluxoTestBase {

    static class AgenteFalso implements Agente {
        final List<String> recebidas = new CopyOnWriteArrayList<>();
        private final MensageiroWhatsApp mensageiro;

        AgenteFalso(MensageiroWhatsApp mensageiro) {
            this.mensageiro = mensageiro;
        }

        @Override
        public String codigo() {
            return "FALSO";
        }

        @Override
        public String nome() {
            return "Opcao ficticia";
        }

        @Override
        public int ordem() {
            return 15;
        }

        @Override
        public String abertura() {
            return "Abertura ficticia";
        }

        @Override
        public Desfecho tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
            recebidas.add(mensagem.conteudo());
            mensageiro.enviar(colaborador, "Resposta ficticia");
            return "fim".equals(mensagem.conteudo()) ? Desfecho.livre("FALSO_FIM") : Desfecho.continua("FALSO_SEGUE");
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        AgenteFalso agenteFalso(MensageiroWhatsApp mensageiro) {
            return new AgenteFalso(mensageiro);
        }
    }

    @Autowired
    AgenteFalso agente;

    @Test
    void agenteRegistradoAparecNoMenuNaOrdemERecebeAsMensagens(CapturedOutput saida) throws Exception {
        agente.recebidas.clear();
        cenario(saida);

        texto(saida, "oi");
        assertThat(enviadas()).singleElement().extracting("texto").asString()
                .endsWith("1 - Contar algo (relato ou reclamação)\n2 - Opcao ficticia\n\n" + TextosConversa.avisoUrgencia(1))
                .doesNotContain("elogio");

        texto(saida, "2");
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo("Abertura ficticia");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.emAgente("FALSO"));

        texto(saida, "primeira");
        texto(saida, "segunda");
        assertThat(agente.recebidas).containsExactly("primeira", "segunda");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.emAgente("FALSO"));
        assertThat(repositorios.relatos()).isEmpty();

        texto(saida, "fim");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
    }

    /** Com o limite no mínimo (20), o que é resposta curta ou escolha não vira aviso; o resto, sim. */
    @Test
    void limiteBaixoNaoAtingeEscolhaNotaMenuNemConfirmacao(CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, "x".repeat(21));
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.avisoTextoLongo(1));

        texto(saida, "x".repeat(20));
        assertThat(enviadas()).singleElement().extracting("texto").asString().contains(TextosConversa.PEDIDO_NUMERO);

        // Escolha pelo nome, com mais de 20 caracteres.
        texto(saida, "Contar algo (relato ou reclamação)");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.emAgente("RELATO"));
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.aberturaRelato());

        texto(saida, "relato ficticio com mais de vinte caracteres");
        enviadas();
        texto(saida, "sim");
        assertThat(repositorios.alertas()).hasSize(1);
        enviadas();

        // No check-in, a nota e o nome da opção não viram aviso.
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();
        texto(saida, "4");
        assertThat(repositorios.checkins()).singleElement().extracting("humor").isEqualTo(4);
        enviadas();

        repositorios.definir(colaborador.id(), EstadoConversa.aguardandoHumor(repositorios.checkins().get(0).id()));
        texto(saida, "Contar algo (relato ou reclamação)");
        assertThat(enviadas()).singleElement().extracting("texto").asString().contains(TextosConversa.PEDIDO_NUMERO);
    }

    @Test
    void agenteDesligadoNaoAparecENaoPodeSerEscolhidoENemRecebeQuemEstavaNele(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();

        texto(saida, "fazer um elogio");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").asString().doesNotContain("elogio");

        // Estava no Elogio quando ele foi desligado: volta ao menu.
        repositorios.definir(colaborador.id(), EstadoConversa.emAgente("ELOGIO"));
        texto(saida, "elogio ficticio");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
        assertThat(repositorios.relatos()).isEmpty();
    }
}
