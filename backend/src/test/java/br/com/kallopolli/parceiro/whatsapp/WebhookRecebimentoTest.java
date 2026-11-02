package br.com.kallopolli.parceiro.whatsapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookRecebimentoTest extends WebhookTestBase {

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"qualquer\":[1,2,3]}",
            "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{\"novo_campo\":true}}]}]}",
            "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.X\",\"status\":\"delivered\"}]}}]}]}",
            "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.Y\",\"type\":\"sticker\"}]}}]}]}",
            "{\"entry\":\"nao e lista\"}",
            "[]",
            "isto nao e json",
            ""
    })
    void payloadAssinadoDesconhecidoOuInvalidoResponde200(String corpo) throws Exception {
        mvc.perform(postAssinado(corpo)).andExpect(status().isOk());
    }

    @Test
    void healthEstaLigadoEOEnvNaoEstaExposto() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
