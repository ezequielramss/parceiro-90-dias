package br.com.kallopolli.parceiro.conversa;

/** Em que ponto da conversa o colaborador está (tabela conversa_estado). */
public record EstadoConversa(Tipo tipo, Long checkinId, Long relatoId) {

    public enum Tipo { LIVRE, AGUARDANDO_HUMOR, AGUARDANDO_CONFIRMACAO_RELATO }

    public static final EstadoConversa LIVRE = new EstadoConversa(Tipo.LIVRE, null, null);

    public static EstadoConversa aguardandoHumor(long checkinId) {
        return new EstadoConversa(Tipo.AGUARDANDO_HUMOR, checkinId, null);
    }

    public static EstadoConversa aguardandoConfirmacao(long relatoId) {
        return new EstadoConversa(Tipo.AGUARDANDO_CONFIRMACAO_RELATO, null, relatoId);
    }
}
