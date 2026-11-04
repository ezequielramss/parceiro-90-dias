package br.com.kallopolli.parceiro.whatsapp.evolution;

import br.com.kallopolli.parceiro.whatsapp.CanalSaida;
import br.com.kallopolli.parceiro.whatsapp.FalhaEnvio;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia texto pela Evolution API: POST /message/sendText/{instância} com {"number","text"} e a
 * chave no cabeçalho apikey (código da v2.3.7; a documentação ainda mostra textMessage.text, que a
 * validação da API não aceita). O id da mensagem criada vem em key.id.
 *
 * <p>Neste canal não há janela de 24 horas nem modelos de mensagem. A chave só existe no cabeçalho
 * montado aqui; exceções do RestClient nunca são logadas nem repassadas como estão.
 */
@Component
@ConditionalOnProperty(name = "parceiro.whatsapp.canal", havingValue = "evolution", matchIfMissing = true)
public class EvolutionCanal implements CanalSaida {

    private final RestClient http;
    private final String instancia;

    public EvolutionCanal(RestClient.Builder builder, EvolutionProperties props) {
        props.verificar();
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        this.http = builder
                .baseUrl(props.urlBase())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader("apikey", props.apiKey())
                .build();
        this.instancia = props.instance();
    }

    @Override
    public String enviarTexto(String numero, String texto) {
        try {
            JsonNode resposta = http.post()
                    .uri("/message/sendText/{instancia}", instancia)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("number", numero, "text", texto))
                    .retrieve()
                    .body(JsonNode.class);
            return resposta == null ? null : resposta.path("key").path("id").asText(null);
        } catch (RestClientResponseException e) {
            throw new FalhaEnvio(e.getStatusCode().value(), "recusado pela Evolution");
        } catch (ResourceAccessException e) {
            throw new FalhaEnvio("timeout ou rede indisponível");
        } catch (RestClientException e) {
            throw new FalhaEnvio(e.getClass().getSimpleName());
        }
    }
}
