package br.com.kallopolli.parceiro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mensagem que chegou pelo webhook. O toString não mostra número nem texto,
 * para nunca irem parar em log por engano.
 */
public record MensagemRecebida(String wamid, String de, String tipo, String texto) {

    /** Tipos aceitos pela coluna mensagens.tipo; qualquer outro vira "desconhecido". */
    private static final String TIPO_VALIDO = "^[a-z_]+$";

    static MensagemRecebida de(JsonNode mensagem) {
        String tipo = mensagem.path("type").asText("");
        return new MensagemRecebida(
                mensagem.path("id").asText(null),
                mensagem.path("from").asText(null),
                tipo.matches(TIPO_VALIDO) ? tipo : "desconhecido",
                mensagem.path("text").path("body").asText(null));
    }

    /** Telefone do remetente em E.164; a Meta manda só os dígitos. */
    public String telefoneE164() {
        return de == null ? null : "+" + de;
    }

    @Override
    public String toString() {
        return "MensagemRecebida[wamid=" + wamid + ", tipo=" + tipo + "]";
    }
}
