package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
class EcoESegredosNosLogsTest extends WebhookTestBase {

    private static final String NUMERO_FICTICIO = "5500999990000";
    private static final String TEXTO_FICTICIO = "texto ficticio de teste";

    @Autowired
    MockMvc mvc;

    @Autowired
    WhatsAppClient client;

    @AfterEach
    void restaurarGraphApi() {
        GRAPH_API.responder(200, "{\"messages\":[{\"id\":\"wamid.RESPOSTA\"}]}");
        GRAPH_API.atrasar(0);
        GRAPH_API.recebidas().clear();
    }

    @Test
    void ecoRespondeComOTextoRecebido(CapturedOutput saida) throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTexto("wamid.ENTRADA1", NUMERO_FICTICIO, TEXTO_FICTICIO)))
                .andExpect(status().isOk());

        var req = GRAPH_API.recebidas().poll(5, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.metodo()).isEqualTo("POST");
        assertThat(req.caminho()).isEqualTo("/v99.0/" + PHONE_NUMBER_ID_FALSO + "/messages");
        assertThat(req.authorization()).isEqualTo("Bearer " + TOKEN_FALSO);
        assertThat(req.corpo())
                .contains("\"to\":\"" + NUMERO_FICTICIO + "\"")
                .contains("\"body\":\"Recebi: " + TEXTO_FICTICIO + "\"");

        esperarLog(saida, "eco enviado");
        assertThat(saida.getAll())
                .contains("mensagem recebida", "wamid.ENTRADA1", "\"tipo\":\"text\"")
                .doesNotContain(TOKEN_FALSO, VERIFY_TOKEN_FALSO, TEXTO_FICTICIO, NUMERO_FICTICIO);
    }

    @Test
    void erroDaMetaNaoVazaToken(CapturedOutput saida) throws Exception {
        // Pior caso: a resposta de erro repete o token na mensagem livre.
        GRAPH_API.responder(401, """
                {"error":{"message":"Invalid OAuth access token %s","type":"OAuthException","code":190}}
                """.formatted(TOKEN_FALSO));

        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTexto("wamid.ENTRADA2", NUMERO_FICTICIO, TEXTO_FICTICIO)))
                .andExpect(status().isOk());

        esperarLog(saida, "eco falhou");
        assertThat(saida.getAll())
                .contains("\"status_http\":401", "\"codigo_meta\":\"190\"")
                .doesNotContain(TOKEN_FALSO, TEXTO_FICTICIO, NUMERO_FICTICIO);
    }

    @Test
    void timeoutViraExcecaoSemToken(CapturedOutput saida) {
        GRAPH_API.atrasar(2_000);

        assertThatThrownBy(() -> client.enviarTexto(NUMERO_FICTICIO, TEXTO_FICTICIO))
                .isInstanceOf(WhatsAppClientException.class)
                .hasMessageContaining("timeout")
                .hasMessageNotContaining(TOKEN_FALSO);
        assertThat(saida.getAll()).doesNotContain(TOKEN_FALSO);
    }

    @Test
    void propriedadesNaoImprimemSegredos(@Autowired WhatsAppProperties props) {
        assertThat(props.toString()).doesNotContain(TOKEN_FALSO, VERIFY_TOKEN_FALSO, "app-secret-falso-de-teste");
    }

    private static void esperarLog(CapturedOutput saida, String trecho) throws InterruptedException {
        var limite = Instant.now().plus(Duration.ofSeconds(5));
        while (!saida.getAll().contains(trecho) && Instant.now().isBefore(limite)) {
            Thread.sleep(50);
        }
        assertThat(saida.getAll()).contains(trecho);
    }
}
