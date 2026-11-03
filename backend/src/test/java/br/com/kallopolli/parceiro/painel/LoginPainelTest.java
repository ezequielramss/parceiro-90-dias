package br.com.kallopolli.parceiro.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** Login e logout pelo Supabase Auth falso. E-mails e senhas fictícios. */
class LoginPainelTest extends PainelTestBase {

    private static final String EMAIL_RH = "rh.ficticio@exemplo.invalid";
    private static final String SENHA_RH = "SENHA-FICTICIA-RH-1";
    private static final String EMAIL_SEM_ACESSO = "sem.acesso@exemplo.invalid";
    private static final String SENHA_SEM_ACESSO = "SENHA-FICTICIA-2";

    @BeforeEach
    void usuarios() {
        acessos();
        SUPABASE.cadastrar(RH, EMAIL_RH, SENHA_RH);
        SUPABASE.cadastrar(SEM_ACESSO, EMAIL_SEM_ACESSO, SENHA_SEM_ACESSO);
    }

    private MockHttpServletResponse login(String corpo) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse();
    }

    private static String credenciais(String email, String senha) {
        return "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha);
    }

    @Test
    void loginCertoGuardaOTokenSoNoCookieEDevolveOPerfil() throws Exception {
        MockHttpServletResponse resposta = login(credenciais(EMAIL_RH, SENHA_RH));

        assertThat(resposta.getStatus()).isEqualTo(200);
        String cookie = resposta.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(FiltroPainel.COOKIE + "=ey")
                .contains("HttpOnly", "SameSite=Strict", "Secure", "Path=/", "Max-Age=3600");
        assertThat(JSON.readTree(resposta.getContentAsString()))
                .isEqualTo(JSON.readTree("{\"perfil\":\"RH\",\"nome\":\"RH Ficticio\",\"loja\":null}"));
        String token = cookie.substring((FiltroPainel.COOKIE + "=").length(), cookie.indexOf(';'));
        assertThat(resposta.getContentAsString()).doesNotContain(token, "access_token", "refresh");
        assertThat(SUPABASE.ultimaChaveRecebida()).isEqualTo(SupabaseAuthFalso.CHAVE_PUBLICAVEL_FALSA);

        // O cookie recebido abre o painel.
        mvc.perform(get("/api/perfil").cookie(sessao(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("RH"));
    }

    @Test
    void senhaErradaEEmailInexistenteTemAMesmaResposta() throws Exception {
        MockHttpServletResponse senhaErrada = login(credenciais(EMAIL_RH, "senha-errada"));
        MockHttpServletResponse emailInexistente = login(credenciais("nao.existe@exemplo.invalid", SENHA_RH));

        for (MockHttpServletResponse r : new MockHttpServletResponse[] {senhaErrada, emailInexistente}) {
            assertThat(r.getStatus()).isEqualTo(401);
            assertThat(r.getHeader(HttpHeaders.SET_COOKIE)).isNull();
            assertThat(JSON.readTree(r.getContentAsString()).at("/erro/mensagem").asText())
                    .isEqualTo("E-mail ou senha incorretos.");
        }
        assertThat(senhaErrada.getContentAsString()).isEqualTo(emailInexistente.getContentAsString());
    }

    @Test
    void corpoForaDoFormatoRecebeAMesmaRespostaGenerica() throws Exception {
        for (String corpo : new String[] {"{}", "{\"email\":\"" + EMAIL_RH + "\"}", "nao-e-json",
                "{\"email\":1,\"senha\":2}", credenciais(EMAIL_RH, "x".repeat(300))}) {
            MockHttpServletResponse r = login(corpo);
            assertThat(r.getStatus()).as(corpo).isEqualTo(401);
            assertThat(r.getContentAsString()).contains("E-mail ou senha incorretos.");
        }
    }

    @Test
    void usuarioSemLinhaEmAcessosNaoRecebeCookie() throws Exception {
        MockHttpServletResponse r = login(credenciais(EMAIL_SEM_ACESSO, SENHA_SEM_ACESSO));

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void supabaseForaDoArViraServicoIndisponivel() throws Exception {
        SUPABASE.responderToken(500);

        MockHttpServletResponse r = login(credenciais(EMAIL_RH, SENHA_RH));

        assertThat(r.getStatus()).isEqualTo(503);
        assertThat(r.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void logoutApagaOCookie() throws Exception {
        mvc.perform(post("/api/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith(FiltroPainel.COOKIE + "=;"),
                                org.hamcrest.Matchers.containsString("Max-Age=0"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void naoHaCadastroRecuperacaoNemRenovacao() throws Exception {
        int antes = SUPABASE.pedidosToken();
        for (String caminho : new String[] {"/api/cadastro", "/api/signup", "/api/recuperar-senha", "/api/renovar",
                "/api/token"}) {
            int status = mvc.perform(post(caminho).cookie(sessao(RH)).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andReturn().getResponse().getStatus();
            assertThat(status).as(caminho).isIn(404, 405);
        }
        assertThat(SUPABASE.pedidosToken()).isEqualTo(antes);
    }
}
