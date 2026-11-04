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

/**
 * Servidor HTTP local que faz o papel da Evolution API nos testes (POST
 * /message/sendText/{instância}). Nada sai da máquina; números e textos são fictícios.
 */
public class EvolutionFalsa implements AutoCloseable {

    /** Uma chamada recebida: método, caminho, cabeçalho apikey e corpo. */
    public record Requisicao(String metodo, String caminho, String apikey, String corpo) {}

    /** Resposta 200 padrão: um id novo a cada envio, em key.id, como a Evolution devolve. */
    public static final String ID_GERADO = null;

    private final HttpServer servidor;
    private final BlockingQueue<Requisicao> recebidas = new LinkedBlockingQueue<>();
    private final Set<String> comFalha = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequencia = new AtomicLong();
    private volatile int status = 200;
    private volatile String resposta = ID_GERADO;
    private volatile long atrasoMs = 0;

    public EvolutionFalsa() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recebidas.add(new Requisicao(troca.getRequestMethod(), troca.getRequestURI().getPath(),
                    troca.getRequestHeaders().getFirst("apikey"), corpo));
            try {
                Thread.sleep(atrasoMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int codigo = status;
            String texto = resposta != null
                    ? resposta
                    : "{\"key\":{\"remoteJid\":\"x@s.whatsapp.net\",\"fromMe\":true,\"id\":\"EVO-SAIDA-"
                        + sequencia.incrementAndGet() + "\"},\"status\":\"PENDING\"}";
            if (comFalha.stream().anyMatch(n -> corpo.contains("\"number\":\"" + n + "\""))) {
                codigo = 400;
                texto = "{\"status\":400,\"error\":\"Bad Request\",\"response\":{\"message\":[\"falha ficticia\"]}}";
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

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public void responder(int status, String resposta) {
        this.status = status;
        this.resposta = resposta;
    }

    public void atrasar(long ms) {
        this.atrasoMs = ms;
    }

    /** Faz o envio para este número (só dígitos) falhar com 400. */
    public void falharPara(String numero) {
        comFalha.add(numero);
    }

    public void limpar() {
        comFalha.clear();
        status = 200;
        resposta = ID_GERADO;
        atrasoMs = 0;
        recebidas.clear();
    }

    public BlockingQueue<Requisicao> recebidas() {
        return recebidas;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
