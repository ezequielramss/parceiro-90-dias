package br.com.kallopolli.parceiro.classificacao;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do Gemini. GEMINI_API_KEY e GEMINI_MODEL vêm só do ambiente, sem valor padrão:
 * sem elas a aplicação não sobe.
 */
@Validated
@ConfigurationProperties("gemini")
public record GeminiProperties(
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String apiKey,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String model,
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Positive int maxCaracteres,
        @Min(0) int novasTentativas,
        @NotNull Duration esperaInicial,
        @NotNull Duration esperaMaxima) {

    /** Placeholder sem variável fica como texto literal ("${GEMINI_API_KEY}"); isso não vale. */
    static final String DEFINIDA = "^(?!\\$\\{).*";
    static final String NAO_DEFINIDA = "variável de ambiente não definida";

    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + ", baseUrl=" + baseUrl + ", apiKey=***]";
    }
}
