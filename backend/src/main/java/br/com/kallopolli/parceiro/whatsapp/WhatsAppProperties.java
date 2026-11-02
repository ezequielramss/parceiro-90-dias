package br.com.kallopolli.parceiro.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da WhatsApp Cloud API. Os segredos vêm só de variáveis de ambiente
 * (ver application.yml e .env.example).
 */
@Validated
@ConfigurationProperties("whatsapp")
public record WhatsAppProperties(
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String token,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String phoneNumberId,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String verifyToken,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String appSecret,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String graphVersion,
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {

    /**
     * O binder do Spring Boot deixa um placeholder sem variável como texto literal
     * ("${WHATSAPP_TOKEN}"), então só @NotBlank não impede a subida sem segredos.
     */
    static final String DEFINIDA = "^(?!\\$\\{).*";
    static final String NAO_DEFINIDA = "variável de ambiente não definida";

    /** O toString padrão de record imprimiria os segredos. */
    @Override
    public String toString() {
        return "WhatsAppProperties[phoneNumberId=" + phoneNumberId
                + ", graphVersion=" + graphVersion
                + ", baseUrl=" + baseUrl
                + ", token=***, verifyToken=***, appSecret=***]";
    }
}
