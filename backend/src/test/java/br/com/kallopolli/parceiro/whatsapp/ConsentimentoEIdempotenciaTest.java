package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.consentimento.EnvioAcompanhamento;
import br.com.kallopolli.parceiro.consentimento.Textos;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ConsentimentoEIdempotenciaTest extends WebhookTestBase {

    private static final String NUMERO = "5500000000201";

    @Autowired
    EnvioAcompanhamento envioAcompanhamento;

    private int sequencia;

    @Test
    void mensagemRepetidaNaoGeraRegistroNemResposta(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        String corpo = payloadTexto("wamid.REPETIDA", NUMERO, "oi");

        mvc.perform(postAssinado(corpo)).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.REPETIDA");
        mvc.perform(postAssinado(corpo)).andExpect(status().isOk());
        esperarLog(saida, "REPETIDA_IGNORADA");

        assertThat(repositorios.mensagens()).filteredOn(m -> m.direcao().equals("ENTRADA")).hasSize(1);
        assertThat(respostasEnviadas()).hasSize(1);
    }

    @Test
    void quemNuncaAceitouRecebePedidoDeAceite(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        enviar(saida, "oi, tudo bem?");

        assertThat(respostasEnviadas()).singleElement().asString().contains("responda ACEITO");
        assertThat(repositorios.consentimentos()).isEmpty();
    }

    @Test
    void aceitoRegistraConsentimentoComOTextoDoTermo(CapturedOutput saida) throws Exception {
        Colaborador colaborador = repositorios.cadastrar("+" + NUMERO);

        enviar(saida, "Aceito!");

        assertThat(repositorios.consentimentos()).singleElement()
                .satisfies(c -> {
                    assertThat(c.ativo()).isTrue();
                    assertThat(c.texto()).isEqualTo(Textos.TERMO_V1);
                    assertThat(c.colaboradorId()).isEqualTo(colaborador.id());
                });
        assertThat(repositorios.auditoria()).extracting("acao").containsExactly("CONSENTIMENTO_ACEITO");
        assertThat(respostasEnviadas()).singleElement().asString().contains("aceite foi registrado");
    }

    @ParameterizedTest
    @ValueSource(strings = {"parar", "PARAR", "Parar", "Parar!", "  parar  ", "Párar", "PÁRAR.", "parar\\n", "pArAr..."})
    void pararComVariacoesRetiraOAceiteEConfirma(String texto, CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        enviar(saida, "aceito");
        GRAPH_API.recebidas().clear();

        enviar(saida, texto);

        assertThat(repositorios.consentimentos()).singleElement().extracting("ativo").isEqualTo(false);
        assertThat(repositorios.auditoria()).extracting("acao")
                .containsExactly("CONSENTIMENTO_ACEITO", "CONSENTIMENTO_RETIRADO");
        assertThat(respostasEnviadas()).singleElement().asString().contains("não vai mais receber");
    }

    @ParameterizedTest
    @ValueSource(strings = {"não quero parar", "parar de trabalhar", "parei", "pare"})
    void frasesQueSoContemPararNaoRetiram(String texto, CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        enviar(saida, "aceito");

        enviar(saida, texto);

        assertThat(repositorios.consentimentos()).singleElement().extracting("ativo").isEqualTo(true);
    }

    @Test
    void depoisDePararPodeAceitarDeNovo(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        enviar(saida, "aceito");
        enviar(saida, "parar");

        enviar(saida, "aceito");

        assertThat(repositorios.consentimentos()).extracting("ativo").containsExactly(false, true);
    }

    @Test
    void remetenteNaoCadastradoNaoRecebeNadaNemEGravado(CapturedOutput saida) throws Exception {
        enviar(saida, "oi");

        assertThat(GRAPH_API.recebidas().poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(repositorios.mensagens()).isEmpty();
        assertThat(saida.getAll()).contains("REMETENTE_NAO_CADASTRADO");
    }

    @Test
    void acompanhamentoSoSaiParaQuemAceitou(CapturedOutput saida) throws Exception {
        Colaborador colaborador = repositorios.cadastrar("+" + NUMERO);

        assertThat(envioAcompanhamento.enviar(colaborador, "Como foi seu dia?")).isFalse();
        assertThat(GRAPH_API.recebidas().poll(200, TimeUnit.MILLISECONDS)).isNull();

        enviar(saida, "aceito");
        GRAPH_API.recebidas().clear();

        assertThat(envioAcompanhamento.enviar(colaborador, "Como foi seu dia?")).isTrue();
        assertThat(respostasEnviadas()).singleElement().asString().contains("Como foi seu dia?");
    }

    @Test
    void mensagensDeSaidaFicamRegistradasComOWamidDaMeta(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        GRAPH_API.responder(200, "{\"messages\":[{\"id\":\"wamid.SAIDA-1\"}]}");

        enviar(saida, "oi");

        assertThat(repositorios.mensagens()).filteredOn(m -> m.direcao().equals("SAIDA"))
                .singleElement().extracting("wamid").isEqualTo("wamid.SAIDA-1");
    }

    private void enviar(CapturedOutput saida, String texto) throws Exception {
        String wamid = "wamid.CONS-" + (++sequencia) + "-" + System.nanoTime();
        mvc.perform(postAssinado(payloadTexto(wamid, NUMERO, texto))).andExpect(status().isOk());
        esperarProcessamento(saida, wamid);
    }

    /** Corpos das mensagens que chegaram à Graph API falsa. */
    private List<String> respostasEnviadas() {
        List<GraphApiFalsa.Requisicao> recebidas = new ArrayList<>();
        GRAPH_API.recebidas().drainTo(recebidas);
        return recebidas.stream().map(GraphApiFalsa.Requisicao::corpo).toList();
    }

    private static void esperarLog(CapturedOutput saida, String trecho) throws InterruptedException {
        for (int i = 0; i < 200 && !saida.getAll().contains(trecho); i++) {
            Thread.sleep(25);
        }
        assertThat(saida.getAll()).contains(trecho);
    }
}
