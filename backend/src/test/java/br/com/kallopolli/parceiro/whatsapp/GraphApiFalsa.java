package br.com.kallopolli.parceiro.whatsapp;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Servidor HTTP local que faz o papel da Graph API nos testes. Nada sai da máquina. */
class GraphApiFalsa implements AutoCloseable {

    record Requisicao(String metodo, String caminho, String authorization, String corpo) {}

    /** Resposta 200 padrão: um wamid novo a cada envio. */
    static final String WAMID_GERADO = null;

    /** Erro da Meta quando a janela de 24 horas está fechada e não há modelo de mensagem. */
    static final String ERRO_JANELA_FECHADA = "{\"error\":{\"message\":\"fora da janela\",\"code\":131047}}";

    private final HttpServer servidor;
    private final BlockingQueue<Requisicao> recebidas = new LinkedBlockingQueue<>();
    private final Set<String> semJanela = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequencia = new AtomicLong();
    private volatile int status = 200;
    private volatile String resposta = WAMID_GERADO;
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
            int codigo = status;
            String texto = resposta != null
                    ? resposta
                    : "{\"messages\":[{\"id\":\"wamid.SAIDA-" + sequencia.incrementAndGet() + "\"}]}";
            if (semJanela.stream().anyMatch(n -> corpo.contains("\"to\":\"" + n + "\""))) {
                codigo = 400;
                texto = ERRO_JANELA_FECHADA;
            }
            byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(codigo, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        // Uma thread por requisição: um teste de timeout não pode atrasar as requisições dos seguintes.
        servidor.setExecutor(Executors.newCachedThreadPool());
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

    /** Simula a janela de 24 horas fechada para este número (sem +). */
    void fecharJanela(String numero) {
        semJanela.add(numero);
    }

    void reabrirJanelas() {
        semJanela.clear();
    }

    BlockingQueue<Requisicao> recebidas() {
        return recebidas;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
