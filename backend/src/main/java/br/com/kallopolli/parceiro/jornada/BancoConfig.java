package br.com.kallopolli.parceiro.jornada;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DataSource montado a partir de {@link BancoProperties}, para que a falta de uma
 * variável apareça como "parceiro.banco.* não definida" antes de qualquer conexão.
 */
@Configuration(proxyBeanMethods = false)
class BancoConfig {

    @Bean
    DataSource dataSource(BancoProperties banco) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(banco.url())
                .username(banco.usuario())
                .password(banco.senha())
                .build();
        ds.setMaximumPoolSize(5);
        ds.setPoolName("parceiro");
        return ds;
    }
}
