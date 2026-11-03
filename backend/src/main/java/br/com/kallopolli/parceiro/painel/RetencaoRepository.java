package br.com.kallopolli.parceiro.painel;

import java.time.Instant;

/** Apagamento dos textos com mais de 90 dias, prometido no termo de participação. */
public interface RetencaoRepository {

    /** Quantas linhas perderam texto nesta rodada. */
    record Apagados(int relatos, int mensagens) {}

    /**
     * Relatos criados antes do limite perdem texto e motivo; mensagens recebidas ou enviadas antes
     * dele perdem o texto (a mensagem do relato e a confirmação "Entendi assim: ..." repetem o
     * relato e o motivo). Categoria, destino, datas e contagens ficam. Rodar de novo não muda nada.
     */
    Apagados apagarTextosAntesDe(Instant limite);
}
