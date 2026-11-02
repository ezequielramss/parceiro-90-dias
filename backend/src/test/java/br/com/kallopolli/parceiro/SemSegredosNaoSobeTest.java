package br.com.kallopolli.parceiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class SemSegredosNaoSobeTest {

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoWhatsApp() {
        assumeTrue(System.getenv("WHATSAPP_TOKEN") == null, "WHATSAPP_TOKEN definido nesta máquina");

        // Ignora um .env local para simular um ambiente sem segredos.
        Throwable erro = catchThrowable(() -> new SpringApplicationBuilder(ParceiroApplication.class)
                .run("--server.port=0", "--spring.config.import=optional:file:nao-existe.properties"));

        assertThat(erro).isNotNull();
        assertThat(erro).rootCause().hasMessageContaining("whatsapp");
    }
}
