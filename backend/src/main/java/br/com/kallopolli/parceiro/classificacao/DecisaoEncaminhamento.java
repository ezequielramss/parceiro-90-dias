package br.com.kallopolli.parceiro.classificacao;

/**
 * Para quem o relato vai e como.
 *
 * @param destino          GERENTE, GERENTE_E_RH, RH ou AGREGADO
 * @param urgente          o RH é avisado na hora
 * @param exigeConfirmacao o colaborador confirma o resumo antes do envio (H16)
 * @param semAlerta        não gera alerta (AGREGADO, ex.: elogio)
 */
public record DecisaoEncaminhamento(String destino, boolean urgente, boolean exigeConfirmacao, boolean semAlerta) {}
