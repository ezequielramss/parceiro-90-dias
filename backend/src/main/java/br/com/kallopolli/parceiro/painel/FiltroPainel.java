package br.com.kallopolli.parceiro.painel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Porta do /api: sem login, nenhum dado sai. A cada chamada confere o token do cookie
 * (assinatura, emissor, validade, audience) e busca o usuário em acessos_dashboard. Papel e loja
 * vêm só de lá; cabeçalho, parâmetro ou corpo enviados pelo navegador não mudam nada.
 *
 * <ul>
 *   <li>painel sem SUPABASE_URL ou chave: 503;
 *   <li>sem cookie, token inválido ou vencido: 401;
 *   <li>usuário sem linha em acessos_dashboard: 403.
 * </ul>
 * Login e logout passam sem sessão. Toda resposta do /api leva Cache-Control: no-store.
 */
@Component
class FiltroPainel extends OncePerRequestFilter {

    static final String COOKIE = "parceiro_sessao";
    static final String ATRIBUTO_ACESSO = "parceiro.painel.acesso";

    private final PainelProperties props;
    private final VerificadorToken verificador;
    private final AcessoRepository acessos;

    FiltroPainel(PainelProperties props, VerificadorToken verificador, AcessoRepository acessos) {
        this.props = props;
        this.verificador = verificador;
        this.acessos = acessos;
    }

    /** Caminho decodificado, sem parâmetros de caminho (;x) e sem barras duplas, em minúsculas. */
    static String caminho(HttpServletRequest pedido) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(pedido).toLowerCase(Locale.ROOT);
    }

    /** Na dúvida, filtra: tudo o que começa com /api passa pela porta, inclusive /api;x e /apix. */
    static boolean daApi(HttpServletRequest pedido) {
        return caminho(pedido).startsWith("/api");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !daApi(pedido);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        resposta.setHeader("Cache-Control", "no-store");
        if (!props.configurado()) {
            RespostasPainel.escrever(resposta, HttpStatus.SERVICE_UNAVAILABLE, "PAINEL_NAO_CONFIGURADO",
                    RespostasPainel.NAO_CONFIGURADO);
            return;
        }
        String c = caminho(pedido);
        if (c.equals("/api/login") || c.equals("/api/logout")) {
            cadeia.doFilter(pedido, resposta);
            return;
        }

        Optional<UUID> usuario;
        try {
            usuario = verificador.usuario(lerCookie(pedido));
        } catch (VerificadorToken.ChavesIndisponiveis e) {
            RespostasPainel.escrever(resposta, HttpStatus.SERVICE_UNAVAILABLE, "AUTH_INDISPONIVEL",
                    RespostasPainel.AUTH_INDISPONIVEL);
            return;
        }
        if (usuario.isEmpty()) {
            RespostasPainel.escrever(resposta, HttpStatus.UNAUTHORIZED, "SESSAO_INVALIDA",
                    RespostasPainel.SESSAO_EXPIRADA);
            return;
        }
        Optional<Acesso> acesso = acessos.buscarAcesso(usuario.get());
        if (acesso.isEmpty()) {
            RespostasPainel.escrever(resposta, HttpStatus.FORBIDDEN, "SEM_ACESSO", RespostasPainel.SEM_ACESSO);
            return;
        }
        pedido.setAttribute(ATRIBUTO_ACESSO, acesso.get());
        cadeia.doFilter(pedido, resposta);
    }

    static String lerCookie(HttpServletRequest pedido) {
        Cookie[] cookies = pedido.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
