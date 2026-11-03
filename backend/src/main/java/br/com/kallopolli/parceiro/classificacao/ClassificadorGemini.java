package br.com.kallopolli.parceiro.classificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Classifica relatos com o Gemini (generateContent, API REST v1beta).
 *
 * <p>A chave vai só no cabeçalho x-goog-api-key. Em 429, 5xx, timeout ou erro de rede tenta de novo
 * com espera crescente (respeitando o retryDelay da API, com teto); esgotadas as tentativas, ou se a
 * resposta for inválida, devolve {@link Classificacao#falha()}: o relato vai ao RH sem classificação,
 * que é o caminho normal quando o nível gratuito estoura o limite. Nunca lança exceção.
 *
 * <p>Logs só com status HTTP, número da tentativa e nome da exceção; nunca relato, resposta ou chave.
 */
@Component
public class ClassificadorGemini implements ClassificadorRelato {

    private static final Logger log = LoggerFactory.getLogger(ClassificadorGemini.class);

    private static final Pattern TAG_RELATO = Pattern.compile("<\\s*/?\\s*relato\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)s");
    private static final Set<String> CONFIANCAS = Set.of("baixa", "media", "alta");

    /** Espera entre tentativas; trocada nos testes. */
    interface Dormir {
        void por(Duration duracao) throws InterruptedException;
    }

    private final RestClient restClient;
    private final GeminiProperties props;
    private final ObjectMapper objectMapper;
    private final PromptClassificacao prompt;
    private final Dormir dormir;

    @Autowired
    public ClassificadorGemini(RestClient.Builder builder, GeminiProperties props, ObjectMapper objectMapper,
                               PromptClassificacao prompt) {
        this(builder, props, objectMapper, prompt, d -> Thread.sleep(d.toMillis()));
    }

    ClassificadorGemini(RestClient.Builder builder, GeminiProperties props, ObjectMapper objectMapper,
                        PromptClassificacao prompt, Dormir dormir) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader("x-goog-api-key", props.apiKey())
                .build();
        this.props = props;
        this.objectMapper = objectMapper;
        this.prompt = prompt;
        this.dormir = dormir;
    }

    @Override
    public Classificacao classificar(String textoDoRelato) {
        try {
            Map<String, Object> corpo = montarCorpo(textoDoRelato);
            int tentativas = 1 + props.novasTentativas();
            for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
                boolean ultima = tentativa == tentativas;
                try {
                    String resposta = restClient.post()
                            .uri("/v1beta/models/{modelo}:generateContent", props.model())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(corpo)
                            .retrieve()
                            .body(String.class);
                    return interpretar(resposta);
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    registrarFalha(status, tentativa, e);
                    if ((status != 429 && status < 500) || ultima) {
                        return Classificacao.falha();
                    }
                    esperar(tentativa, retryDelay(e.getResponseBodyAsString()));
                } catch (ResourceAccessException e) {
                    registrarFalha(null, tentativa, e);
                    if (ultima) {
                        return Classificacao.falha();
                    }
                    esperar(tentativa, null);
                }
            }
            return Classificacao.falha();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarFalha(null, 0, e);
            return Classificacao.falha();
        } catch (Exception e) {
            registrarFalha(null, 0, e);
            return Classificacao.falha();
        }
    }

    /** Remove as tags do delimitador e limita o tamanho, para o relato não "sair" de dentro de <relato>. */
    String sanear(String texto) {
        if (texto == null) {
            return "";
        }
        String saneado = texto;
        String anterior;
        do {
            anterior = saneado;
            saneado = TAG_RELATO.matcher(saneado).replaceAll("");
        } while (!saneado.equals(anterior));
        saneado = saneado.strip();
        return saneado.length() <= props.maxCaracteres() ? saneado : saneado.substring(0, props.maxCaracteres());
    }

    private Map<String, Object> montarCorpo(String textoDoRelato) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", prompt.prompt()))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "<relato>\n" + sanear(textoDoRelato) + "\n</relato>")))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseJsonSchema", prompt.esquema()));
    }

    /** Valida a resposta contra o esquema; qualquer desvio vira falha. */
    private Classificacao interpretar(String resposta) {
        try {
            JsonNode texto = objectMapper.readTree(resposta)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!texto.isTextual()) {
                return invalida();
            }
            JsonNode saida = objectMapper.readTree(texto.asText());
            JsonNode categoria = saida.path("categoria");
            JsonNode motivo = saida.path("motivo");
            JsonNode acao = saida.path("acao_sugerida");
            JsonNode confianca = saida.path("confianca");
            JsonNode sinais = saida.path("sinais_graves");
            boolean valida = categoria.isTextual() && prompt.categorias().contains(categoria.asText())
                    && motivo.isTextual() && !motivo.asText().isBlank()
                    && acao.isTextual() && !acao.asText().isBlank()
                    && confianca.isTextual() && CONFIANCAS.contains(confianca.asText())
                    && sinais.isBoolean();
            if (!valida) {
                return invalida();
            }
            return new Classificacao(categoria.asText(), motivo.asText().strip(), acao.asText().strip(),
                    confianca.asText(), sinais.asBoolean(), false);
        } catch (Exception e) {
            return invalida();
        }
    }

    private Classificacao invalida() {
        log.atWarn().setMessage("classificação descartada").addKeyValue("erro", "RespostaInvalida").log();
        return Classificacao.falha();
    }

    private void esperar(int tentativa, Duration pedidoPelaApi) throws InterruptedException {
        Duration espera = pedidoPelaApi != null
                ? pedidoPelaApi
                : props.esperaInicial().multipliedBy(1L << (tentativa - 1));
        dormir.por(espera.compareTo(props.esperaMaxima()) > 0 ? props.esperaMaxima() : espera);
    }

    /** Lê o retryDelay (ex.: "41s") do corpo do erro 429, se houver. Nunca loga o corpo. */
    private Duration retryDelay(String corpoErro) {
        try {
            for (JsonNode detalhe : objectMapper.readTree(corpoErro).path("error").path("details")) {
                Matcher m = RETRY_DELAY.matcher(detalhe.path("retryDelay").asText(""));
                if (m.matches()) {
                    return Duration.ofMillis(Math.round(Double.parseDouble(m.group(1)) * 1000));
                }
            }
        } catch (Exception e) {
            // corpo sem JSON: usa a espera crescente padrão
        }
        return null;
    }

    private void registrarFalha(Integer status, int tentativa, Exception e) {
        log.atWarn().setMessage("falha ao classificar relato")
                .addKeyValue("status_http", status)
                .addKeyValue("tentativa", tentativa)
                .addKeyValue("erro", e.getClass().getSimpleName())
                .log();
    }
}
