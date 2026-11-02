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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook da WhatsApp Cloud API.
 *
 * <p>O POST valida a assinatura sobre o corpo bruto antes de qualquer conversão,
 * responde 200 na hora e entrega cada mensagem ao {@link ProcessadorMensagens},
 * que roda em outra thread. Os logs trazem só tipo e wamid; nunca texto, número ou nome.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties props;
    private final ObjectMapper objectMapper;
    private final AssinaturaWebhook assinatura;
    private final ProcessadorMensagens processador;

    public WhatsAppWebhookController(
            WhatsAppProperties props,
            ObjectMapper objectMapper,
            AssinaturaWebhook assinatura,
            ProcessadorMensagens processador) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.assinatura = assinatura;
        this.processador = processador;
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

    /** Recebe eventos. 401 sem processar se a assinatura falhar; senão 200 imediato. */
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String cabecalhoAssinatura,
            @RequestBody(required = false) byte[] corpo) {
        if (!assinatura.valida(corpo, cabecalhoAssinatura)) {
            log.warn("assinatura do webhook ausente ou inválida");
            return ResponseEntity.status(401).build();
        }

        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(corpo == null ? new byte[0] : corpo);
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
                    receberMensagem(MensagemRecebida.de(mensagem));
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

    private void receberMensagem(MensagemRecebida mensagem) {
        log.atInfo().setMessage("mensagem recebida")
                .addKeyValue("wamid", mensagem.wamid())
                .addKeyValue("tipo", mensagem.tipo())
                .log();
        if (mensagem.wamid() != null && mensagem.de() != null) {
            processador.processar(mensagem);
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
