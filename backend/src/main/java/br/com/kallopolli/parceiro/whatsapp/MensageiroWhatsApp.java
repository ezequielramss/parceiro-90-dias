package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Envia mensagens a um colaborador e grava a mensagem de saída. */
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
        registrar(colaborador, client.enviarTexto(colaborador.numeroWhatsApp(), texto), texto);
    }

    /** Mensagem com botões de resposta. @throws WhatsAppClientException se a Graph API recusar */
    public void enviarBotoes(Colaborador colaborador, String corpo, List<Botao> botoes) {
        registrar(colaborador, client.enviarBotoes(colaborador.numeroWhatsApp(), corpo, botoes), corpo);
    }

    private void registrar(Colaborador colaborador, String wamid, String texto) {
        if (wamid != null) {
            mensagens.registrarSaida(wamid, colaborador.id(), texto);
        }
    }
}
