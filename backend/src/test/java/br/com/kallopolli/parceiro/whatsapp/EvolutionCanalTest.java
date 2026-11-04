package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/** Entrada e saída pela Evolution API, com a Evolution falsa. Números e textos fictícios. */
@ExtendWith(OutputCaptureExtension.class)
class EvolutionCanalTest extends WebhookTestBase {

    private static final String NUMERO = "5511999990601";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    CanalSaida canal;

    @Test
    void eventoDeTextoViraMensagemRecebidaEResposta(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postWebhook(payloadTexto("EVO-TXT-1", NUMERO, "oi"))).andExpect(status().isOk());
        esperarProcessamento(saida, "EVO-TXT-1");

        assertThat(repositorios.mensagens()).filteredOn(m -> m.direcao().equals("ENTRADA")).singleElement()
                .satisfies(m -> {
                    assertThat(m.wamid()).isEqualTo("EVO-TXT-1");
                    assertThat(m.tipo()).isEqualTo("text");
                    assertThat(m.texto()).isEqualTo("oi");
                });
        assertThat(EVOLUTION.recebidas().poll(1, TimeUnit.SECONDS)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "segredo-errado", "SEGREDO-WEBHOOK-FALSO-DE-TESTE-41b", "SEGREDO-WEBHOOK-FALSO-DE-TESTE-41b2 "})
    void segredoErradoOuAusenteERecusadoSemProcessar(String segredo, CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        var pedido = post("/webhook/evolution").contentType(MediaType.APPLICATION_JSON)
                .content(payloadTexto("EVO-SEG-1", NUMERO, "oi"));
        if (!segredo.isEmpty()) {
            pedido.header(CABECALHO_SEGREDO, segredo);
        }

        mvc.perform(pedido).andExpect(status().isUnauthorized());

        Thread.sleep(200);
        assertThat(repositorios.mensagens()).isEmpty();
        assertThat(EVOLUTION.recebidas()).isEmpty();
        assertThat(saida.getAll()).contains("segredo do webhook ausente ou inválido").doesNotContain("EVO-SEG-1");
    }

    @Test
    void apikeyNoCorpoNaoSubstituiOSegredo() throws Exception {
        mvc.perform(post("/webhook/evolution").contentType(MediaType.APPLICATION_JSON)
                        .header("apikey", APIKEY_FALSA)
                        .content(payloadTexto("EVO-SEG-2", NUMERO, "oi")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "120363000000000001@g.us",
            "status@broadcast",
            "1234567890@broadcast",
            "120363000000000002@newsletter",
            "123456789012345@lid"})
    void grupoStatusTransmissaoECanalSaoIgnorados(String remoteJid, CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postWebhook(evento("EVO-IGN-1", remoteJid, false, "conversation", "{\"conversation\":\"oi\"}")))
                .andExpect(status().isOk());

        Thread.sleep(200);
        assertThat(repositorios.mensagens()).isEmpty();
        assertThat(saida.getAll()).contains("evento ignorado", "NAO_E_CONVERSA_INDIVIDUAL");
    }

    @Test
    void mensagemEnviadaPeloProprioNumeroEIgnorada(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postWebhook(evento("EVO-ME-1", NUMERO + "@s.whatsapp.net", true, "conversation",
                "{\"conversation\":\"oi\"}"))).andExpect(status().isOk());

        Thread.sleep(200);
        assertThat(repositorios.mensagens()).isEmpty();
        assertThat(saida.getAll()).contains("ENVIADA_PELO_PROPRIO_NUMERO");
    }

    @Test
    void lidComNumeroAlternativoUsaONumero(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        String corpo = """
                {"event":"messages.upsert","instance":"instancia-falsa","data":{
                  "key":{"remoteJid":"123456789012345@lid","remoteJidAlt":"%s@s.whatsapp.net","fromMe":false,"id":"EVO-LID-1"},
                  "message":{"conversation":"oi"},"messageType":"conversation"}}""".formatted(NUMERO);

        mvc.perform(postWebhook(corpo)).andExpect(status().isOk());
        esperarProcessamento(saida, "EVO-LID-1");

        assertThat(repositorios.mensagens()).filteredOn(m -> m.direcao().equals("ENTRADA")).hasSize(1);
    }

    @Test
    void tipoSemTratamentoEIgnoradoSemGravar(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postWebhook(evento("EVO-REACAO", NUMERO + "@s.whatsapp.net", false, "reactionMessage",
                "{\"reactionMessage\":{\"text\":\"x\"}}"))).andExpect(status().isOk());

        Thread.sleep(200);
        assertThat(repositorios.mensagens()).isEmpty();
        assertThat(saida.getAll()).contains("TIPO_SEM_TRATAMENTO");
    }

    @Test
    void envioLevaApikeyNoCabecalhoEOCorpoEsperado() throws Exception {
        String id = canal.enviarTexto(NUMERO, "texto ficticio");

        var requisicao = EVOLUTION.recebidas().poll(1, TimeUnit.SECONDS);
        assertThat(requisicao).isNotNull();
        assertThat(requisicao.metodo()).isEqualTo("POST");
        assertThat(requisicao.caminho()).isEqualTo("/message/sendText/" + INSTANCIA_FALSA);
        assertThat(requisicao.apikey()).isEqualTo(APIKEY_FALSA);
        assertThat(JSON.readTree(requisicao.corpo()))
                .isEqualTo(JSON.readTree("{\"number\":\"" + NUMERO + "\",\"text\":\"texto ficticio\"}"));
        assertThat(id).startsWith("EVO-SAIDA-");
    }

    @Test
    void falhaDeEnvioViraFalhaEnvioSemChaveNemTexto() {
        EVOLUTION.falharPara(NUMERO);

        assertThatThrownBy(() -> canal.enviarTexto(NUMERO, "texto ficticio"))
                .isInstanceOf(FalhaEnvio.class)
                .satisfies(e -> assertThat(((FalhaEnvio) e).status()).isEqualTo(400))
                .hasMessageNotContaining(APIKEY_FALSA)
                .hasMessageNotContaining("texto ficticio")
                .hasMessageNotContaining(NUMERO);
    }

    @Test
    void evolutionForaDoArViraFalhaEnvio() {
        EVOLUTION.atrasar(2_000);

        assertThatThrownBy(() -> canal.enviarTexto(NUMERO, "texto ficticio"))
                .isInstanceOf(FalhaEnvio.class)
                .hasMessageContaining("timeout")
                .hasMessageNotContaining(APIKEY_FALSA);
    }
}
