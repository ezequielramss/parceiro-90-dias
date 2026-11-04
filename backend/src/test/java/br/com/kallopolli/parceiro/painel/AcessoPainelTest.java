package br.com.kallopolli.parceiro.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Sem login nenhum dado sai; token inválido = 401; sem linha em acessos_dashboard = 403. */
class AcessoPainelTest extends PainelTestBase {

    static final List<String> ROTAS = List.of("/api/perfil", "/api/kpis", "/api/kpis/serie-mensal",
            "/api/colaboradores", "/api/colaboradores/00000000-0000-0000-0000-000000000001/linha-do-tempo",
            "/api/alertas", "/api/alertas?status=ABERTO&destino=GERENTE", "/api/relatos", "/api/bem-estar");

    @BeforeEach
    void dados() {
        cenarioDuasLojas();
    }

    private int codigo(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido).andReturn().getResponse().getStatus();
    }

    @Test
    void semCookieTodaRotaResponde401SemDados() throws Exception {
        for (String rota : ROTAS) {
            MockHttpServletResponse r = mvc.perform(get(rota)).andReturn().getResponse();
            assertThat(r.getStatus()).as(rota).isEqualTo(401);
            assertThat(r.getContentAsString()).as(rota)
                    .isEqualTo("{\"erro\":{\"codigo\":\"SESSAO_INVALIDA\",\"mensagem\":\"Sua sessão expirou. Entre novamente para continuar.\"}}");
        }
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        List<String> tokens = List.of(
                SUPABASE.tokenComAssinaturaErrada(RH),
                SUPABASE.tokenExpirado(RH),
                SUPABASE.tokenDeOutroEmissor(RH),
                SUPABASE.tokenComOutraAudience(RH),
                "nao.e.jwt",
                "",
                // alg "none": sem assinatura.
                "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0." + SUPABASE.token(RH).split("\\.")[1] + ".");
        for (String token : tokens) {
            for (String rota : ROTAS) {
                assertThat(codigo(get(rota).cookie(sessao(token)))).as(rota + " " + token).isEqualTo(401);
            }
        }
    }

    @Test
    void tokenNoCabecalhoAuthorizationNaoVale() throws Exception {
        assertThat(codigo(get("/api/perfil").header("Authorization", "Bearer " + SUPABASE.token(RH)))).isEqualTo(401);
    }

    @Test
    void usuarioSemLinhaEmAcessosResponde403() throws Exception {
        for (String rota : ROTAS) {
            MockHttpServletResponse r = mvc.perform(como(SEM_ACESSO, rota)).andReturn().getResponse();
            assertThat(r.getStatus()).as(rota).isEqualTo(403);
            assertThat(r.getContentAsString()).as(rota).contains("SEM_ACESSO").doesNotContain("Pessoa Ficticia");
        }
    }

    @Test
    void caminhoDisfarcadoTambemPassaPeloFiltro() throws Exception {
        for (String rota : List.of("/api;x=1/kpis", "/API/kpis", "/api/kpis/", "/api//kpis", "/%61pi/kpis")) {
            MockHttpServletResponse r = mvc.perform(get(rota)).andReturn().getResponse();
            assertThat(r.getStatus()).as(rota).isNotEqualTo(200);
            assertThat(r.getContentAsString()).as(rota).doesNotContain("emJornada");
        }
    }

    @Test
    void nenhumaRotaDeEscritaAlemDeLoginELogout() throws Exception {
        for (String rota : ROTAS) {
            String caminho = rota.split("\\?")[0];
            for (MockHttpServletRequestBuilder pedido : List.of(post(caminho), put(caminho), patch(caminho),
                    delete(caminho))) {
                int s = codigo(pedido.cookie(sessao(RH)).contentType(MediaType.APPLICATION_JSON).content("{}"));
                assertThat(s).as(caminho).isIn(404, 405);
            }
        }
        assertThat(codigo(patch("/api/relatos/RL-2026-0001").cookie(sessao(RH))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONCLUIDO\"}"))).isIn(404, 405);
        assertThat(codigo(post("/api/relatorios/gerar").cookie(sessao(RH))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).isIn(404, 405);
    }

    @Test
    void cabecalhosDeSegurancaNoApiENosArquivos() throws Exception {
        for (MockHttpServletRequestBuilder pedido : List.of(como(RH, "/api/kpis"), get("/api/kpis"), get("/"),
                get("/index.html"), get("/js/app.js"))) {
            mvc.perform(pedido)
                    .andExpect(header().string("Content-Security-Policy",
                            org.hamcrest.Matchers.startsWith("default-src 'self'")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }
        assertThat(CabecalhosSeguranca.CSP).doesNotContain("unsafe-inline", "unsafe-eval");
        mvc.perform(como(RH, "/api/kpis")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/kpis")).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void dashboardEstaticoAbreSemLoginESemDados() throws Exception {
        MockHttpServletResponse index = mvc.perform(get("/index.html")).andReturn().getResponse();
        assertThat(index.getStatus()).isEqualTo(200);
        assertThat(index.getContentAsString()).contains("<div id=\"app\"").doesNotContain("<script>");
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
        mvc.perform(get("/mock/kpis.json")).andExpect(status().isOk());
    }
}
