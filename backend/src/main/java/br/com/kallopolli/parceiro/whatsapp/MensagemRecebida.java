package br.com.kallopolli.parceiro.whatsapp;

import java.util.Set;

/**
 * Mensagem recebida por qualquer canal (Evolution ou simulador), já no formato que o
 * {@link ProcessadorMensagens} usa: id da mensagem, número do remetente (só dígitos), tipo e texto.
 * O toString não mostra número nem texto, para nunca irem parar em log por engano.
 */
public record MensagemRecebida(String wamid, String de, String tipo, String texto) {

    public static final String TEXTO = "text";

    /** Tipos que recebem "por enquanto só leio texto" (H03/IA 2). */
    private static final Set<String> NAO_SUPORTADOS = Set.of("audio", "image", "video", "sticker", "document");

    /** O que a pessoa escreveu. */
    public String conteudo() {
        return texto;
    }

    public boolean naoSuportado() {
        return NAO_SUPORTADOS.contains(tipo);
    }

    @Override
    public String toString() {
        return "MensagemRecebida[wamid=" + wamid + ", tipo=" + tipo + "]";
    }
}
