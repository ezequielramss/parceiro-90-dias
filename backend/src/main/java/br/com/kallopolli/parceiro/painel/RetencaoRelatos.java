package br.com.kallopolli.parceiro.painel;

import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retenção prometida no termo de participação: relatos ficam guardados por até 90 dias. Todo dia,
 * às 3h de São Paulo, apaga o texto e o motivo dos relatos criados há mais de 90 dias (o prazo
 * conta da data do relato) e o texto das mensagens do mesmo período. Categoria, destino, datas e
 * contagens ficam para os indicadores. A auditoria e o log registram só as quantidades.
 *
 * <p>Liga com parceiro.retencao.ativo=true (padrão).
 */
@Component
@ConditionalOnProperty(name = "parceiro.retencao.ativo", havingValue = "true", matchIfMissing = true)
public class RetencaoRelatos {

    private static final Logger log = LoggerFactory.getLogger(RetencaoRelatos.class);
    static final Duration PRAZO = Duration.ofDays(90);

    private final RetencaoRepository repositorio;
    private final AuditoriaRepository auditoria;
    private final Clock relogio;

    public RetencaoRelatos(RetencaoRepository repositorio, AuditoriaRepository auditoria, Clock relogio) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void diaria() {
        try {
            executar();
        } catch (Exception e) {
            log.atWarn().setMessage("falha na retenção dos relatos")
                    .addKeyValue("erro", e.getClass().getSimpleName())
                    .log();
        }
    }

    public RetencaoRepository.Apagados executar() {
        Instant limite = relogio.instant().minus(PRAZO);
        RetencaoRepository.Apagados apagados = repositorio.apagarTextosAntesDe(limite);
        if (apagados.relatos() > 0 || apagados.mensagens() > 0) {
            auditoria.registrar("sistema", "RETENCAO_90_DIAS",
                    "relatos=" + apagados.relatos() + ";mensagens=" + apagados.mensagens());
        }
        log.atInfo().setMessage("retenção dos relatos")
                .addKeyValue("relatos", apagados.relatos())
                .addKeyValue("mensagens", apagados.mensagens())
                .log();
        return apagados;
    }
}
