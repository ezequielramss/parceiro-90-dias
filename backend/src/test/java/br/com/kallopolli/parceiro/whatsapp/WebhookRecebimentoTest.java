package br.com.kallopolli.parceiro.whatsapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class WebhookRecebimentoTest extends WebhookTestBase {

    @Autowired
    MockMvc mvc;

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
    void payloadDesconhecidoOuInvalidoResponde200(String corpo) throws Exception {
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
    }

    @Test
    void healthEstaLigadoEOEnvNaoEstaExposto() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
