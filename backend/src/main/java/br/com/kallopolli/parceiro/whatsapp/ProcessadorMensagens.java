package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.alerta.AlertaEntregaRepository;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processa cada mensagem fora da thread que a recebeu (webhook da Evolution ou simulador).
 *
 * <ol>
 *   <li>CONVERSEI ou NÃO CONSEGUI de um número de responsável com alerta aberto: resposta ao
 *       alerta mais recente ainda aberto daquele número. Sem alerta aberto, o texto segue o fluxo.
 *   <li>Número de colaborador ativo (com ou sem o nono dígito): grava a mensagem (id único
 *       descarta repetições), aplica o aceite ("parar" tem prioridade sobre tudo) e, com aceite
 *       ativo, segue a conversa.
 *   <li>Número que só é responsável recebe o texto curto de responsável.
 *   <li>Número desconhecido é ignorado.
 * </ol>
 * O mesmo número pode ser colaborador, gerente e RH. Falhas vão para o log só com o id da
 * mensagem e o tipo do erro.
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
    private final AlertaEntregaRepository entregas;
    private final WamidsRecentes wamidsRecentes;
    private final CanalSaida canal;

    public ProcessadorMensagens(
            ColaboradorRepository colaboradores,
            MensagemRepository mensagens,
            FluxoConsentimento fluxoConsentimento,
            OrquestradorConversa orquestrador,
            RespostaAlerta respostaAlerta,
            ResponsavelRepository responsaveis,
            AlertaEntregaRepository entregas,
            WamidsRecentes wamidsRecentes,
            CanalSaida canal) {
        this.colaboradores = colaboradores;
        this.mensagens = mensagens;
        this.fluxoConsentimento = fluxoConsentimento;
        this.orquestrador = orquestrador;
        this.respostaAlerta = respostaAlerta;
        this.responsaveis = responsaveis;
        this.entregas = entregas;
        this.wamidsRecentes = wamidsRecentes;
        this.canal = canal;
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
        List<String> candidatos = TelefoneBrasil.candidatosE164(mensagem.de());
        List<Responsavel> comoResponsavel = responsaveis.ativosPorTelefones(candidatos);

        Optional<String> respostaAoAlerta = respostaAoAlerta(mensagem, comoResponsavel);
        if (respostaAoAlerta.isPresent()) {
            return respostaAoAlerta.get();
        }

        Optional<Colaborador> colaborador = candidatos.stream()
                .map(colaboradores::buscarAtivoPorTelefone)
                .flatMap(Optional::stream)
                .findFirst();
        if (colaborador.isPresent()) {
            return colaborador(colaborador.get(), mensagem);
        }

        if (!comoResponsavel.isEmpty()) {
            if (!wamidsRecentes.novo(mensagem.wamid())) {
                return "REPETIDA_IGNORADA";
            }
            canal.enviarTexto(comoResponsavel.get(0).numeroWhatsApp(), TextosConversa.responsavelForaDeAlerta());
            return "RESPONSAVEL_FORA_DE_ALERTA";
        }
        return "REMETENTE_NAO_CADASTRADO";
    }

    /** CONVERSEI ou NÃO CONSEGUI vale para a entrega mais recente ainda aberta daquele número. */
    private Optional<String> respostaAoAlerta(MensagemRecebida mensagem, List<Responsavel> comoResponsavel) {
        if (comoResponsavel.isEmpty() || !MensagemRecebida.TEXTO.equals(mensagem.tipo())) {
            return Optional.empty();
        }
        Optional<RespostaPorTexto.Alerta> resposta = RespostaPorTexto.alerta(mensagem.conteudo());
        if (resposta.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> ids = comoResponsavel.stream().map(Responsavel::id).toList();
        Optional<Long> entrega = entregas.maisRecenteAberta(ids);
        if (entrega.isEmpty()) {
            return Optional.empty();
        }
        if (!wamidsRecentes.novo(mensagem.wamid())) {
            return Optional.of("REPETIDA_IGNORADA");
        }
        return Optional.of(respostaAlerta.tratar(
                RespostaPorTexto.acaoAlerta(entrega.get(), resposta.get()), mensagem.de()));
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
