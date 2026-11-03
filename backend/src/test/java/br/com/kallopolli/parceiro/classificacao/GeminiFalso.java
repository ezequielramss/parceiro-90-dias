package br.com.kallopolli.parceiro.classificacao;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;

/** Servidor HTTP local que faz o papel da API do Gemini nos testes. Nada sai da máquina. */
public class GeminiFalso implements AutoCloseable {

    public record Requisicao(String caminho, String chave, String corpo) {}

    public record Resposta(int status, String corpo, long atrasoMs) {}

    private final HttpServer servidor;
    private final List<Requisicao> recebidas = new CopyOnWriteArrayList<>();
    private final LinkedBlockingDeque<Resposta> roteiro = new LinkedBlockingDeque<>();
    private volatile Resposta padrao = new Resposta(500, "{}", 0);

    public GeminiFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recebidas.add(new Requisicao(
                    troca.getRequestURI().toString(),
                    troca.getRequestHeaders().getFirst("x-goog-api-key"),
                    corpo));
            Resposta r = roteiro.isEmpty() ? padrao : roteiro.poll();
            try {
                Thread.sleep(r.atrasoMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = r.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            try {
                troca.sendResponseHeaders(r.status(), bytes.length);
                troca.getResponseBody().write(bytes);
            } catch (IOException e) {
                // o cliente desistiu (timeout): nada a fazer
            }
            troca.close();
        });
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Respostas na ordem; depois que acabam, repete a última enfileirada. */
    public void responder(Resposta... respostas) {
        roteiro.clear();
        recebidas.clear();
        for (Resposta r : respostas) {
            roteiro.add(r);
        }
        padrao = respostas[respostas.length - 1];
    }

    public List<Requisicao> recebidas() {
        return List.copyOf(recebidas);
    }

    /** Resposta 200 no formato do generateContent, com o JSON da classificação no texto. */
    public static Resposta sucesso(String jsonDaClassificacao) {
        String texto = jsonDaClassificacao.replace("\\", "\\\\").replace("\"", "\\\"");
        return new Resposta(200, """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
                """.formatted(texto), 0);
    }

    public static Resposta classificacao(String categoria) {
        return sucesso("""
                {"categoria":"%s","motivo":"motivo ficticio","acao_sugerida":"acao ficticia",\
                "confianca":"alta","sinais_graves":false}""".formatted(categoria));
    }

    public static Resposta erro429(String retryDelay) {
        return new Resposta(429, """
                {"error":{"code":429,"message":"cota","status":"RESOURCE_EXHAUSTED","details":[
                  {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"%s"}]}}
                """.formatted(retryDelay), 0);
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
