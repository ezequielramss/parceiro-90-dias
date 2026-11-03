package br.com.kallopolli.parceiro.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Login por e-mail e senha no Supabase Auth (POST /auth/v1/token?grant_type=password, chave
 * publicável no cabeçalho apikey). Só o backend fala com o Supabase; o navegador nunca vê a chave.
 * Nada daqui vai para o log: nem e-mail, nem senha, nem token, nem o corpo da resposta.
 */
@Component
class SupabaseAuth {

    /** Token de acesso devolvido pelo Supabase e quantos segundos ele vale. */
    record Sessao(String accessToken, long expiraEmSegundos) {
        @Override
        public String toString() {
            return "Sessao[accessToken=***, expiraEmSegundos=" + expiraEmSegundos + "]";
        }
    }

    /** O Supabase não respondeu ou respondeu com erro do servidor. */
    static class Indisponivel extends RuntimeException {
        Indisponivel(String motivo) {
            super(motivo);
        }
    }

    private final PainelProperties props;
    private final RestClient http;
    private final ObjectMapper json;

    SupabaseAuth(PainelProperties props, RestClient.Builder builder, ObjectMapper json) {
        this.props = props;
        this.json = json;
        var cliente = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        var fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(props.readTimeout());
        this.http = builder.requestFactory(fabrica).build();
    }

    /** Vazio quando e-mail ou senha estão errados (qualquer 4xx do Supabase). */
    Optional<Sessao> entrar(String email, String senha) {
        try {
            return http.post()
                    .uri(props.urlBase() + "/auth/v1/token?grant_type=password")
                    .header("apikey", props.supabasePublishableKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", senha))
                    .exchange((pedido, resposta) -> {
                        int status = resposta.getStatusCode().value();
                        if (status >= 500) {
                            throw new Indisponivel("supabase respondeu " + status);
                        }
                        if (status != 200) {
                            return Optional.<Sessao>empty();
                        }
                        return lerSessao(resposta.getBody().readAllBytes());
                    });
        } catch (RestClientException e) {
            // A mensagem pode repetir a URL ou o corpo: só o tipo do erro segue adiante.
            throw new Indisponivel(e.getClass().getSimpleName());
        }
    }

    private Optional<Sessao> lerSessao(byte[] corpo) {
        try {
            JsonNode no = json.readTree(corpo);
            String token = no.path("access_token").asText("");
            long validade = no.path("expires_in").asLong(0);
            if (token.isBlank() || validade <= 0) {
                throw new Indisponivel("resposta do supabase sem token");
            }
            return Optional.of(new Sessao(token, validade));
        } catch (IOException e) {
            throw new Indisponivel("resposta do supabase ilegível");
        }
    }
}
