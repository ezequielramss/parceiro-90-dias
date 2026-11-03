package br.com.kallopolli.parceiro.alerta;

import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import br.com.kallopolli.parceiro.whatsapp.TelefoneBrasil;
import br.com.kallopolli.parceiro.whatsapp.WhatsAppClient;
import br.com.kallopolli.parceiro.whatsapp.WhatsAppClientException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Toque do responsável nos botões do alerta (H17). Só vale do número dono da entrega.
 * "ok": CONVERSEI e alerta ATENDIDO (contato realizado, não resolvido). "nao": NAO_CONSEGUI e o
 * alerta continua ABERTO. "ok" vale depois de "nao"; "nao" depois de "ok" e toques repetidos
 * são ignorados sem erro.
 */
@Service
public class RespostaAlerta {

    private static final Logger log = LoggerFactory.getLogger(RespostaAlerta.class);
    private static final Pattern ID_BOTAO = Pattern.compile("alerta:(\\d+):(ok|nao)");

    private final AlertaEntregaRepository entregas;
    private final AlertaRepository alertas;
    private final ResponsavelRepository responsaveis;
    private final AuditoriaRepository auditoria;
    private final WhatsAppClient client;

    public RespostaAlerta(AlertaEntregaRepository entregas, AlertaRepository alertas,
                          ResponsavelRepository responsaveis, AuditoriaRepository auditoria, WhatsAppClient client) {
        this.entregas = entregas;
        this.alertas = alertas;
        this.responsaveis = responsaveis;
        this.auditoria = auditoria;
        this.client = client;
    }

    /** Devolve o resultado para o log de processamento. */
    public String tratar(MensagemRecebida toque) {
        Matcher m = ID_BOTAO.matcher(toque.botaoId());
        if (!m.matches()) {
            auditoria.registrar("desconhecido", "TOQUE_IGNORADO", "botao/invalido");
            return "TOQUE_INVALIDO";
        }
        long entregaId = Long.parseLong(m.group(1));
        boolean conversei = "ok".equals(m.group(2));

        Optional<AlertaEntregaRepository.Entrega> entrega = entregas.buscarEntrega(entregaId);
        Optional<Responsavel> dono = entrega.flatMap(e -> responsaveis.buscarResponsavel(e.responsavelId()));
        if (dono.isEmpty() || !TelefoneBrasil.mesmoNumero(dono.get().telefone(), toque.de())) {
            auditoria.registrar("desconhecido", "TOQUE_IGNORADO", "entrega/" + entregaId);
            return "TOQUE_DE_OUTRO_NUMERO";
        }
        String quem = "responsavel/" + dono.get().id();
        String respostaAtual = entrega.get().resposta();

        if (conversei) {
            if (AlertaEntregaRepository.CONVERSEI.equals(respostaAtual)) {
                return "TOQUE_REPETIDO";
            }
            entregas.responder(entregaId, AlertaEntregaRepository.CONVERSEI);
            alertas.marcarAtendido(entrega.get().alertaId());
            auditoria.registrar(quem, "TOQUE_CONVERSEI", "entrega/" + entregaId);
            responder(dono.get(), TextosConversa.respostaConversei());
            return "CONVERSEI";
        }
        if (respostaAtual != null) {
            return "TOQUE_REPETIDO";
        }
        entregas.responder(entregaId, AlertaEntregaRepository.NAO_CONSEGUI);
        auditoria.registrar(quem, "TOQUE_NAO_CONSEGUI", "entrega/" + entregaId);
        responder(dono.get(), TextosConversa.respostaNaoConsegui());
        return "NAO_CONSEGUI";
    }

    private void responder(Responsavel responsavel, String texto) {
        try {
            client.enviarTexto(responsavel.numeroWhatsApp(), texto);
        } catch (WhatsAppClientException e) {
            log.atWarn().setMessage("falha ao responder ao responsável")
                    .addKeyValue("status_http", e.status())
                    .addKeyValue("codigo_meta", e.codigoMeta())
                    .log();
        }
    }
}
