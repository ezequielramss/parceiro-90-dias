package br.com.kallopolli.parceiro.conversa;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversaEstadoRepository {

    /** Sem mensagem por este tempo, EM_AGENTE vale como LIVRE. Não há tarefa agendada: vale na leitura. */
    Duration EXPIRACAO_AGENTE = Duration.ofMinutes(30);

    /** Estado gravado; LIVRE se o colaborador ainda não tem linha. */
    EstadoConversa estadoDe(UUID colaboradorId);

    /** Cria ou substitui o estado (uma linha por colaborador) e renova atualizado_em. */
    void definir(UUID colaboradorId, EstadoConversa estado);

    /** Quando o estado mudou pela última vez; vazio se o colaborador ainda não tem linha. */
    Optional<Instant> atualizadoEm(UUID colaboradorId);

    /** Estado que vale agora: EM_AGENTE há mais de 30 minutos sem mensagem conta como LIVRE. */
    default EstadoConversa estadoVigente(UUID colaboradorId, Instant agora) {
        EstadoConversa estado = estadoDe(colaboradorId);
        if (estado.tipo() != EstadoConversa.Tipo.EM_AGENTE) {
            return estado;
        }
        boolean expirado = atualizadoEm(colaboradorId)
                .map(quando -> quando.isBefore(agora.minus(EXPIRACAO_AGENTE)))
                .orElse(true);
        return expirado ? EstadoConversa.LIVRE : estado;
    }
}
