package br.com.kallopolli.parceiro.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Quem vê o quê, com dados fictícios de duas lojas. */
class VisibilidadePainelTest extends PainelTestBase {

    private static final Set<String> RESTRITAS =
            Set.of("CONFLITO_CHEFIA", "NAO_CLASSIFICADO", "ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE");

    @BeforeEach
    void dados() {
        cenarioDuasLojas();
    }

    private List<String> rotasDoGerente() {
        return List.of("/api/perfil", "/api/kpis", "/api/kpis/serie-mensal", "/api/colaboradores",
                "/api/colaboradores/" + colabA.id() + "/linha-do-tempo", "/api/alertas",
                "/api/alertas?status=ATENDIDO", "/api/alertas?destino=RH", "/api/alertas?destino=GERENTE",
                "/api/bem-estar");
    }

    @Test
    void gerenteDaLojaANaoVeNadaDaLojaBEmNenhumaRota() throws Exception {
        for (String rota : rotasDoGerente()) {
            String corpo = texto(GERENTE_A, rota);
            assertThat(corpo).as(rota).doesNotContain(colabB.id().toString(), NOME_B, LOJA_B, MOTIVO_B, ACAO_B,
                    "VONTADE_DE_SAIR", "Vontade de sair");
        }
        JsonNode colaboradores = json(GERENTE_A, "/api/colaboradores");
        assertThat(colaboradores).hasSize(1);
        assertThat(colaboradores.get(0).path("id").asText()).isEqualTo(colabA.id().toString());

        JsonNode alertas = json(GERENTE_A, "/api/alertas");
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).path("categoria").asText()).isEqualTo("CANSACO");
        assertThat(alertas.get(0).path("status").asText()).isEqualTo("SEM_RETORNO_24H");

        JsonNode kpis = json(GERENTE_A, "/api/kpis");
        assertThat(kpis.path("emJornada").asInt()).isEqualTo(1);
        assertThat(kpis.path("alertasAbertos").asInt() + kpis.path("semRetorno24h").asInt()
                + kpis.path("atendidos").asInt()).isEqualTo(1);
    }

    @Test
    void gerenteNuncaRecebeMotivoTextoNemCategoriaRestrita() throws Exception {
        for (String rota : rotasDoGerente()) {
            String corpo = texto(GERENTE_A, rota);
            assertThat(corpo).as(rota).doesNotContain(MOTIVO_A, TEXTO_A, MOTIVO_RESTRITO, TEXTO_RESTRITO,
                    "\"motivo\"", "Assédio", "Conflito com chefia", "Humilhação", "Situação grave");
            for (String restrita : RESTRITAS) {
                assertThat(corpo).as(rota).doesNotContain(restrita);
            }
        }
        // A sobrecarga com sinais graves foi ao RH como urgente: o gerente não a vê.
        assertThat(texto(GERENTE_A, "/api/alertas")).doesNotContain("SOBRECARGA");
        // O relato que o colaborador não quis enviar (DUVIDA, sem alerta) não conta.
        assertThat(texto(GERENTE_A, "/api/bem-estar")).doesNotContain("Dúvidas");
    }

    @Test
    void gerenteNaoLigaAlertaAPessoa() throws Exception {
        JsonNode alerta = json(GERENTE_A, "/api/alertas").get(0);
        assertThat(alerta.path("colaboradorId").isNull()).isTrue();

        JsonNode eventos = json(GERENTE_A, "/api/colaboradores/" + colabA.id() + "/linha-do-tempo").path("eventos");
        assertThat(eventos).isNotEmpty().allSatisfy(e -> assertThat(e.path("tipo").asText()).isEqualTo("CHECKIN"));

        JsonNode bemEstar = json(GERENTE_A, "/api/bem-estar");
        assertThat(bemEstar.path("pessoas")).allSatisfy(p -> {
            List<String> campos = new ArrayList<>();
            p.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).containsExactlyInAnyOrder("categoria", "sugestao");
        });
    }

    @Test
    void gerenteNaoAcessaRelatos() throws Exception {
        var r = mvc.perform(como(GERENTE_A, "/api/relatos")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).doesNotContain(TEXTO_A, TEXTO_RESTRITO);
    }

    @Test
    void idDeOutraLojaEIdInexistenteDao404Iguais() throws Exception {
        var outraLoja = mvc.perform(como(GERENTE_A, "/api/colaboradores/" + colabB.id() + "/linha-do-tempo"))
                .andReturn().getResponse();
        var inexistente = mvc.perform(como(GERENTE_A,
                "/api/colaboradores/00000000-0000-0000-0000-00000000dead/linha-do-tempo")).andReturn().getResponse();
        var invalido = mvc.perform(como(GERENTE_A, "/api/colaboradores/c01/linha-do-tempo")).andReturn().getResponse();

        assertThat(outraLoja.getStatus()).isEqualTo(404);
        assertThat(inexistente.getStatus()).isEqualTo(404);
        assertThat(invalido.getStatus()).isEqualTo(404);
        assertThat(outraLoja.getContentAsString()).isEqualTo(inexistente.getContentAsString())
                .isEqualTo(invalido.getContentAsString());
    }

    @Test
    void papelOuLojaEnviadosPeloNavegadorSaoIgnorados() throws Exception {
        String esperado = texto(GERENTE_A, "/api/alertas");
        String[] caminhos = {"/api/alertas?papel=RH&loja=" + LOJA_B + "&perfil=RH",
                "/api/alertas?user_id=" + RH};
        for (String caminho : caminhos) {
            String corpo = mvc.perform(como(GERENTE_A, caminho)).andReturn().getResponse().getContentAsString();
            assertThat(corpo).isEqualTo(esperado);
        }
        String comCabecalhos = mvc.perform(get("/api/alertas").cookie(sessao(GERENTE_A))
                        .header("X-Papel", "RH").header("X-Loja", LOJA_B).header("X-Perfil", "RH")
                        .contentType("application/json").content("{\"papel\":\"RH\",\"loja\":\"loja norte\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(comCabecalhos).isEqualTo(esperado);
        assertThat(mvc.perform(como(GERENTE_A, "/api/relatos?papel=RH").header("X-Papel", "RH"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        // O token traz app_metadata com papel RH e loja B (SupabaseAuthFalso): também ignorado.
        assertThat(json(GERENTE_A, "/api/perfil").path("perfil").asText()).isEqualTo("GERENTE");
        assertThat(json(GERENTE_A, "/api/perfil").path("loja").asText()).isEqualTo("loja centro");
    }

    @Test
    void rhVeTodasAsLojasComMotivoEAcao() throws Exception {
        JsonNode colaboradores = json(RH, "/api/colaboradores");
        assertThat(colaboradores).hasSize(2);

        JsonNode alertas = json(RH, "/api/alertas");
        assertThat(alertas).hasSize(6);
        Set<String> categorias = new HashSet<>();
        alertas.forEach(a -> categorias.add(a.path("categoria").asText()));
        assertThat(categorias).contains("CANSACO", "ASSEDIO", "CONFLITO_CHEFIA", "VONTADE_DE_SAIR", "SOBRECARGA");
        assertThat(texto(RH, "/api/alertas")).contains(MOTIVO_A, MOTIVO_B, MOTIVO_RESTRITO, ACAO_B);
        // Categoria confidencial: sem identificador da pessoa nem para o RH.
        alertas.forEach(a -> {
            if (RESTRITAS.contains(a.path("categoria").asText())) {
                assertThat(a.path("colaboradorId").isNull()).isTrue();
            }
        });

        assertThat(json(RH, "/api/alertas?destino=GERENTE")).hasSize(3);
        assertThat(json(RH, "/api/alertas?status=ATENDIDO")).hasSize(2);

        JsonNode relatos = json(RH, "/api/relatos");
        Set<String> doRh = new HashSet<>();
        relatos.forEach(r -> doRh.add(r.path("categoria").asText()));
        assertThat(doRh).containsExactlyInAnyOrder("ASSEDIO", "CONFLITO_CHEFIA", "SOBRECARGA", "VONTADE_DE_SAIR");
        assertThat(texto(RH, "/api/relatos")).contains(TEXTO_RESTRITO).doesNotContain(TEXTO_A);

        JsonNode eventos = json(RH, "/api/colaboradores/" + colabA.id() + "/linha-do-tempo").path("eventos");
        Set<String> tipos = new HashSet<>();
        eventos.forEach(e -> tipos.add(e.path("tipo").asText()));
        assertThat(tipos).contains("CHECKIN", "ALERTA", "RETORNO");
        assertThat(json(RH, "/api/colaboradores/" + colabB.id() + "/linha-do-tempo").path("eventos")).isNotEmpty();
    }

    @Test
    void filtroInvalidoDa400() throws Exception {
        assertThat(mvc.perform(como(RH, "/api/alertas?status=QUALQUER")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(como(RH, "/api/alertas?destino=GERENTE_E_RH")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(como(RH, "/api/relatos?status=x")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void camposComoNosJsonDoMock() throws Exception {
        compararCampos("kpis.json", json(RH, "/api/kpis"));
        compararCampos("serie-mensal.json", json(RH, "/api/kpis/serie-mensal"));
        compararCampos("colaboradores.json", json(RH, "/api/colaboradores").get(0));
        compararCampos("alertas.json", json(GERENTE_A, "/api/alertas").get(0));
        compararCampos("relatos.json", json(RH, "/api/relatos").get(0));
        compararCampos("bemestar.json", json(RH, "/api/bem-estar"));
        compararCampos("bemestar-gerente.json", json(GERENTE_A, "/api/bem-estar"));
        JsonNode linha = json(RH, "/api/colaboradores/" + colabA.id() + "/linha-do-tempo");
        assertThat(linha.path("colaboradorId").asText()).isEqualTo(colabA.id().toString());
        assertThat(linha.path("eventos").get(0).has("tipo")).isTrue();
        assertThat(linha.path("eventos").get(0).has("titulo")).isTrue();
        assertThat(linha.path("eventos").get(0).has("criadoEm")).isTrue();
        assertThat(linha.path("eventos").get(0).has("resumo")).isTrue();
    }

    /** Todo campo do mock existe na resposta real (a resposta pode ter campos a mais). */
    private static void compararCampos(String mock, JsonNode real) throws Exception {
        JsonNode exemplo = JSON.readTree(Files.readString(Path.of("..", "dashboard", "mock", mock)));
        if (exemplo.isArray()) {
            exemplo = exemplo.get(0);
        }
        assertCampos(mock, exemplo, real);
    }

    private static void assertCampos(String onde, JsonNode exemplo, JsonNode real) {
        for (Iterator<String> it = exemplo.fieldNames(); it.hasNext(); ) {
            String campo = it.next();
            assertThat(real.has(campo)).as(onde + " → " + campo).isTrue();
            JsonNode filho = exemplo.get(campo);
            if (filho.isObject()) {
                assertCampos(onde + "." + campo, filho, real.get(campo));
            } else if (filho.isArray() && !filho.isEmpty() && filho.get(0).isObject() && !real.get(campo).isEmpty()) {
                assertCampos(onde + "." + campo + "[0]", filho.get(0), real.get(campo).get(0));
            }
        }
    }
}
