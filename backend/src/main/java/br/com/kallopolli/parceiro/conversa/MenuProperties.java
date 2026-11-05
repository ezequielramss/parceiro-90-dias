package br.com.kallopolli.parceiro.conversa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Menu do colaborador. Acima de {@code textoLongoCaracteres}, um texto solto recebe o aviso de que
 * pode ser registrado como relato, em vez de só o menu. O mínimo de 20 garante que uma resposta
 * curta nunca vire aviso; valor fora da faixa impede a subida.
 */
@Validated
@ConfigurationProperties("parceiro.menu")
public record MenuProperties(@DefaultValue("120") @Min(20) @Max(4096) int textoLongoCaracteres) {}
