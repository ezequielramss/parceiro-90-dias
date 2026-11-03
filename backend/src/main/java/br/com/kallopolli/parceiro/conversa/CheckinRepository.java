package br.com.kallopolli.parceiro.conversa;

import java.util.Optional;
import java.util.UUID;

public interface CheckinRepository {

    /**
     * Insere o check-in do dia e devolve o id; vazio se o dia já existia (unicidade
     * colaborador + dia). Quem chama só envia se recebeu um id: o mesmo dia nunca sai duas vezes.
     */
    Optional<Long> criarSeNovo(UUID colaboradorId, int diaJornada);

    void registrarHumor(long checkinId, int humor);
}
