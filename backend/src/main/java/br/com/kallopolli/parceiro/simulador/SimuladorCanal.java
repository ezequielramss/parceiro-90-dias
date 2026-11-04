package br.com.kallopolli.parceiro.simulador;

import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.whatsapp.CanalSaida;
import br.com.kallopolli.parceiro.whatsapp.TelefoneBrasil;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Canal de saída do simulador local (parceiro.whatsapp.canal=simulador): em vez de mandar ao
 * WhatsApp, guarda as mensagens do robô numa fila em memória por número, com as últimas
 * {@value #MAXIMO}. Tudo se perde ao reiniciar. Nada daqui vai para log, auditoria ou banco além do
 * que o fluxo normal já grava.
 */
@Component
@ConditionalOnProperty(name = "parceiro.whatsapp.canal", havingValue = "simulador")
public class SimuladorCanal implements CanalSaida {

    static final int MAXIMO = 200;

    /** Mensagem do robô para um número, com os atalhos das opções escritas no texto. */
    public record Saida(long seq, String numero, String texto, List<String> opcoes, Instant enviadaEm) {
        @Override
        public String toString() {
            return "Saida[seq=" + seq + "]";
        }
    }

    private final Map<String, Deque<Saida>> filas = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    @Override
    public String enviarTexto(String numero, String texto) {
        var saida = new Saida(sequencia.incrementAndGet(), numero, texto, opcoes(texto), Instant.now());
        Deque<Saida> fila = filas.computeIfAbsent(numero, n -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addLast(saida);
            while (fila.size() > MAXIMO) {
                fila.removeFirst();
            }
        }
        return "sim-" + UUID.randomUUID();
    }

    /** Mensagens para o número (com ou sem o nono dígito) com seq maior que {@code depois}. */
    public List<Saida> depois(String numero, long depois) {
        List<Saida> lista = new ArrayList<>();
        for (String candidato : TelefoneBrasil.candidatosE164(numero)) {
            Deque<Saida> fila = filas.get(candidato.substring(1));
            if (fila == null) {
                continue;
            }
            synchronized (fila) {
                fila.stream().filter(s -> s.seq() > depois).forEach(lista::add);
            }
        }
        lista.sort((a, b) -> Long.compare(a.seq(), b.seq()));
        return lista;
    }

    /** Os textos que a pessoa digitaria, a partir das opções escritas na mensagem. */
    static List<String> opcoes(String texto) {
        if (texto.contains(TextosConversa.OPCOES_CONFIRMACAO)) {
            return List.of("SIM", "NÃO");
        }
        if (texto.contains(TextosConversa.OPCOES_ALERTA)) {
            return List.of("CONVERSEI", "NÃO CONSEGUI");
        }
        // Só o check-in pede a nota; as boas-vindas também falam em "nota de 1 a 5", mas sem pedir.
        if (texto.contains("Responda com uma nota de 1 a 5")) {
            return List.of("1", "2", "3", "4", "5");
        }
        if (texto.contains("responda ACEITO")) {
            return List.of("ACEITO");
        }
        return List.of();
    }
}
