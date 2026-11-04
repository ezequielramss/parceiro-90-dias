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
            "{\"event\":\"connection.update\",\"instance\":\"x\",\"data\":{\"state\":\"open\"}}",
            "{\"event\":\"messages.update\",\"data\":{\"status\":\"DELIVERY_ACK\"}}",
            "{\"event\":\"messages.upsert\",\"data\":{\"key\":{\"remoteJid\":\"x\"}}}",
            "{\"event\":\"messages.upsert\",\"data\":\"nao e objeto\"}",
            "{\"event\":\"messages.upsert\"}",
            "[]",
            "isto nao e json",
            ""
    })
    void payloadComSegredoDesconhecidoOuInvalidoResponde200(String corpo) throws Exception {
        mvc.perform(postWebhook(corpo)).andExpect(status().isOk());
    }

    @Test
    void healthEstaLigadoEOEnvNaoEstaExposto() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test
    void semMetaNemSimuladorNoCanalEvolution() throws Exception {
        mvc.perform(get("/webhook/whatsapp")).andExpect(status().isNotFound());
        mvc.perform(get("/simulador")).andExpect(status().isNotFound());
        mvc.perform(get("/simulador/api/contatos")).andExpect(status().isNotFound());
    }
}
