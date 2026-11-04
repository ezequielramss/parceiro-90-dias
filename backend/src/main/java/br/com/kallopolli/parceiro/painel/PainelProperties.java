package br.com.kallopolli.parceiro.painel;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do dashboard. URL e chave publicável do Supabase vêm só do ambiente e são
 * opcionais: sem elas o painel responde 503 e o resto da aplicação sobe normalmente.
 */
@Validated
@ConfigurationProperties("parceiro.dashboard")
public record PainelProperties(
        String supabaseUrl,
        String supabasePublishableKey,
        boolean cookieSeguro,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration jwksCache) {

    /** Sem URL ou chave (vazias ou placeholder não resolvido), o painel fica indisponível. */
    public boolean configurado() {
        return definida(supabaseUrl) && definida(supabasePublishableKey);
    }

    /** URL sem a barra final, como aparece no emissor do token. */
    String urlBase() {
        return supabaseUrl.strip().replaceAll("/+$", "");
    }

    /** Emissor dos tokens do projeto (claim iss). */
    String emissor() {
        return urlBase() + "/auth/v1";
    }

    private static boolean definida(String valor) {
        return valor != null && !valor.isBlank() && !valor.startsWith("${");
    }

    /** O toString padrão de record imprimiria a chave. */
    @Override
    public String toString() {
        return "PainelProperties[supabaseUrl=" + supabaseUrl
                + ", cookieSeguro=" + cookieSeguro
                + ", supabasePublishableKey=***]";
    }
}
