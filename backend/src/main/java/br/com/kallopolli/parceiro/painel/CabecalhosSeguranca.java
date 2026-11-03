package br.com.kallopolli.parceiro.painel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança em toda resposta: só scripts, estilos e dados da própria origem (sem
 * script inline), sem adivinhar tipo de arquivo, sem Referer e sem ser aberto dentro de outro site.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CabecalhosSeguranca extends OncePerRequestFilter {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        resposta.setHeader("Content-Security-Policy", CSP);
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader("Referrer-Policy", "no-referrer");
        resposta.setHeader("X-Frame-Options", "DENY");
        cadeia.doFilter(pedido, resposta);
    }
}
