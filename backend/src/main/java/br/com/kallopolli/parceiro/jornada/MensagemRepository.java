package br.com.kallopolli.parceiro.jornada;

import java.util.UUID;

public interface MensagemRepository {

    /**
     * Registra uma mensagem recebida. Devolve false se o wamid já existia: é a
     * garantia de idempotência do webhook (wamid único no banco).
     */
    boolean registrarEntrada(String wamid, UUID colaboradorId, String tipo, String texto);

    void registrarSaida(String wamid, UUID colaboradorId, String texto);
}
