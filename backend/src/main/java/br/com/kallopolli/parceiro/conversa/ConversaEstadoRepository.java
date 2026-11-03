package br.com.kallopolli.parceiro.conversa;

import java.util.UUID;

public interface ConversaEstadoRepository {

    /** Estado atual; LIVRE se o colaborador ainda não tem linha. */
    EstadoConversa estadoDe(UUID colaboradorId);

    /** Cria ou substitui o estado (uma linha por colaborador). */
    void definir(UUID colaboradorId, EstadoConversa estado);
}
