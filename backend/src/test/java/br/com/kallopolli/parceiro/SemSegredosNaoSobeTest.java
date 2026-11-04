package br.com.kallopolli.parceiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A aplicação não sobe sem as variáveis de ambiente dos segredos (H38), e sobe sem nenhuma
 * variável da Meta.
 *
 * <p>Sobe a aplicação num processo Java separado, numa pasta temporária vazia e com as variáveis
 * de segredo removidas do ambiente. O application.yml importa "optional:file:.env" relativo à
 * pasta do processo; assim nenhum backend/.env local entra no teste, exista ele ou não, e
 * variáveis reais definidas na máquina também não.
 */
class SemSegredosNaoSobeTest {

    private static final List<String> SEGREDOS = List.of(
            "WHATSAPP_CANAL", "EVOLUTION_API_URL", "EVOLUTION_API_KEY", "EVOLUTION_INSTANCE",
            "EVOLUTION_WEBHOOK_SECRET", "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD",
            "GEMINI_API_KEY", "GEMINI_MODEL", "SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY",
            // Da Meta, que saiu do projeto: não podem fazer falta nem diferença.
            "WHATSAPP_TOKEN", "WHATSAPP_PHONE_NUMBER_ID", "WHATSAPP_VERIFY_TOKEN", "WHATSAPP_APP_SECRET",
            "WHATSAPP_GRAPH_VERSION");

    private static final Map<String, String> BANCO = Map.of(
            "DATABASE_URL", "jdbc:postgresql://127.0.0.1:1/nao-usado", "DATABASE_USER", "usuario-falso",
            "DATABASE_PASSWORD", "senha-falsa");
    private static final Map<String, String> GEMINI = Map.of("GEMINI_API_KEY", "chave-falsa", "GEMINI_MODEL", "modelo-falso");
    private static final Map<String, String> EVOLUTION = Map.of(
            "EVOLUTION_API_URL", "http://127.0.0.1:1", "EVOLUTION_API_KEY", "apikey-falsa",
            "EVOLUTION_INSTANCE", "instancia-falsa", "EVOLUTION_WEBHOOK_SECRET", "segredo-falso");

    @TempDir
    Path pastaSemEnv;

    record Resultado(boolean terminou, boolean subiu, int codigo, String saida) {}

    @Test
    void aplicacaoNaoSobeSemNenhumaVariavel() throws Exception {
        Resultado r = subir(Map.of());

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START")
                .containsAnyOf("EVOLUTION_", "parceiro.banco.", "gemini.")
                .doesNotContain("Started ParceiroApplication");
    }

    @Test
    void canalEvolutionNaoSobeSemAsVariaveisDaEvolution() throws Exception {
        Resultado r = subir(juntar(BANCO, GEMINI));

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START", "EVOLUTION_API_URL", "EVOLUTION_API_KEY",
                        "EVOLUTION_INSTANCE", "EVOLUTION_WEBHOOK_SECRET")
                .doesNotContain("Started ParceiroApplication");
    }

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoBanco() throws Exception {
        Resultado r = subir(juntar(EVOLUTION, GEMINI));

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START", "parceiro.banco.")
                .doesNotContain("Started ParceiroApplication", "apikey-falsa", "segredo-falso");
    }

    @Test
    void aplicacaoNaoSobeSemAsVariaveisDoGemini() throws Exception {
        Resultado r = subir(juntar(EVOLUTION, BANCO));

        assertThat(r.terminou()).as("a aplicação deveria ter parado sozinha").isTrue();
        assertThat(r.codigo()).isNotZero();
        assertThat(r.saida())
                .contains("APPLICATION FAILED TO START", "gemini.")
                .doesNotContain("Started ParceiroApplication", "chave-falsa");
    }

    @Test
    void canalEvolutionSobeSemNenhumaVariavelDaMeta() throws Exception {
        Resultado r = subir(juntar(EVOLUTION, juntar(BANCO, GEMINI)));

        assertThat(r.subiu()).as("a aplicação deveria ter subido; saída:\n%s", r.saida()).isTrue();
        assertThat(r.saida()).doesNotContain("apikey-falsa", "segredo-falso");
    }

    @Test
    void simuladorSobeSemEvolutionNemMeta() throws Exception {
        Map<String, String> variaveis = juntar(BANCO, GEMINI);
        variaveis.put("WHATSAPP_CANAL", "simulador");

        Resultado r = subir(variaveis);

        assertThat(r.subiu()).as("a aplicação deveria ter subido; saída:\n%s", r.saida()).isTrue();
    }

    private static Map<String, String> juntar(Map<String, String> a, Map<String, String> b) {
        Map<String, String> m = new HashMap<>(a);
        m.putAll(b);
        return m;
    }

    /** Espera o processo parar sozinho, ou subir (então ele é encerrado aqui). */
    private Resultado subir(Map<String, String> variaveis) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"),
                ParceiroApplication.class.getName(), "--server.port=0",
                "--parceiro.checkin.ativo=false", "--parceiro.retencao.ativo=false")
                .directory(pastaSemEnv.toFile())
                .redirectErrorStream(true);
        SEGREDOS.forEach(pb.environment()::remove);
        pb.environment().putAll(variaveis);

        Process processo = pb.start();
        CompletableFuture<Void> subiu = new CompletableFuture<>();
        StringBuilder texto = new StringBuilder();
        CompletableFuture<String> saida = CompletableFuture.supplyAsync(() -> {
            try (var leitor = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = leitor.readLine()) != null) {
                    synchronized (texto) {
                        texto.append(linha).append('\n');
                    }
                    if (linha.contains("Started ParceiroApplication")) {
                        subiu.complete(null);
                    }
                }
            } catch (IOException e) {
                // processo encerrado
            }
            synchronized (texto) {
                return texto.toString();
            }
        });
        CompletableFuture.anyOf(subiu, processo.onExit()).get(90, TimeUnit.SECONDS);
        boolean subiuMesmo = subiu.isDone();
        if (subiuMesmo || processo.isAlive()) {
            processo.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
        boolean terminou = !subiuMesmo;
        String tudo = saida.get(20, TimeUnit.SECONDS);
        return new Resultado(terminou, subiuMesmo, terminou ? processo.exitValue() : -1, tudo);
    }
}
