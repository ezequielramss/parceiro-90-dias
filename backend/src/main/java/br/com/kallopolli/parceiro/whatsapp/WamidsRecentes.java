package br.com.kallopolli.parceiro.whatsapp;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Idempotência das mensagens de responsáveis (gerente e RH), que não são gravadas em
 * public.mensagens (a tabela exige colaborador). Guarda os últimos wamids em memória: cobre
 * os reenvios da Evolution enquanto o backend está no ar. Responder CONVERSEI ou NÃO CONSEGUI
 * duas vezes também não muda nada, pela própria regra.
 */
@Component
public class WamidsRecentes {

    private static final int MAXIMO = 10_000;

    private final Map<String, Boolean> vistos = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> maisAntigo) {
            return size() > MAXIMO;
        }
    };

    /** true na primeira vez que o wamid aparece. */
    public synchronized boolean novo(String wamid) {
        return vistos.putIfAbsent(wamid, Boolean.TRUE) == null;
    }
}
