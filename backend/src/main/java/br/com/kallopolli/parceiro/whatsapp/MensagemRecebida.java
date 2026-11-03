package br.com.kallopolli.parceiro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * Mensagem que chegou pelo webhook: texto, toque em botão ou um tipo que o assistente não lê.
 * O toString não mostra número, texto nem botão, para nunca irem parar em log por engano.
 */
public record MensagemRecebida(String wamid, String de, String tipo, String texto, String botaoId, String botaoTitulo) {

    /** Tipos aceitos pela coluna mensagens.tipo; qualquer outro vira "desconhecido". */
    private static final String TIPO_VALIDO = "^[a-z_]+$";

    /** Tipos que recebem "por enquanto só leio texto" (H03/IA 2). */
    private static final Set<String> NAO_SUPORTADOS = Set.of("audio", "image", "video", "sticker", "document");

    public MensagemRecebida(String wamid, String de, String tipo, String texto) {
        this(wamid, de, tipo, texto, null, null);
    }

    static MensagemRecebida de(JsonNode mensagem) {
        String tipo = mensagem.path("type").asText("");
        String botaoId = null;
        String botaoTitulo = null;
        if ("interactive".equals(tipo)) {
            JsonNode resposta = mensagem.path("interactive").path("button_reply");
            botaoId = resposta.path("id").asText(null);
            botaoTitulo = resposta.path("title").asText(null);
        } else if ("button".equals(tipo)) {
            botaoId = mensagem.path("button").path("payload").asText(null);
            botaoTitulo = mensagem.path("button").path("text").asText(null);
        }
        return new MensagemRecebida(
                mensagem.path("id").asText(null),
                mensagem.path("from").asText(null),
                tipo.matches(TIPO_VALIDO) ? tipo : "desconhecido",
                mensagem.path("text").path("body").asText(null),
                botaoId,
                botaoTitulo);
    }

    /** O que a pessoa escreveu ou o título do botão que tocou. */
    public String conteudo() {
        return texto != null ? texto : botaoTitulo;
    }

    public boolean tocouBotao() {
        return botaoId != null;
    }

    public boolean naoSuportado() {
        return NAO_SUPORTADOS.contains(tipo);
    }

    @Override
    public String toString() {
        return "MensagemRecebida[wamid=" + wamid + ", tipo=" + tipo + "]";
    }
}
