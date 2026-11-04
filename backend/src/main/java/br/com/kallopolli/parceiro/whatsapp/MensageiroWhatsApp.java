package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import org.springframework.stereotype.Service;

/** Envia mensagens a um colaborador pelo canal configurado e grava a mensagem de saída. */
@Service
public class MensageiroWhatsApp {

    private final CanalSaida canal;
    private final MensagemRepository mensagens;

    public MensageiroWhatsApp(CanalSaida canal, MensagemRepository mensagens) {
        this.canal = canal;
        this.mensagens = mensagens;
    }

    /** @throws FalhaEnvio se o canal recusar ou não responder */
    public void enviar(Colaborador colaborador, String texto) {
        String id = canal.enviarTexto(colaborador.numeroWhatsApp(), texto);
        if (id != null) {
            mensagens.registrarSaida(id, colaborador.id(), texto);
        }
    }
}
