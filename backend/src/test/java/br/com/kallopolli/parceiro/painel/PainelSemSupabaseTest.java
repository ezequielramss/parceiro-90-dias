package br.com.kallopolli.parceiro.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sem SUPABASE_URL e SUPABASE_PUBLISHABLE_KEY a aplicação sobe, o WhatsApp funciona e o painel
 * responde 503 com mensagem clara. Segredos falsos.
 */
@SpringBootTest(properties = {
        "parceiro.whatsapp.canal=evolution",
        "EVOLUTION_API_URL=http://127.0.0.1:1",
        "EVOLUTION_API_KEY=APIKEY-FALSA-SEM-SUPABASE",
        "EVOLUTION_INSTANCE=instancia-falsa",
        "EVOLUTION_WEBHOOK_SECRET=segredo-falso-sem-supabase",
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:1/nao-usado",
        "DATABASE_USER=usuario-falso",
        "DATABASE_PASSWORD=SENHA-FALSA-SEM-SUPABASE",
        "GEMINI_API_KEY=CHAVE-FALSA-SEM-SUPABASE",
        "GEMINI_MODEL=modelo-falso",
        "management.health.db.enabled=false",
        "parceiro.checkin.ativo=false",
        "parceiro.retencao.ativo=false",
        "SUPABASE_URL=",
        "SUPABASE_PUBLISHABLE_KEY="
})
@AutoConfigureMockMvc
class PainelSemSupabaseTest {

    @Autowired
    MockMvc mvc;

    @Test
    void painelResponde503EWhatsAppContinua() throws Exception {
        for (String rota : AcessoPainelTest.ROTAS) {
            mvc.perform(get(rota))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("{\"erro\":{\"codigo\":\"PAINEL_NAO_CONFIGURADO\"}}"));
        }
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@exemplo.invalid\",\"senha\":\"y\"}"))
                .andExpect(status().isServiceUnavailable());

        String mensagem = mvc.perform(get("/api/perfil")).andReturn().getResponse().getContentAsString();
        assertThat(mensagem).contains("SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY");

        mvc.perform(post("/webhook/evolution").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Secret", "segredo-falso-sem-supabase")
                        .content("{\"event\":\"connection.update\",\"data\":{}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
