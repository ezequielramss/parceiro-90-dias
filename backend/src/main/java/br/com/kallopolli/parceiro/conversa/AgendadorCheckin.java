package br.com.kallopolli.parceiro.conversa;

import br.com.kallopolli.parceiro.consentimento.EnvioAcompanhamento;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.whatsapp.FalhaEnvio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Check-in dos dias 1 a 10 (H04), sem horário (decisão do PO): a cada minuto, envia o check-in
 * do dia a quem está ativo, tem aceite ativo e ainda não recebeu o do dia.
 *
 * <p>dia_jornada = (hoje - data_inicio) + 1, no fuso America/Sao_Paulo. Insere a linha em checkins
 * antes de enviar (unicidade colaborador + dia): o mesmo dia nunca sai duas vezes, e um envio que
 * falha (canal fora do ar ou número recusado) não é repetido. Dia perdido não é
 * recuperado. Quem está confirmando um relato recebe o check-in depois, no mesmo dia.
 *
 * <p>Liga com parceiro.checkin.ativo=true.
 */
@Component
@ConditionalOnProperty(name = "parceiro.checkin.ativo", havingValue = "true")
public class AgendadorCheckin {

    private static final Logger log = LoggerFactory.getLogger(AgendadorCheckin.class);
    private static final int ULTIMO_DIA = 10;

    private final ColaboradorRepository colaboradores;
    private final CheckinRepository checkins;
    private final ConversaEstadoRepository estados;
    private final EnvioAcompanhamento envio;
    private final AuditoriaRepository auditoria;
    private final Clock relogio;

    public AgendadorCheckin(ColaboradorRepository colaboradores, CheckinRepository checkins,
                            ConversaEstadoRepository estados, EnvioAcompanhamento envio,
                            AuditoriaRepository auditoria, Clock relogio) {
        this.colaboradores = colaboradores;
        this.checkins = checkins;
        this.estados = estados;
        this.envio = envio;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void tick() {
        LocalDate hoje = LocalDate.now(relogio);
        for (Colaborador colaborador : colaboradores.ativosComAceiteIniciadosEntre(hoje.minusDays(ULTIMO_DIA - 1), hoje)) {
            try {
                enviarCheckin(colaborador, hoje);
            } catch (Exception e) {
                log.atError().setMessage("falha no check-in")
                        .addKeyValue("colaborador_id", colaborador.id())
                        .addKeyValue("erro", e.getClass().getSimpleName())
                        .log();
            }
        }
    }

    private void enviarCheckin(Colaborador colaborador, LocalDate hoje) {
        int dia = (int) ChronoUnit.DAYS.between(colaborador.dataInicio(), hoje) + 1;
        if (dia < 1 || dia > ULTIMO_DIA) {
            return;
        }
        if (estados.estadoDe(colaborador.id()).tipo() == EstadoConversa.Tipo.AGUARDANDO_CONFIRMACAO_RELATO) {
            return;
        }
        Optional<Long> checkinId = checkins.criarSeNovo(colaborador.id(), dia);
        if (checkinId.isEmpty()) {
            return;
        }
        String quem = "colaborador/" + colaborador.id();
        String objeto = "checkin/" + checkinId.get();
        try {
            if (!envio.enviar(colaborador, TextosConversa.checkin(dia, colaborador.primeiroNome()))) {
                auditoria.registrar(quem, "CHECKIN_SEM_ACEITE", objeto);
                return;
            }
        } catch (FalhaEnvio e) {
            auditoria.registrar(quem, "CHECKIN_FALHA_ENVIO", objeto);
            log.atWarn().setMessage("check-in não enviado")
                    .addKeyValue("checkin_id", checkinId.get())
                    .addKeyValue("status_http", e.status())
                    .log();
            return;
        }
        estados.definir(colaborador.id(), EstadoConversa.aguardandoHumor(checkinId.get()));
        auditoria.registrar(quem, "CHECKIN_ENVIADO", objeto);
    }
}
