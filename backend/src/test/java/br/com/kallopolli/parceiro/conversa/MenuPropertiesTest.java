package br.com.kallopolli.parceiro.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** O limite do texto longo é validado na subida. */
class MenuPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MenuProperties.class)
    static class Config {}

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void padraoE120() {
        contexto.run(c -> {
            assertThat(c.getBean(MenuProperties.class).textoLongoCaracteres()).isEqualTo(120);
            assertThat(c.getBean(MenuProperties.class).triagemSinaisGraves()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "120", "4096"})
    void aceitaValorNaFaixa(String valor) {
        contexto.withPropertyValues("parceiro.menu.texto-longo-caracteres=" + valor)
                .run(c -> assertThat(c).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "19", "4097", "abc", ""})
    void rejeitaValorInvalidoNaSubida(String valor) {
        contexto.withPropertyValues("parceiro.menu.texto-longo-caracteres=" + valor)
                .run(c -> assertThat(c).hasFailed());
    }
}
