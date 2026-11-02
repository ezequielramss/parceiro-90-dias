package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.consentimento.FluxoConsentimento;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processa cada mensagem fora da thread do webhook.
 *
 * <p>Ordem: identifica o colaborador pelo telefone, grava a mensagem (o wamid único
 * descarta repetições) e só então aplica o fluxo de aceite. Uma mensagem repetida
 * não gera registro nem resposta. Falhas vão para o log só com wamid e o tipo do erro.
 */
@Service
public class ProcessadorMensagens {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorMensagens.class);

    private final ColaboradorRepository colaboradores;
    private final MensagemRepository mensagens;
    private final FluxoConsentimento fluxoConsentimento;

    public ProcessadorMensagens(
            ColaboradorRepository colaboradores,
            MensagemRepository mensagens,
            FluxoConsentimento fluxoConsentimento) {
        this.colaboradores = colaboradores;
        this.mensagens = mensagens;
        this.fluxoConsentimento = fluxoConsentimento;
    }

    @Async
    public void processar(MensagemRecebida mensagem) {
        try {
            registrar(mensagem, executar(mensagem));
        } catch (Exception e) {
            // Nunca logar a exceção inteira: mensagens de erro podem carregar dados.
            log.atError().setMessage("falha ao processar mensagem")
                    .addKeyValue("wamid", mensagem.wamid())
                    .addKeyValue("erro", e.getClass().getSimpleName())
                    .log();
        }
    }

    private String executar(MensagemRecebida mensagem) {
        Optional<Colaborador> colaborador = colaboradores.buscarAtivoPorTelefone(mensagem.telefoneE164());
        if (colaborador.isEmpty()) {
            return "REMETENTE_NAO_CADASTRADO";
        }
        boolean nova = mensagens.registrarEntrada(
                mensagem.wamid(), colaborador.get().id(), mensagem.tipo(), mensagem.texto());
        if (!nova) {
            return "REPETIDA_IGNORADA";
        }
        return fluxoConsentimento.tratar(colaborador.get(), mensagem.texto()).name();
    }

    private void registrar(MensagemRecebida mensagem, String resultado) {
        log.atInfo().setMessage("mensagem processada")
                .addKeyValue("wamid", mensagem.wamid())
                .addKeyValue("resultado", resultado)
                .log();
    }
}
