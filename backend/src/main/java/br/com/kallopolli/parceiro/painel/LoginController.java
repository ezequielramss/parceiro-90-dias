package br.com.kallopolli.parceiro.painel;

import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e logout do dashboard. O token do Supabase vai só no cookie HttpOnly, SameSite=Strict;
 * o corpo da resposta traz apenas o perfil. Senha errada e e-mail inexistente recebem a mesma
 * resposta. Não há cadastro, recuperação de senha nem renovação de token: token vencido = 401.
 *
 * <p>O corpo do login é lido à mão para que nenhum erro de leitura (que o Spring registraria
 * com trechos do corpo) chegue ao log.
 */
@RestController
class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    private static final int MAX_EMAIL = 320;
    private static final int MAX_SENHA = 256;

    private final SupabaseAuth supabase;
    private final VerificadorToken verificador;
    private final AcessoRepository acessos;
    private final AuditoriaRepository auditoria;
    private final PainelProperties props;
    private final ObjectMapper json;

    LoginController(SupabaseAuth supabase, VerificadorToken verificador, AcessoRepository acessos,
                    AuditoriaRepository auditoria, PainelProperties props, ObjectMapper json) {
        this.supabase = supabase;
        this.verificador = verificador;
        this.acessos = acessos;
        this.auditoria = auditoria;
        this.props = props;
        this.json = json;
    }

    @PostMapping(path = "/api/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> entrar(@RequestBody(required = false) byte[] corpo) {
        Optional<String[]> credenciais = ler(corpo);
        if (credenciais.isEmpty()) {
            return recusado("FORMATO");
        }
        Optional<SupabaseAuth.Sessao> sessao;
        try {
            sessao = supabase.entrar(credenciais.get()[0], credenciais.get()[1]);
        } catch (SupabaseAuth.Indisponivel e) {
            registrar("AUTH_INDISPONIVEL");
            return RespostasPainel.erro(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_INDISPONIVEL",
                    RespostasPainel.AUTH_INDISPONIVEL);
        }
        if (sessao.isEmpty()) {
            return recusado("CREDENCIAIS");
        }

        Optional<UUID> usuario;
        try {
            usuario = verificador.usuario(sessao.get().accessToken());
        } catch (VerificadorToken.ChavesIndisponiveis e) {
            registrar("AUTH_INDISPONIVEL");
            return RespostasPainel.erro(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_INDISPONIVEL",
                    RespostasPainel.AUTH_INDISPONIVEL);
        }
        if (usuario.isEmpty()) {
            return recusado("TOKEN");
        }
        Optional<Acesso> acesso = acessos.buscarAcesso(usuario.get());
        if (acesso.isEmpty()) {
            registrar("SEM_ACESSO");
            return RespostasPainel.erro(HttpStatus.FORBIDDEN, "SEM_ACESSO", RespostasPainel.SEM_ACESSO);
        }

        auditoria.registrar("painel/" + usuario.get(), "PAINEL_LOGIN", "acessos_dashboard/" + usuario.get());
        registrar("OK");
        ResponseCookie cookie = cookie(sessao.get().accessToken(), Duration.ofSeconds(sessao.get().expiraEmSegundos()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ServicoPainel.perfil(acesso.get()));
    }

    @PostMapping("/api/logout")
    ResponseEntity<Void> sair() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie cookie(String valor, Duration validade) {
        return ResponseCookie.from(FiltroPainel.COOKIE, valor)
                .httpOnly(true)
                .secure(props.cookieSeguro())
                .sameSite("Strict")
                .path("/")
                .maxAge(validade)
                .build();
    }

    /** {email, senha}; vazio se faltar algo ou vier fora do formato. */
    private Optional<String[]> ler(byte[] corpo) {
        if (corpo == null || corpo.length == 0 || corpo.length > 4096) {
            return Optional.empty();
        }
        try {
            JsonNode no = json.readTree(corpo);
            String email = no.path("email").isTextual() ? no.path("email").asText().strip() : "";
            String senha = no.path("senha").isTextual() ? no.path("senha").asText() : "";
            if (email.isEmpty() || senha.isEmpty() || email.length() > MAX_EMAIL || senha.length() > MAX_SENHA) {
                return Optional.empty();
            }
            return Optional.of(new String[] {email, senha});
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ResponseEntity<Object> recusado(String motivo) {
        registrar("RECUSADO_" + motivo);
        return RespostasPainel.erro(HttpStatus.UNAUTHORIZED, "LOGIN_RECUSADO", RespostasPainel.LOGIN_RECUSADO);
    }

    private static void registrar(String resultado) {
        log.atInfo().setMessage("login no painel").addKeyValue("resultado", resultado).log();
    }
}
