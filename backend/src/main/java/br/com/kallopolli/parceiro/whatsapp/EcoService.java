package br.com.kallopolli.parceiro.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Prova de ponta a ponta da Etapa 0: responde "Recebi: &lt;texto&gt;" a toda mensagem de texto.
 * Roda fora da thread do webhook para o POST responder 200 sem esperar a Graph API.
 */
@Service
public class EcoService {

    private static final Logger log = LoggerFactory.getLogger(EcoService.class);

    private final WhatsAppClient whatsAppClient;

    public EcoService(WhatsAppClient whatsAppClient) {
        this.whatsAppClient = whatsAppClient;
    }

    @Async
    public void responder(String wamidRecebido, String de, String texto) {
        try {
            String wamidEnviado = whatsAppClient.enviarTexto(de, "Recebi: " + texto);
            log.atInfo().setMessage("eco enviado")
                    .addKeyValue("wamid", wamidRecebido)
                    .addKeyValue("wamid_resposta", wamidEnviado)
                    .log();
        } catch (WhatsAppClientException e) {
            log.atWarn().setMessage("eco falhou")
                    .addKeyValue("wamid", wamidRecebido)
                    .addKeyValue("status_http", e.status())
                    .addKeyValue("codigo_meta", e.codigoMeta())
                    .log();
        }
    }
}
