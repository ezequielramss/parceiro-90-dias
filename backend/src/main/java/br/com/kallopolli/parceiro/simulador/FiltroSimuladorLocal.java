package br.com.kallopolli.parceiro.simulador;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * O simulador não tem login, então só atende a própria máquina: conexão de loopback, Host
 * localhost ou 127.0.0.1 e nenhum cabeçalho de proxy. Um túnel (cloudflared, ngrok) entra pelo
 * loopback, mas traz Forwarded, X-Forwarded-For ou CF-Connecting-IP, ou outro Host: tudo isso é
 * recusado com 403.
 */
@Component
@ConditionalOnProperty(name = "parceiro.whatsapp.canal", havingValue = "simulador")
class FiltroSimuladorLocal extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroSimuladorLocal.class);
    private static final List<String> CABECALHOS_DE_PROXY = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP", "CF-Connecting-IP");
    private static final List<String> HOSTS = List.of("localhost", "127.0.0.1");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !UrlPathHelper.defaultInstance.getPathWithinApplication(pedido).toLowerCase(Locale.ROOT)
                .startsWith("/simulador");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        resposta.setHeader("Cache-Control", "no-store");
        String motivo = motivoParaRecusar(pedido);
        if (motivo != null) {
            log.atWarn().setMessage("simulador recusou requisição").addKeyValue("motivo", motivo).log();
            resposta.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        cadeia.doFilter(pedido, resposta);
    }

    static String motivoParaRecusar(HttpServletRequest pedido) {
        if (!loopback(pedido.getRemoteAddr())) {
            return "CONEXAO_NAO_LOCAL";
        }
        for (String cabecalho : CABECALHOS_DE_PROXY) {
            if (pedido.getHeader(cabecalho) != null) {
                return "CABECALHO_DE_PROXY";
            }
        }
        // O servidor deriva getServerName() do próprio Host; o cabeçalho, quando vem, é conferido como chegou.
        String host = pedido.getHeader("Host") != null ? pedido.getHeader("Host") : pedido.getServerName();
        if (host == null || host.startsWith("[")) {
            return "HOST_NAO_LOCAL";
        }
        int porta = host.indexOf(':');
        String nome = (porta >= 0 ? host.substring(0, porta) : host).toLowerCase(Locale.ROOT);
        return HOSTS.contains(nome) ? null : "HOST_NAO_LOCAL";
    }

    private static boolean loopback(String endereco) {
        if (endereco == null || !endereco.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            // Só endereço literal (sem consulta de DNS).
            return InetAddress.getByName(endereco).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }
}
