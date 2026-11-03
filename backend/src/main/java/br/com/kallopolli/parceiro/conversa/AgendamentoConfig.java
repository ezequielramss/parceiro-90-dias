package br.com.kallopolli.parceiro.conversa;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Agendamento e relógio no fuso da operação. O código usa sempre este Clock, nunca now() direto. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AgendamentoConfig {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock relogio() {
        return Clock.system(FUSO);
    }
}
