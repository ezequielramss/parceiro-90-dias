package br.com.kallopolli.parceiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A aplicação não sobe sem as variáveis de ambiente dos segredos (H38).
 *
 * <p>Sobe a aplicação num processo Java separado, numa pasta temporária vazia e com as variáveis
 * de segredo removidas do ambiente. O application.yml importa "optional:file:.env" relativo à
 * pasta do processo; assim nenhum backend/.env local entra no teste, exista ele ou não, e
 * variáveis reais definidas na máquina também não.
 */
class SemSegredosNaoSobeTest {

    private static final List<String> SEGREDOS = List.of(
            "WHATSAPP_TOKEN", "WHATSAPP_PHONE_NUMBER_ID", "WHATSAPP_VERIFY_TOKEN", "WHATSAPP_APP_SECRET",
            "WHATSAPP_GRAPH_VERSION", "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD",
            "GEMINI_API_KEY", "GEMINI_MODEL");

    @TempDir
    Path pastaSemEnv;

    record Resultado(boolean terminou, int codigo, String saida) {}

    @Test
    void aplicacaoNaoSobeSemNenhumaVariavel() throws Exception {
        Resultado r = subir(Map.of());

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START")
                .containsAnyOf("whatsapp.", "parceiro.banco.", "gemini.")
                .doesNotContain("Started ParceiroApplication");
    }

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoBanco() throws Exception {
        Resultado r = subir(Map.of(
                "WHATSAPP_TOKEN", "token-falso", "WHATSAPP_PHONE_NUMBER_ID", "1",
                "WHATSAPP_VERIFY_TOKEN", "verify-falso", "WHATSAPP_APP_SECRET", "segredo-falso",
                "WHATSAPP_GRAPH_VERSION", "v99.0",
                "GEMINI_API_KEY", "chave-falsa", "GEMINI_MODEL", "modelo-falso"));

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START", "parceiro.banco.")
                .doesNotContain("Started ParceiroApplication");
    }

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoGemini() throws Exception {
        Resultado r = subir(Map.of(
                "WHATSAPP_TOKEN", "token-falso", "WHATSAPP_PHONE_NUMBER_ID", "1",
                "WHATSAPP_VERIFY_TOKEN", "verify-falso", "WHATSAPP_APP_SECRET", "segredo-falso",
                "WHATSAPP_GRAPH_VERSION", "v99.0",
                "DATABASE_URL", "jdbc:postgresql://127.0.0.1:1/nao-usado", "DATABASE_USER", "usuario-falso",
                "DATABASE_PASSWORD", "senha-falsa"));

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START", "gemini.")
                .doesNotContain("Started ParceiroApplication", "chave-falsa");
    }

    private Resultado subir(Map<String, String> variaveis) throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"),
                ParceiroApplication.class.getName(), "--server.port=0")
                .directory(pastaSemEnv.toFile())
                .redirectErrorStream(true);
        SEGREDOS.forEach(pb.environment()::remove);
        pb.environment().putAll(variaveis);

        Process processo = pb.start();
        CompletableFuture<String> saida = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        boolean terminou = processo.waitFor(90, TimeUnit.SECONDS);
        if (!terminou) {
            processo.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
        return new Resultado(terminou, terminou ? processo.exitValue() : -1, saida.join());
    }
}
