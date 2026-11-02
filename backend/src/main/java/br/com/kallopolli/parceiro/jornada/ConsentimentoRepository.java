package br.com.kallopolli.parceiro.jornada;

import java.util.Optional;
import java.util.UUID;

public interface ConsentimentoRepository {

    boolean temAtivo(UUID colaboradorId);

    /** Registra o aceite e devolve o id do consentimento. */
    long registrarAceite(UUID colaboradorId, String textoAceito);

    /** Marca a retirada do consentimento ativo; vazio se não havia nenhum. */
    Optional<Long> registrarRetirada(UUID colaboradorId);
}
