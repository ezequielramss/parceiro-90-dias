package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.kallopolli.parceiro.classificacao.CategoriasEmMemoria;
import br.com.kallopolli.parceiro.classificacao.ClassificadorFalso;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sobe a aplicação com segredos FALSOS (nenhum valor real), repositórios em memória
 * e a Graph API apontando para um servidor local.
 */
@SpringBootTest(properties = {
        "WHATSAPP_TOKEN=" + WebhookTestBase.TOKEN_FALSO,
        "WHATSAPP_PHONE_NUMBER_ID=" + WebhookTestBase.PHONE_NUMBER_ID_FALSO,
        "WHATSAPP_VERIFY_TOKEN=" + WebhookTestBase.VERIFY_TOKEN_FALSO,
        "WHATSAPP_APP_SECRET=" + WebhookTestBase.APP_SECRET_FALSO,
        "WHATSAPP_GRAPH_VERSION=v99.0",
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:1/nao-usado",
        "DATABASE_USER=usuario-falso",
        "DATABASE_PASSWORD=" + WebhookTestBase.SENHA_BANCO_FALSA,
        "GEMINI_API_KEY=" + WebhookTestBase.CHAVE_GEMINI_FALSA,
        "GEMINI_MODEL=modelo-falso",
        "management.health.db.enabled=false",
        "parceiro.checkin.ativo=false",
        "whatsapp.read-timeout=500ms"
})
@AutoConfigureMockMvc
@Import(WebhookTestBase.Repositorios.class)
abstract class WebhookTestBase {

    static final String TOKEN_FALSO = "TOKEN-FALSO-DE-TESTE-7f3a9c";
    static final String PHONE_NUMBER_ID_FALSO = "100000000000001";
    static final String VERIFY_TOKEN_FALSO = "verify-falso-de-teste";
    static final String APP_SECRET_FALSO = "APP-SECRET-FALSO-DE-TESTE-41b2";
    static final String SENHA_BANCO_FALSA = "SENHA-BANCO-FALSA-DE-TESTE-9d0e";
    static final String CHAVE_GEMINI_FALSA = "CHAVE-GEMINI-FALSA-DE-TESTE-5c1a";

    static final GraphApiFalsa GRAPH_API;

    static {
        try {
            GRAPH_API = new GraphApiFalsa();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class Repositorios {
        @Bean
        @Primary
        RepositoriosEmMemoria repositoriosEmMemoria() {
            return new RepositoriosEmMemoria();
        }

        @Bean
        @Primary
        CategoriasEmMemoria categoriasEmMemoria() {
            return new CategoriasEmMemoria();
        }

        @Bean
        @Primary
        ClassificadorFalso classificadorFalso() {
            return new ClassificadorFalso();
        }
    }

    @DynamicPropertySource
    static void graphApi(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.base-url", GRAPH_API::url);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    RepositoriosEmMemoria repositorios;

    @Autowired
    ClassificadorFalso classificador;

    @BeforeEach
    void limparEstado() {
        repositorios.limpar();
        classificador.limpar();
        GRAPH_API.responder(200, GraphApiFalsa.WAMID_GERADO);
        GRAPH_API.reabrirJanelas();
        GRAPH_API.atrasar(0);
        GRAPH_API.recebidas().clear();
    }

    /** POST com X-Hub-Signature-256 calculado com o App Secret falso. */
    static MockHttpServletRequestBuilder postAssinado(String corpo) {
        return post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", assinar(corpo, APP_SECRET_FALSO))
                .content(corpo);
    }

    static String assinar(String corpo, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    /** Toque em botão de resposta, no formato que a Meta envia; número fictício. */
    static String payloadBotao(String wamid, String de, String botaoId, String titulo) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5500000000000","phone_number_id":"%s"},
                  "messages":[{"from":"%s","id":"%s","timestamp":"1700000000","type":"interactive",
                    "interactive":{"type":"button_reply","button_reply":{"id":"%s","title":"%s"}}}]
                }}]}]}
                """.formatted(PHONE_NUMBER_ID_FALSO, de, wamid, botaoId, titulo);
    }

    /** Mensagem de um tipo que o assistente não lê (áudio, imagem, vídeo, figurinha). */
    static String payloadTipo(String wamid, String de, String tipo) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp",
                  "messages":[{"from":"%s","id":"%s","timestamp":"1700000000","type":"%s","%s":{"id":"midia"}}]
                }}]}]}
                """.formatted(de, wamid, tipo, tipo);
    }

    /** O processamento é assíncrono: espera o log de conclusão daquele wamid. */
    static void esperarProcessamento(CapturedOutput saida, String wamid) throws InterruptedException {
        var limite = Instant.now().plus(Duration.ofSeconds(5));
        while (!processado(saida, wamid) && Instant.now().isBefore(limite)) {
            Thread.sleep(25);
        }
        assertThat(processado(saida, wamid)).as("processamento de " + wamid).isTrue();
    }

    private static boolean processado(CapturedOutput saida, String wamid) {
        return saida.getAll().lines().anyMatch(l -> l.contains("\"wamid\":\"" + wamid + "\"")
                && (l.contains("mensagem processada") || l.contains("falha ao processar mensagem")));
    }
}
