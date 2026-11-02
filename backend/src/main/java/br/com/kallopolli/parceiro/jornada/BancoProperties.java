package br.com.kallopolli.parceiro.jornada;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Conexão com o Postgres, lida só do ambiente. Existe para a aplicação recusar a
 * subida quando faltar DATABASE_URL, DATABASE_USER ou DATABASE_PASSWORD, em vez de
 * falhar só na primeira consulta.
 */
@Validated
@ConfigurationProperties("parceiro.banco")
public record BancoProperties(
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String url,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String usuario,
        @NotBlank @Pattern(regexp = DEFINIDA, message = NAO_DEFINIDA) String senha) {

    static final String DEFINIDA = "^(?!\\$\\{).*";
    static final String NAO_DEFINIDA = "variável de ambiente não definida";

    @Override
    public String toString() {
        return "BancoProperties[url=" + url + ", usuario=" + usuario + ", senha=***]";
    }
}
