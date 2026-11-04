package br.com.kallopolli.parceiro.alerta;

import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.whatsapp.TelefoneBrasil;
import br.com.kallopolli.parceiro.whatsapp.CanalSaida;
import br.com.kallopolli.parceiro.whatsapp.FalhaEnvio;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resposta do responsável ao alerta (H17): CONVERSEI ou NÃO CONSEGUI digitados, já convertidos na
 * ação interna alerta:&lt;entregaId&gt;:ok|nao. Só vale do número dono da entrega.
 * "ok": CONVERSEI e alerta ATENDIDO (contato realizado, não resolvido). "nao": NAO_CONSEGUI e o
 * alerta continua ABERTO. "ok" vale depois de "nao"; "nao" depois de "ok" e respostas repetidas
 * são ignoradas sem erro. As ações na auditoria mantêm os nomes de antes (TOQUE_*).
 */
@Service
public class RespostaAlerta {

    private static final Logger log = LoggerFactory.getLogger(RespostaAlerta.class);
    private static final Pattern ACAO = Pattern.compile("alerta:(\\d+):(ok|nao)");

    private final AlertaEntregaRepository entregas;
    private final AlertaRepository alertas;
    private final ResponsavelRepository responsaveis;
    private final AuditoriaRepository auditoria;
    private final CanalSaida canal;

    public RespostaAlerta(AlertaEntregaRepository entregas, AlertaRepository alertas,
                          ResponsavelRepository responsaveis, AuditoriaRepository auditoria, CanalSaida canal) {
        this.entregas = entregas;
        this.alertas = alertas;
        this.responsaveis = responsaveis;
        this.auditoria = auditoria;
        this.canal = canal;
    }

    /**
     * @param acao alerta:&lt;entregaId&gt;:ok|nao
     * @param de   número de quem respondeu, só dígitos
     * @return o resultado para o log de processamento
     */
    public String tratar(String acao, String de) {
        Matcher m = ACAO.matcher(acao);
        if (!m.matches()) {
            auditoria.registrar("desconhecido", "TOQUE_IGNORADO", "acao/invalida");
            return "TOQUE_INVALIDO";
        }
        long entregaId = Long.parseLong(m.group(1));
        boolean conversei = "ok".equals(m.group(2));

        Optional<AlertaEntregaRepository.Entrega> entrega = entregas.buscarEntrega(entregaId);
        Optional<Responsavel> dono = entrega.flatMap(e -> responsaveis.buscarResponsavel(e.responsavelId()));
        if (dono.isEmpty() || !TelefoneBrasil.mesmoNumero(dono.get().telefone(), de)) {
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
            canal.enviarTexto(responsavel.numeroWhatsApp(), texto);
        } catch (FalhaEnvio e) {
            log.atWarn().setMessage("falha ao responder ao responsável")
                    .addKeyValue("status_http", e.status())
                    .log();
        }
    }
}
