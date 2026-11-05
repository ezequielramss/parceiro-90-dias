package br.com.kallopolli.parceiro.conversa;

/** Em que ponto da conversa o colaborador está (tabela conversa_estado). */
public record EstadoConversa(Tipo tipo, Long checkinId, Long relatoId, String agente) {

    public enum Tipo { LIVRE, AGUARDANDO_HUMOR, AGUARDANDO_CONFIRMACAO_RELATO, EM_AGENTE }

    public static final EstadoConversa LIVRE = new EstadoConversa(Tipo.LIVRE, null, null, null);

    public static EstadoConversa aguardandoHumor(long checkinId) {
        return new EstadoConversa(Tipo.AGUARDANDO_HUMOR, checkinId, null, null);
    }

    public static EstadoConversa aguardandoConfirmacao(long relatoId) {
        return new EstadoConversa(Tipo.AGUARDANDO_CONFIRMACAO_RELATO, null, relatoId, null);
    }

    /** A pessoa escolheu um agente no menu; cada mensagem vai para ele. */
    public static EstadoConversa emAgente(String codigo) {
        return new EstadoConversa(Tipo.EM_AGENTE, null, null, codigo);
    }
}
