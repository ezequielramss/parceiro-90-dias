package br.com.kallopolli.parceiro.conversa;

import br.com.kallopolli.parceiro.classificacao.Classificacao;
import java.util.Optional;
import java.util.UUID;

public interface RelatoRepository {

    String AGUARDANDO = "AGUARDANDO";
    String CONFIRMADO = "CONFIRMADO";
    String DESCARTADO = "DESCARTADO";

    /** Relato salvo, sem o texto (o texto nunca sai do banco para alerta ou log). */
    record RelatoSalvo(long id, UUID colaboradorId, Classificacao classificacao, String confirmacao) {}

    /** Grava o relato com confirmacao AGUARDANDO e devolve o id. */
    long criar(UUID colaboradorId, long mensagemId, String texto);

    void gravarClassificacao(long relatoId, Classificacao classificacao);

    /** O colaborador escreveu de novo durante a confirmação (H16). */
    void atualizarTexto(long relatoId, String texto);

    void definirConfirmacao(long relatoId, String confirmacao);

    /** PARAR: descarta o que estava aguardando confirmação. Devolve quantos. */
    int descartarAguardando(UUID colaboradorId);

    Optional<RelatoSalvo> buscarRelato(long relatoId);
}
