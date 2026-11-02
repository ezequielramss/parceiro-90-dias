package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.jornada.BancoProperties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/** Nenhum segredo, texto de mensagem ou telefone aparece no log, nem nos caminhos de erro. */
@ExtendWith(OutputCaptureExtension.class)
class LogsSemSegredosTest extends WebhookTestBase {

    private static final String NUMERO = "5500999990000";
    private static final String TEXTO = "texto ficticio de teste";

    @Autowired
    WhatsAppClient client;

    @Test
    void fluxoNormalNaoLogaSegredosTextoNemTelefone(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-1", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-1");

        assertThat(saida.getAll()).contains("mensagem recebida", "\"tipo\":\"text\"", "PEDIDO_ACEITE");
        semSegredos(saida);
    }

    @Test
    void falhaNoProcessamentoLogaSoWamidETipoDoErro(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        repositorios.falharNaProximaGravacao(new IllegalStateException(
                "erro com dados: " + TEXTO + " " + NUMERO + " " + TOKEN_FALSO + " " + SENHA_BANCO_FALSA));

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-2", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-2");

        assertThat(saida.getAll()).contains("falha ao processar mensagem", "\"erro\":\"IllegalStateException\"");
        semSegredos(saida);
    }

    @Test
    void erroDaMetaRepetindoOTokenNaoVaza(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        GRAPH_API.responder(401, """
                {"error":{"message":"Invalid OAuth access token %s","type":"OAuthException","code":190}}
                """.formatted(TOKEN_FALSO));

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-3", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-3");

        assertThat(saida.getAll()).contains("falha ao processar mensagem", "WhatsAppClientException");
        semSegredos(saida);
    }

    @Test
    void assinaturaInvalidaNaoLogaOCorpo(CapturedOutput saida) throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=00")
                        .content(payloadTexto("wamid.LOG-4", NUMERO, TEXTO)))
                .andExpect(status().isUnauthorized());

        assertThat(saida.getAll()).contains("assinatura do webhook ausente ou inválida");
        semSegredos(saida);
    }

    @Test
    void timeoutViraExcecaoSemToken(CapturedOutput saida) {
        GRAPH_API.atrasar(2_000);

        assertThatThrownBy(() -> client.enviarTexto(NUMERO, TEXTO))
                .isInstanceOf(WhatsAppClientException.class)
                .hasMessageContaining("timeout")
                .hasMessageNotContaining(TOKEN_FALSO);
        semSegredos(saida);
    }

    @Test
    void propriedadesNaoImprimemSegredos(@Autowired WhatsAppProperties whatsapp, @Autowired BancoProperties banco) {
        assertThat(whatsapp.toString()).doesNotContain(TOKEN_FALSO, VERIFY_TOKEN_FALSO, APP_SECRET_FALSO);
        assertThat(banco.toString()).doesNotContain(SENHA_BANCO_FALSA);
    }

    private static void semSegredos(CapturedOutput saida) throws RuntimeException {
        try {
            GRAPH_API.recebidas().poll(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(saida.getAll()).doesNotContain(
                TOKEN_FALSO, VERIFY_TOKEN_FALSO, APP_SECRET_FALSO, SENHA_BANCO_FALSA, TEXTO, NUMERO);
    }
}
