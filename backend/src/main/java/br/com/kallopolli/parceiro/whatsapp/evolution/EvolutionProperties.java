package br.com.kallopolli.parceiro.whatsapp.evolution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

/**
 * Configuração da Evolution API. Os valores vêm só de variáveis de ambiente e só são
 * obrigatórios com parceiro.whatsapp.canal=evolution: por isso a conferência fica em
 * {@link #verificar()}, chamada pelo canal, e não em anotações (que valeriam também no simulador).
 */
@ConfigurationProperties("evolution")
public record EvolutionProperties(
        String apiUrl,
        String apiKey,
        String instance,
        String webhookSecret,
        Duration connectTimeout,
        Duration readTimeout) {

    /** Para a aplicação dizendo quais variáveis faltam, sem mostrar valor nenhum. */
    void verificar() {
        List<String> faltando = new ArrayList<>();
        if (!definida(apiUrl)) {
            faltando.add("EVOLUTION_API_URL");
        }
        if (!definida(apiKey)) {
            faltando.add("EVOLUTION_API_KEY");
        }
        if (!definida(instance)) {
            faltando.add("EVOLUTION_INSTANCE");
        }
        if (!definida(webhookSecret)) {
            faltando.add("EVOLUTION_WEBHOOK_SECRET");
        }
        if (!faltando.isEmpty()) {
            throw new InvalidConfigurationPropertyValueException("evolution", "<não definida>",
                    "com parceiro.whatsapp.canal=evolution, defina as variáveis de ambiente " + String.join(", ", faltando));
        }
    }

    String urlBase() {
        return apiUrl.strip().replaceAll("/+$", "");
    }

    private static boolean definida(String valor) {
        return valor != null && !valor.isBlank() && !valor.startsWith("${");
    }

    /** O toString padrão de record imprimiria a chave e o segredo. */
    @Override
    public String toString() {
        return "EvolutionProperties[apiUrl=" + apiUrl + ", instance=" + instance
                + ", apiKey=***, webhookSecret=***]";
    }
}
