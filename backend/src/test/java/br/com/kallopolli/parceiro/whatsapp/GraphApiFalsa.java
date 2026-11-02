package br.com.kallopolli.parceiro.whatsapp;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Servidor HTTP local que faz o papel da Graph API nos testes. Nada sai da máquina. */
class GraphApiFalsa implements AutoCloseable {

    record Requisicao(String metodo, String caminho, String authorization, String corpo) {}

    private final HttpServer servidor;
    private final BlockingQueue<Requisicao> recebidas = new LinkedBlockingQueue<>();
    private volatile int status = 200;
    private volatile String resposta = "{\"messages\":[{\"id\":\"wamid.RESPOSTA\"}]}";
    private volatile long atrasoMs = 0;

    GraphApiFalsa() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recebidas.add(new Requisicao(
                    troca.getRequestMethod(),
                    troca.getRequestURI().getPath(),
                    troca.getRequestHeaders().getFirst("Authorization"),
                    corpo));
            try {
                Thread.sleep(atrasoMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void responder(int status, String resposta) {
        this.status = status;
        this.resposta = resposta;
    }

    void atrasar(long ms) {
        this.atrasoMs = ms;
    }

    BlockingQueue<Requisicao> recebidas() {
        return recebidas;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
