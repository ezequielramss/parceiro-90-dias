package br.com.kallopolli.parceiro.whatsapp;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sobe a aplicação com segredos FALSOS (nenhum valor real) e a Graph API apontando
 * para um servidor local.
 */
@SpringBootTest(properties = {
        "WHATSAPP_TOKEN=" + WebhookTestBase.TOKEN_FALSO,
        "WHATSAPP_PHONE_NUMBER_ID=" + WebhookTestBase.PHONE_NUMBER_ID_FALSO,
        "WHATSAPP_VERIFY_TOKEN=" + WebhookTestBase.VERIFY_TOKEN_FALSO,
        "WHATSAPP_APP_SECRET=app-secret-falso-de-teste",
        "WHATSAPP_GRAPH_VERSION=v99.0",
        "whatsapp.read-timeout=500ms"
})
@AutoConfigureMockMvc
abstract class WebhookTestBase {

    static final String TOKEN_FALSO = "TOKEN-FALSO-DE-TESTE-7f3a9c";
    static final String PHONE_NUMBER_ID_FALSO = "100000000000001";
    static final String VERIFY_TOKEN_FALSO = "verify-falso-de-teste";

    static final GraphApiFalsa GRAPH_API;

    static {
        try {
            GRAPH_API = new GraphApiFalsa();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void graphApi(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.base-url", GRAPH_API::url);
    }

    @AfterAll
    static void limparFila() {
        GRAPH_API.recebidas().clear();
    }

    /** Mensagem de texto no formato que a Meta envia; número e texto fictícios. */
    static String payloadTexto(String wamid, String de, String texto) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5500000000000","phone_number_id":"%s"},
                  "contacts":[{"profile":{"name":"Pessoa Ficticia"},"wa_id":"%s"}],
                  "messages":[{"from":"%s","id":"%s","timestamp":"1700000000","type":"text","text":{"body":"%s"}}]
                }}]}]}
                """.formatted(PHONE_NUMBER_ID_FALSO, de, de, wamid, texto);
    }
}
