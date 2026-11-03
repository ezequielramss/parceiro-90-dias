package br.com.kallopolli.parceiro.alerta;

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

    /** Grava CONVERSEI ou NAO_CONSEGUI com respondido_em. */
    void responder(long entregaId, String resposta);
}
