package br.com.kallopolli.parceiro.consentimento;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ConsentimentoRepository;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Única porta para mensagens de acompanhamento (check-ins, lembretes). Só envia a
 * quem tem aceite ativo. O agendador da próxima etapa deve passar sempre por aqui.
 */
@Service
public class EnvioAcompanhamento {

    private static final Logger log = LoggerFactory.getLogger(EnvioAcompanhamento.class);

    private final ConsentimentoRepository consentimentos;
    private final MensageiroWhatsApp mensageiro;

    public EnvioAcompanhamento(ConsentimentoRepository consentimentos, MensageiroWhatsApp mensageiro) {
        this.consentimentos = consentimentos;
        this.mensageiro = mensageiro;
    }

    /** Devolve false, sem enviar, se o colaborador não tem aceite ativo. */
    public boolean enviar(Colaborador colaborador, String texto) {
        if (!consentimentos.temAtivo(colaborador.id())) {
            log.atInfo().setMessage("acompanhamento bloqueado: sem aceite")
                    .addKeyValue("colaborador_id", colaborador.id())
                    .log();
            return false;
        }
        mensageiro.enviar(colaborador, texto);
        return true;
    }
}
