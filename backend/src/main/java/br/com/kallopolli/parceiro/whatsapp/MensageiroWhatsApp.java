package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import org.springframework.stereotype.Service;

/** Envia texto a um colaborador e grava a mensagem de saída. */
@Service
public class MensageiroWhatsApp {

    private final WhatsAppClient client;
    private final MensagemRepository mensagens;

    public MensageiroWhatsApp(WhatsAppClient client, MensagemRepository mensagens) {
        this.client = client;
        this.mensagens = mensagens;
    }

    /** @throws WhatsAppClientException se a Graph API recusar ou não responder */
    public void enviar(Colaborador colaborador, String texto) {
        String wamid = client.enviarTexto(colaborador.numeroWhatsApp(), texto);
        if (wamid != null) {
            mensagens.registrarSaida(wamid, colaborador.id(), texto);
        }
    }
}
