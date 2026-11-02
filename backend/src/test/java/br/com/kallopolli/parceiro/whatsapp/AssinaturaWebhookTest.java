package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
class AssinaturaWebhookTest extends WebhookTestBase {

    private static final String NUMERO = "5500000000101";

    @Test
    void assinaturaValidaProcessa(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postAssinado(payloadTexto("wamid.ASS-OK", NUMERO, "oi"))).andExpect(status().isOk());

        esperarProcessamento(saida, "wamid.ASS-OK");
        assertThat(repositorios.mensagens()).extracting("wamid").contains("wamid.ASS-OK");
    }

    @Test
    void semAssinaturaResponde401ENaoProcessa() throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTexto("wamid.ASS-SEM", NUMERO, "oi")))
                .andExpect(status().isUnauthorized());

        naoProcessou();
    }

    @Test
    void assinaturaComOutroSegredoResponde401() throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        String corpo = payloadTexto("wamid.ASS-OUTRO", NUMERO, "oi");

        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinar(corpo, "outro-segredo"))
                        .content(corpo))
                .andExpect(status().isUnauthorized());

        naoProcessou();
    }

    @Test
    void corpoAlteradoDepoisDeAssinadoResponde401() throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        String original = payloadTexto("wamid.ASS-ALT", NUMERO, "oi");
        String alterado = payloadTexto("wamid.ASS-ALT", NUMERO, "parar");

        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinar(original, APP_SECRET_FALSO))
                        .content(alterado))
                .andExpect(status().isUnauthorized());

        naoProcessou();
    }

    @Test
    void corpoComEspacoExtraNaoPassa() throws Exception {
        // A assinatura é sobre os bytes exatos: reformatar o JSON invalida.
        String corpo = "{\"entry\":[]}";
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinar(corpo, APP_SECRET_FALSO))
                        .content("{ \"entry\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sha256=", "sha256=zz", "sha1=abcdef", "abcdef0123", "sha256=00"})
    void cabecalhoMalformadoResponde401(String cabecalho) throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", cabecalho)
                        .content("{\"entry\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    private void naoProcessou() throws InterruptedException {
        assertThat(GRAPH_API.recebidas().poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(repositorios.mensagens()).isEmpty();
    }
}
