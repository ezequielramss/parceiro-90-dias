package br.com.kallopolli.parceiro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia mensagens pela WhatsApp Cloud API: POST /{versao}/{phone-number-id}/messages.
 *
 * <p>O token só existe no header Authorization montado aqui. Exceções do RestClient
 * nunca são logadas nem repassadas como estão, porque podem trazer URL, headers ou corpo.
 */
@Component
public class WhatsAppClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String caminhoMensagens;

    public WhatsAppClient(RestClient.Builder builder, WhatsAppProperties props, ObjectMapper objectMapper) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.token())
                .build();
        this.objectMapper = objectMapper;
        this.caminhoMensagens = "/" + props.graphVersion() + "/" + props.phoneNumberId() + "/messages";
    }

    /**
     * Envia um texto simples e devolve o wamid da mensagem criada.
     *
     * @throws WhatsAppClientException se a Meta recusar ou a rede falhar
     */
    public String enviarTexto(String para, String texto) {
        var corpo = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", para,
                "type", "text",
                "text", Map.of("preview_url", false, "body", texto));
        try {
            JsonNode resposta = restClient.post()
                    .uri(caminhoMensagens)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
            return resposta == null ? null : resposta.path("messages").path(0).path("id").asText(null);
        } catch (RestClientResponseException e) {
            throw new WhatsAppClientException(e.getStatusCode().value(), codigoErroMeta(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new WhatsAppClientException("timeout ou rede indisponível");
        } catch (RestClientException e) {
            throw new WhatsAppClientException(e.getClass().getSimpleName());
        }
    }

    /** Extrai só error.code da resposta da Meta, sem a mensagem livre. */
    private String codigoErroMeta(String corpo) {
        try {
            JsonNode erro = objectMapper.readTree(corpo).path("error");
            return erro.hasNonNull("code") ? erro.get("code").asText() : "desconhecido";
        } catch (Exception e) {
            return "desconhecido";
        }
    }
}
