package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.alerta.RespostaAlerta;
import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.alerta.ResponsavelRepository;
import br.com.kallopolli.parceiro.consentimento.FluxoConsentimento;
import br.com.kallopolli.parceiro.conversa.OrquestradorConversa;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processa cada mensagem fora da thread do webhook.
 *
 * <ol>
 *   <li>Toque em botão "alerta:..." é sempre resposta de responsável.
 *   <li>Número de colaborador ativo (com ou sem o nono dígito): grava a mensagem (wamid único
 *       descarta repetições), aplica o aceite ("parar" tem prioridade sobre tudo) e, com aceite
 *       ativo, segue a conversa.
 *   <li>Número que só é responsável recebe o texto curto de responsável.
 *   <li>Número desconhecido é ignorado.
 * </ol>
 * Falhas vão para o log só com wamid e o tipo do erro.
 */
@Service
public class ProcessadorMensagens {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorMensagens.class);

    private final ColaboradorRepository colaboradores;
    private final MensagemRepository mensagens;
    private final FluxoConsentimento fluxoConsentimento;
    private final OrquestradorConversa orquestrador;
    private final RespostaAlerta respostaAlerta;
    private final ResponsavelRepository responsaveis;
    private final WamidsRecentes wamidsRecentes;
    private final WhatsAppClient client;

    public ProcessadorMensagens(
            ColaboradorRepository colaboradores,
            MensagemRepository mensagens,
            FluxoConsentimento fluxoConsentimento,
            OrquestradorConversa orquestrador,
            RespostaAlerta respostaAlerta,
            ResponsavelRepository responsaveis,
            WamidsRecentes wamidsRecentes,
            WhatsAppClient client) {
        this.colaboradores = colaboradores;
        this.mensagens = mensagens;
        this.fluxoConsentimento = fluxoConsentimento;
        this.orquestrador = orquestrador;
        this.respostaAlerta = respostaAlerta;
        this.responsaveis = responsaveis;
        this.wamidsRecentes = wamidsRecentes;
        this.client = client;
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
        if (mensagem.tocouBotao() && mensagem.botaoId().startsWith("alerta:")) {
            return wamidsRecentes.novo(mensagem.wamid()) ? respostaAlerta.tratar(mensagem) : "REPETIDA_IGNORADA";
        }

        List<String> candidatos = TelefoneBrasil.candidatosE164(mensagem.de());
        Optional<Colaborador> colaborador = candidatos.stream()
                .map(colaboradores::buscarAtivoPorTelefone)
                .flatMap(Optional::stream)
                .findFirst();
        if (colaborador.isPresent()) {
            return colaborador(colaborador.get(), mensagem);
        }

        List<Responsavel> comoResponsavel = responsaveis.ativosPorTelefones(candidatos);
        if (!comoResponsavel.isEmpty()) {
            if (!wamidsRecentes.novo(mensagem.wamid())) {
                return "REPETIDA_IGNORADA";
            }
            // Responder abre a janela de 24 horas do responsável para os próximos alertas.
            client.enviarTexto(comoResponsavel.get(0).numeroWhatsApp(), TextosConversa.responsavelForaDeAlerta());
            return "RESPONSAVEL_FORA_DE_ALERTA";
        }
        return "REMETENTE_NAO_CADASTRADO";
    }

    private String colaborador(Colaborador colaborador, MensagemRecebida mensagem) {
        Optional<Long> mensagemId = mensagens.registrarEntrada(
                mensagem.wamid(), colaborador.id(), mensagem.tipo(), mensagem.conteudo());
        if (mensagemId.isEmpty()) {
            return "REPETIDA_IGNORADA";
        }
        FluxoConsentimento.Resultado aceite = fluxoConsentimento.tratar(colaborador, mensagem.conteudo());
        return switch (aceite) {
            case RETIRADA -> {
                orquestrador.aoParar(colaborador);
                yield "RETIRADA";
            }
            case REGISTRADA -> orquestrador.tratar(colaborador, mensagem, mensagemId.get());
            default -> aceite.name();
        };
    }

    private void registrar(MensagemRecebida mensagem, String resultado) {
        log.atInfo().setMessage("mensagem processada")
                .addKeyValue("wamid", mensagem.wamid())
                .addKeyValue("resultado", resultado)
                .log();
    }
}
