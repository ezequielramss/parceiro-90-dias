package br.com.kallopolli.parceiro.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook da WhatsApp Cloud API.
 *
 * <p>Etapa 0: sem validação de assinatura (X-Hub-Signature-256), sem idempotência e sem banco.
 * Os logs trazem só tipo e wamid; nunca texto, número de telefone ou nome.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties props;
    private final ObjectMapper objectMapper;
    private final EcoService ecoService;

    public WhatsAppWebhookController(WhatsAppProperties props, ObjectMapper objectMapper, EcoService ecoService) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.ecoService = ecoService;
    }

    /** Desafio de verificação que a Meta faz ao cadastrar a URL do webhook. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        if ("subscribe".equals(modo) && desafio != null && tokenConfere(verifyToken)) {
            log.info("webhook verificado pela Meta");
            return ResponseEntity.ok(desafio);
        }
        log.warn("verificação do webhook recusada");
        return ResponseEntity.status(403).build();
    }

    /** Recebe eventos. Sempre 200 para a Meta não reenviar; o trabalho pesado vai para outra thread. */
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receber(@RequestBody(required = false) String corpo) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(corpo == null ? "" : corpo);
        } catch (Exception e) {
            log.warn("payload do webhook não é JSON válido");
            return ResponseEntity.ok().build();
        }
        if (raiz == null || raiz.isMissingNode()) {
            log.warn("payload do webhook vazio");
            return ResponseEntity.ok().build();
        }

        for (JsonNode entry : raiz.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode valor = change.path("value");
                for (JsonNode mensagem : valor.path("messages")) {
                    tratarMensagem(mensagem);
                }
                for (JsonNode status : valor.path("statuses")) {
                    log.atInfo().setMessage("status recebido")
                            .addKeyValue("wamid", status.path("id").asText(null))
                            .addKeyValue("status", status.path("status").asText(null))
                            .log();
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    private void tratarMensagem(JsonNode mensagem) {
        String wamid = mensagem.path("id").asText(null);
        String tipo = mensagem.path("type").asText("desconhecido");
        log.atInfo().setMessage("mensagem recebida")
                .addKeyValue("wamid", wamid)
                .addKeyValue("tipo", tipo)
                .log();

        String de = mensagem.path("from").asText(null);
        String texto = mensagem.path("text").path("body").asText(null);
        if ("text".equals(tipo) && de != null && texto != null) {
            ecoService.responder(wamid, de, texto);
        }
    }

    private boolean tokenConfere(String recebido) {
        if (recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                props.verifyToken().getBytes(StandardCharsets.UTF_8));
    }
}
