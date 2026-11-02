package br.com.kallopolli.parceiro.whatsapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class WebhookVerificacaoTest extends WebhookTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void tokenCertoDevolveODesafio() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN_FALSO)
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void tokenErradoRecusa() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "outro-token")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    void modoDiferenteDeSubscribeRecusa() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", VERIFY_TOKEN_FALSO)
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semParametrosRecusa() throws Exception {
        mvc.perform(get("/webhook/whatsapp")).andExpect(status().isForbidden());
    }
}
