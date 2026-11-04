package br.com.kallopolli.parceiro.alerta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertaEntregaRepository {

    String CONVERSEI = "CONVERSEI";
    String NAO_CONSEGUI = "NAO_CONSEGUI";

    record Entrega(long id, long alertaId, UUID responsavelId, boolean falhaEnvio, String resposta) {}

    /** Uma linha por alerta e responsável (unicidade no banco). Devolve o id. */
    long criar(long alertaId, UUID responsavelId);

    void registrarEnvio(long entregaId, String wamid);

    void marcarFalha(long entregaId);

    Optional<Entrega> buscarEntrega(long entregaId);

    /**
     * A entrega mais recente ainda aberta (enviada, sem falha e sem CONVERSEI) de qualquer um dos
     * responsáveis. É a que recebe a resposta CONVERSEI ou NÃO CONSEGUI digitada.
     */
    Optional<Long> maisRecenteAberta(List<UUID> responsavelIds);

    /** Grava CONVERSEI ou NAO_CONSEGUI com respondido_em. */
    void responder(long entregaId, String resposta);
}
