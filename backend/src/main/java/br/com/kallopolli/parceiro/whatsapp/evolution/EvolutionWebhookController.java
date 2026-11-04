package br.com.kallopolli.parceiro.whatsapp.evolution;

import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import br.com.kallopolli.parceiro.whatsapp.ProcessadorMensagens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook da Evolution API. A Evolution manda, a cada chamada, os cabeçalhos configurados no
 * webhook da instância (campo headers de /webhook/set); um deles é o segredo compartilhado
 * {@value #CABECALHO}, comparado em tempo constante. Sem ele, ou com valor errado: 401 e nada é
 * processado. Depois responde 200 na hora e entrega cada mensagem ao {@link ProcessadorMensagens},
 * em outra thread. Os logs trazem só id, tipo e motivo; nunca texto, número, nome ou segredo.
 */
@RestController
@ConditionalOnProperty(name = "parceiro.whatsapp.canal", havingValue = "evolution", matchIfMissing = true)
public class EvolutionWebhookController {

    static final String CABECALHO = "X-Webhook-Secret";

    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookController.class);

    private final byte[] segredo;
    private final ObjectMapper objectMapper;
    private final ProcessadorMensagens processador;

    public EvolutionWebhookController(EvolutionProperties props, ObjectMapper objectMapper,
                                      ProcessadorMensagens processador) {
        props.verificar();
        this.segredo = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.processador = processador;
    }

    @PostMapping(path = "/webhook/evolution", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receber(@RequestHeader(name = CABECALHO, required = false) String recebido,
                                        @RequestBody(required = false) byte[] corpo) {
        if (recebido == null || !MessageDigest.isEqual(recebido.getBytes(StandardCharsets.UTF_8), segredo)) {
            log.warn("segredo do webhook ausente ou inválido");
            return ResponseEntity.status(401).build();
        }
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(corpo == null ? new byte[0] : corpo);
        } catch (Exception e) {
            log.warn("payload do webhook não é JSON válido");
            return ResponseEntity.ok().build();
        }
        if (raiz == null || !raiz.isObject()) {
            log.warn("payload do webhook vazio ou fora do formato");
            return ResponseEntity.ok().build();
        }
        for (EventoEvolution.Leitura leitura : EventoEvolution.ler(raiz)) {
            if (leitura.mensagem() == null) {
                log.atInfo().setMessage("evento ignorado").addKeyValue("motivo", leitura.ignorada()).log();
            } else {
                receberMensagem(leitura.mensagem());
            }
        }
        return ResponseEntity.ok().build();
    }

    private void receberMensagem(MensagemRecebida mensagem) {
        log.atInfo().setMessage("mensagem recebida")
                .addKeyValue("wamid", mensagem.wamid())
                .addKeyValue("tipo", mensagem.tipo())
                .log();
        processador.processar(mensagem);
    }
}
