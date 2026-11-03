package br.com.kallopolli.parceiro.alerta;

public interface AlertaRepository {

    /** Cria o alerta ABERTO e devolve o id. destino: GERENTE, RH ou GERENTE_E_RH. */
    long criar(long relatoId, String destino, boolean urgente);

    /** Contato realizado (H20): ATENDIDO não é resolvido. */
    void marcarAtendido(long alertaId);
}
