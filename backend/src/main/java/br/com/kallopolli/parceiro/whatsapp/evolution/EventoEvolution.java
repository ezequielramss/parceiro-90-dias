package br.com.kallopolli.parceiro.whatsapp.evolution;

import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converte o evento messages.upsert da Evolution API (v2.3.7) em {@link MensagemRecebida}.
 *
 * <p>O corpo é {event, instance, data, ...}; data traz key {remoteJid, fromMe, id, remoteJidAlt},
 * message e messageType. A documentação não mostra esse formato: ele foi lido no código da versão
 * fixada (webhook.controller.ts e whatsapp.baileys.service.ts).
 *
 * <p>Só aceita conversa individual (@s.whatsapp.net) que não foi enviada pelo próprio número
 * pareado. Grupos, status, transmissões, canais e eventos que não são de mensagem são ignorados,
 * com o motivo (sem dados) para o log.
 */
final class EventoEvolution {

    static final String MESSAGES_UPSERT = "MESSAGES_UPSERT";
    private static final String INDIVIDUAL = "@s.whatsapp.net";

    /** Tipos de mídia que recebem "por enquanto só leio texto". */
    private static final Map<String, String> MIDIAS = Map.of(
            "audioMessage", "audio", "imageMessage", "image", "videoMessage", "video",
            "stickerMessage", "sticker", "documentMessage", "document",
            "documentWithCaptionMessage", "document", "ptvMessage", "video");

    /** Resultado da leitura: a mensagem, ou o motivo de ter sido ignorada. */
    record Leitura(MensagemRecebida mensagem, String ignorada) {
        static Leitura ignorar(String motivo) {
            return new Leitura(null, motivo);
        }
    }

    private EventoEvolution() {}

    static List<Leitura> ler(JsonNode raiz) {
        String evento = raiz.path("event").asText("").replaceAll("[.-]", "_").toUpperCase();
        if (!MESSAGES_UPSERT.equals(evento)) {
            return List.of(Leitura.ignorar("EVENTO_NAO_E_MENSAGEM"));
        }
        JsonNode data = raiz.path("data");
        List<Leitura> leituras = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(d -> leituras.add(lerMensagem(d)));
        } else {
            leituras.add(lerMensagem(data));
        }
        return leituras;
    }

    private static Leitura lerMensagem(JsonNode data) {
        JsonNode key = data.path("key");
        if (key.path("fromMe").asBoolean(false)) {
            return Leitura.ignorar("ENVIADA_PELO_PROPRIO_NUMERO");
        }
        String jid = key.path("remoteJid").asText("");
        String alternativo = key.path("remoteJidAlt").asText("");
        if (jid.endsWith("@lid") && alternativo.endsWith(INDIVIDUAL)) {
            jid = alternativo;
        }
        if (!jid.endsWith(INDIVIDUAL)) {
            return Leitura.ignorar("NAO_E_CONVERSA_INDIVIDUAL");
        }
        String numero = jid.substring(0, jid.indexOf('@'));
        int dispositivo = numero.indexOf(':');
        if (dispositivo >= 0) {
            numero = numero.substring(0, dispositivo);
        }
        String id = key.path("id").asText("");
        if (!numero.matches("\\d{8,15}") || id.isBlank()) {
            return Leitura.ignorar("SEM_NUMERO_OU_ID");
        }

        JsonNode message = data.path("message");
        String texto = textoDe(message);
        if (texto != null) {
            return new Leitura(new MensagemRecebida(id, numero, MensagemRecebida.TEXTO, texto), null);
        }
        String tipo = MIDIAS.get(data.path("messageType").asText(""));
        if (tipo == null) {
            tipo = MIDIAS.keySet().stream().filter(message::has).findFirst().map(MIDIAS::get).orElse(null);
        }
        if (tipo == null) {
            return Leitura.ignorar("TIPO_SEM_TRATAMENTO");
        }
        return new Leitura(new MensagemRecebida(id, numero, tipo, null), null);
    }

    private static String textoDe(JsonNode message) {
        if (message.path("conversation").isTextual()) {
            return message.path("conversation").asText();
        }
        JsonNode estendido = message.path("extendedTextMessage").path("text");
        return estendido.isTextual() ? estendido.asText() : null;
    }
}
