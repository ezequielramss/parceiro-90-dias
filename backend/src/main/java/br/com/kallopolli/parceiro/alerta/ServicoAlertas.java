package br.com.kallopolli.parceiro.alerta;

import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.classificacao.DecisaoEncaminhamento;
import br.com.kallopolli.parceiro.classificacao.RoteadorRelato;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.Botao;
import br.com.kallopolli.parceiro.whatsapp.WhatsAppClient;
import br.com.kallopolli.parceiro.whatsapp.WhatsAppClientException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cria o alerta de um relato e envia aos responsáveis (H17, H19), com os botões
 * "Conversei" e "Não consegui". O gerente nunca recebe nome, motivo nem texto do relato; o RH
 * recebe o motivo, nunca o nome nem o texto. Tudo vai para a auditoria (H39), sem texto de relato.
 */
@Service
public class ServicoAlertas {

    private static final Logger log = LoggerFactory.getLogger(ServicoAlertas.class);
    private static final String SISTEMA = "sistema";

    private final AlertaRepository alertas;
    private final AlertaEntregaRepository entregas;
    private final ResponsavelRepository responsaveis;
    private final AuditoriaRepository auditoria;
    private final WhatsAppClient client;

    public ServicoAlertas(AlertaRepository alertas, AlertaEntregaRepository entregas,
                          ResponsavelRepository responsaveis, AuditoriaRepository auditoria, WhatsAppClient client) {
        this.alertas = alertas;
        this.entregas = entregas;
        this.responsaveis = responsaveis;
        this.auditoria = auditoria;
        this.client = client;
    }

    /** Devolve o id do alerta criado. */
    public long criarEEnviar(Colaborador colaborador, long relatoId, DecisaoEncaminhamento decisao,
                             Classificacao classificacao) {
        String destino = decisao.destino();
        long alertaId = alertas.criar(relatoId, destino, decisao.urgente());
        auditoria.registrar(SISTEMA, "ALERTA_CRIADO", "alerta/" + alertaId);

        List<Responsavel> gerentes = new ArrayList<>();
        List<Responsavel> rh = new ArrayList<>();
        if (RoteadorRelato.GERENTE.equals(destino) || RoteadorRelato.GERENTE_E_RH.equals(destino)) {
            gerentes.addAll(responsaveis.gerentesAtivosDaLoja(colaborador.loja()));
            if (gerentes.isEmpty()) {
                auditoria.registrar(SISTEMA, "ALERTA_LOJA_SEM_GERENTE", "alerta/" + alertaId);
                rh.addAll(responsaveis.rhAtivos());
            }
        }
        if ((RoteadorRelato.RH.equals(destino) || RoteadorRelato.GERENTE_E_RH.equals(destino)) && rh.isEmpty()) {
            rh.addAll(responsaveis.rhAtivos());
        }
        if (gerentes.isEmpty() && rh.isEmpty()) {
            auditoria.registrar(SISTEMA, "ALERTA_SEM_DESTINATARIO", "alerta/" + alertaId);
            log.atWarn().setMessage("alerta sem destinatário ativo").addKeyValue("alerta_id", alertaId).log();
        }

        String loja = colaborador.loja();
        String turno = colaborador.turno();
        String categoria = classificacao.categoria();
        for (Responsavel gerente : gerentes) {
            enviar(gerente, alertaId,
                    TextosConversa.alertaGerente(loja, turno, categoria, classificacao.acaoSugerida()));
        }
        String corpoRh;
        if (classificacao.falhaIa()) {
            corpoRh = TextosConversa.alertaRhSemClassificacao(loja, turno);
        } else if (decisao.urgente()) {
            corpoRh = TextosConversa.alertaRhUrgente(loja, turno, categoria, classificacao.motivo(),
                    classificacao.acaoSugerida());
        } else {
            corpoRh = TextosConversa.alertaRh(loja, turno, categoria, classificacao.motivo(),
                    classificacao.acaoSugerida());
        }
        for (Responsavel pessoaRh : rh) {
            enviar(pessoaRh, alertaId, corpoRh);
        }
        return alertaId;
    }

    private void enviar(Responsavel responsavel, long alertaId, String corpo) {
        long entregaId = entregas.criar(alertaId, responsavel.id());
        List<Botao> botoes = List.of(
                new Botao("alerta:" + entregaId + ":ok", TextosConversa.BOTAO_CONVERSEI),
                new Botao("alerta:" + entregaId + ":nao", TextosConversa.BOTAO_NAO_CONSEGUI));
        try {
            String wamid = client.enviarBotoes(responsavel.numeroWhatsApp(), corpo, botoes);
            entregas.registrarEnvio(entregaId, wamid);
            auditoria.registrar(SISTEMA, "ALERTA_ENVIADO", "entrega/" + entregaId);
        } catch (WhatsAppClientException e) {
            // Ex.: janela de 24 horas fechada para o responsável. Lembrete e escalonamento: Etapa 3.
            entregas.marcarFalha(entregaId);
            auditoria.registrar(SISTEMA, "ALERTA_FALHA_ENVIO", "entrega/" + entregaId);
            log.atWarn().setMessage("falha ao enviar alerta")
                    .addKeyValue("entrega_id", entregaId)
                    .addKeyValue("status_http", e.status())
                    .addKeyValue("codigo_meta", e.codigoMeta())
                    .log();
        }
    }
}
