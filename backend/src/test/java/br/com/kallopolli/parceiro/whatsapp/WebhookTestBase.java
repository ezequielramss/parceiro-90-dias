package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.kallopolli.parceiro.classificacao.CategoriasEmMemoria;
import br.com.kallopolli.parceiro.classificacao.ClassificadorFalso;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import br.com.kallopolli.parceiro.painel.PainelEmMemoria;
import br.com.kallopolli.parceiro.painel.SupabaseAuthFalso;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
 * Sobe a aplicação com segredos FALSOS (nenhum valor real), repositórios em memória, o canal
 * Evolution apontando para a {@link EvolutionFalsa} e o Supabase Auth para um servidor local.
 */
@SpringBootTest(properties = {
        "parceiro.whatsapp.canal=evolution",
        "EVOLUTION_API_KEY=" + WebhookTestBase.APIKEY_FALSA,
        "EVOLUTION_INSTANCE=" + WebhookTestBase.INSTANCIA_FALSA,
        "EVOLUTION_WEBHOOK_SECRET=" + WebhookTestBase.SEGREDO_WEBHOOK_FALSO,
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:1/nao-usado",
        "DATABASE_USER=usuario-falso",
        "DATABASE_PASSWORD=" + WebhookTestBase.SENHA_BANCO_FALSA,
        "GEMINI_API_KEY=" + WebhookTestBase.CHAVE_GEMINI_FALSA,
        "GEMINI_MODEL=modelo-falso",
        "management.health.db.enabled=false",
        "parceiro.checkin.ativo=false",
        "parceiro.retencao.ativo=false",
        "evolution.read-timeout=500ms"
})
@AutoConfigureMockMvc
@Import(WebhookTestBase.Repositorios.class)
public abstract class WebhookTestBase {

    static final String APIKEY_FALSA = "APIKEY-EVOLUTION-FALSA-DE-TESTE-7f3a";
    static final String INSTANCIA_FALSA = "instancia-falsa";
    static final String SEGREDO_WEBHOOK_FALSO = "SEGREDO-WEBHOOK-FALSO-DE-TESTE-41b2";
    static final String SENHA_BANCO_FALSA = "SENHA-BANCO-FALSA-DE-TESTE-9d0e";
    static final String CHAVE_GEMINI_FALSA = "CHAVE-GEMINI-FALSA-DE-TESTE-5c1a";
    static final String CABECALHO_SEGREDO = "X-Webhook-Secret";

    static final EvolutionFalsa EVOLUTION;
    protected static final SupabaseAuthFalso SUPABASE;

    static {
        try {
            EVOLUTION = new EvolutionFalsa();
            SUPABASE = new SupabaseAuthFalso();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Repositórios em memória e classificador falso; o simulador também usa. */
    @TestConfiguration
    public static class Repositorios {
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

        @Bean
        @Primary
        PainelEmMemoria painelEmMemoria() {
            return new PainelEmMemoria();
        }
    }

    @DynamicPropertySource
    static void servidoresFalsos(DynamicPropertyRegistry registry) {
        registry.add("EVOLUTION_API_URL", EVOLUTION::url);
        registry.add("parceiro.dashboard.supabase-url", SUPABASE::url);
        registry.add("parceiro.dashboard.supabase-publishable-key", () -> SupabaseAuthFalso.CHAVE_PUBLICAVEL_FALSA);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RepositoriosEmMemoria repositorios;

    @Autowired
    protected PainelEmMemoria painel;

    @Autowired
    ClassificadorFalso classificador;

    @BeforeEach
    void limparEstado() {
        repositorios.limpar();
        classificador.limpar();
        painel.limpar();
        SUPABASE.limpar();
        EVOLUTION.limpar();
    }

    /** POST no webhook da Evolution com o segredo compartilhado falso. */
    static MockHttpServletRequestBuilder postWebhook(String corpo) {
        return post("/webhook/evolution")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CABECALHO_SEGREDO, SEGREDO_WEBHOOK_FALSO)
                .content(corpo);
    }

    /** Evento messages.upsert de texto, no formato da Evolution v2.3.7; número e texto fictícios. */
    static String payloadTexto(String id, String de, String texto) {
        return evento(id, de + "@s.whatsapp.net", false, "conversation",
                "{\"conversation\":" + json(texto) + "}");
    }

    /** Texto em extendedTextMessage (mensagem com link, resposta citada etc.). */
    static String payloadTextoEstendido(String id, String de, String texto) {
        return evento(id, de + "@s.whatsapp.net", false, "extendedTextMessage",
                "{\"extendedTextMessage\":{\"text\":" + json(texto) + "}}");
    }

    /** Mensagem de um tipo que o assistente não lê: audio, image, video ou sticker. */
    static String payloadTipo(String id, String de, String tipo) {
        String messageType = Map.of("audio", "audioMessage", "image", "imageMessage", "video", "videoMessage",
                "sticker", "stickerMessage").get(tipo);
        return evento(id, de + "@s.whatsapp.net", false, messageType, "{\"" + messageType + "\":{\"mimetype\":\"x\"}}");
    }

    static String evento(String id, String remoteJid, boolean fromMe, String messageType, String message) {
        return """
                {"event":"messages.upsert","instance":"%s","data":{
                  "key":{"remoteJid":"%s","fromMe":%s,"id":"%s"},
                  "pushName":"Pessoa Ficticia","message":%s,"messageType":"%s","messageTimestamp":1700000000,
                  "instanceId":"00000000-0000-0000-0000-000000000000","source":"android"},
                 "destination":"http://localhost/webhook/evolution","date_time":"2026-11-04T10:00:00.000Z",
                 "sender":"5500000000000@s.whatsapp.net","server_url":"http://localhost:8081","apikey":"%s"}
                """.formatted(INSTANCIA_FALSA, remoteJid, fromMe, id, message, messageType, APIKEY_FALSA);
    }

    private static String json(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** O processamento é assíncrono: espera o log de conclusão daquele id. */
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
