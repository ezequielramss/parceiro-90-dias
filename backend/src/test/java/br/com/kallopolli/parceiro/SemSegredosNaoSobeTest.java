package br.com.kallopolli.parceiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class SemSegredosNaoSobeTest {

    // Ignora um .env local para simular um ambiente sem segredos.
    private static final String SEM_ENV = "--spring.config.import=optional:file:nao-existe.properties";

    @BeforeEach
    void exigeAmbienteLimpo() {
        assumeTrue(System.getenv("WHATSAPP_TOKEN") == null && System.getenv("DATABASE_URL") == null,
                "variáveis reais definidas nesta máquina");
    }

    @Test
    void aplicacaoNaoSobeSemNenhumaVariavel() {
        Throwable erro = catchThrowable(() -> new SpringApplicationBuilder(ParceiroApplication.class)
                .run("--server.port=0", SEM_ENV));

        assertThat(erro).isNotNull();
        assertThat(erro).rootCause().message().containsAnyOf("whatsapp", "parceiro.banco");
    }

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoBanco() {
        Throwable erro = catchThrowable(() -> new SpringApplicationBuilder(ParceiroApplication.class)
                .run("--server.port=0", SEM_ENV,
                        "--WHATSAPP_TOKEN=x", "--WHATSAPP_PHONE_NUMBER_ID=1", "--WHATSAPP_VERIFY_TOKEN=v",
                        "--WHATSAPP_APP_SECRET=s", "--WHATSAPP_GRAPH_VERSION=v99.0"));

        assertThat(erro).isNotNull();
        assertThat(erro).rootCause().message().contains("parceiro.banco");
    }
}
