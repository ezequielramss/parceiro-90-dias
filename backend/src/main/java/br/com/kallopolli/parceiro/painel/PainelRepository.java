package br.com.kallopolli.parceiro.painel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leituras do painel, somente leitura. O filtro de quem vê o quê fica DENTRO da consulta, nunca
 * depois em Java: cada método recebe o {@link Acesso} e devolve só o que aquele papel pode ver.
 *
 * <ul>
 *   <li>RH: todas as lojas, com motivo e ação sugerida;
 *   <li>GERENTE: só a própria loja; só alertas de categorias com destino GERENTE ou GERENTE_E_RH
 *       (nunca urgentes nem restritas); motivo sempre nulo; nenhum relato;
 *   <li>colaboradores: só ativos e com aceite ativo.
 * </ul>
 */
public interface PainelRepository {

    record ColaboradorPainel(UUID id, String nome, String loja, String turno, LocalDate dataInicio,
                             Instant ultimoContato) {}

    record CheckinPainel(UUID colaboradorId, int dia, Instant enviadoEm, Instant respondidoEm, Integer humor) {}

    /**
     * Alerta com o status já calculado (ABERTO há mais de 24 horas = SEM_RETORNO_24H) e o retorno
     * dos responsáveis: CONVERSEI se alguém conversou, senão NAO_CONSEGUI, senão nulo.
     * Para o GERENTE, colaboradorId e motivo vêm nulos.
     */
    record AlertaPainel(long id, UUID colaboradorId, String categoria, String destino, boolean urgente,
                        String status, Instant criadoEm, String motivo, String acaoSugerida, String retorno,
                        Instant respondidoEm, String destinatarios) {}

    /** Relato como o RH vê. Texto e motivo podem ser nulos depois da retenção de 90 dias. */
    record RelatoPainel(long id, UUID colaboradorId, String loja, String turno, String categoria, String estado,
                        boolean urgente, Instant criadoEm, String texto, String motivo, String acaoSugerida) {}

    record Contagem(String categoria, long quantidade) {}

    List<ColaboradorPainel> colaboradores(Acesso acesso);

    /** Vazio se o id não existe OU é de outra loja: a resposta é a mesma (404). */
    Optional<ColaboradorPainel> colaborador(Acesso acesso, UUID id);

    /** Check-ins dos colaboradores visíveis; colaboradorId nulo = todos. */
    List<CheckinPainel> checkins(Acesso acesso, UUID colaboradorId);

    /** status, destino e colaboradorId nulos = sem filtro. destino GERENTE ou RH inclui GERENTE_E_RH. */
    List<AlertaPainel> alertas(Acesso acesso, Instant agora, String status, String destino, UUID colaboradorId);

    /** Só para o RH: relatos encaminhados ao RH. Para o GERENTE, sempre vazio. */
    List<RelatoPainel> relatos(Acesso acesso, String estado);

    /**
     * Só para o RH: relatos encaminhados (com alerta) ou de categoria agregada, fora as categorias
     * restritas, de colaboradores visíveis, sem o texto. Para o GERENTE, sempre vazio.
     */
    List<RelatoPainel> relatosBemEstar(Acesso acesso);

    /**
     * Relatos por categoria, inclusive os que já perderam texto e motivo. RH: encaminhados ou
     * agregados. GERENTE: os da loja que viraram alerta para ele, mais os elogios.
     */
    List<Contagem> distribuicao(Acesso acesso);
}
