package br.com.kallopolli.parceiro.simulador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.classificacao.ClassificadorFalso;
import br.com.kallopolli.parceiro.consentimento.EnvioAcompanhamento;
import br.com.kallopolli.parceiro.conversa.AgendadorCheckin;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import br.com.kallopolli.parceiro.whatsapp.WebhookTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Simulador local (parceiro.whatsapp.canal=simulador): página, API, guarda de loopback e o fluxo
 * completo pela API. Repositórios em memória, classificador falso, números e textos fictícios.
 */
@SpringBootTest(properties = {
        "parceiro.whatsapp.canal=simulador",
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:1/nao-usado",
        "DATABASE_USER=usuario-falso",
        "DATABASE_PASSWORD=SENHA-FALSA-SIMULADOR",
        "GEMINI_API_KEY=CHAVE-FALSA-SIMULADOR",
        "GEMINI_MODEL=modelo-falso",
        "management.health.db.enabled=false",
        "parceiro.checkin.ativo=false",
        "parceiro.retencao.ativo=false"
})
@AutoConfigureMockMvc
@Import(WebhookTestBase.Repositorios.class)
@ExtendWith(OutputCaptureExtension.class)
class SimuladorTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String COLABORADOR = "5511999990701";
    private static final String GERENTE = "5511999990702";
    private static final String RH = "5511999990703";
    private static final String RELATO = "RELATO-FICTICIO-DO-SIMULADOR";

    @Autowired
    MockMvc mvc;

    @Autowired
    RepositoriosEmMemoria repositorios;

    @Autowired
    ClassificadorFalso classificador;

    @Autowired
    EnvioAcompanhamento envioAcompanhamento;

    @BeforeEach
    void limpar() throws Exception {
        ultimaVista.clear();
        repositorios.limpar();
        classificador.limpar();
        // A fila do simulador vive enquanto o contexto vive: começa do que já existe.
        for (String telefone : List.of(COLABORADOR, GERENTE, RH, COLABORADOR.substring(0, 4) + COLABORADOR.substring(5))) {
            ultimas(telefone);
        }
    }

    @Test
    void paginaEServidaComAPoliticaDoDashboard() throws Exception {
        var pagina = mvc.perform(get("/simulador"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.startsWith("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString();
        assertThat(pagina).contains("Simulador de WhatsApp", "src=\"/simulador/app.js\"").doesNotContain("<script>");
        mvc.perform(get("/simulador/app.js")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/javascript")));
        mvc.perform(get("/simulador/estilo.css")).andExpect(status().isOk());
        mvc.perform(get("/simulador/../application.yml")).andExpect(status().is4xxClientError());
    }

    @ParameterizedTest
    @CsvSource({
            "Forwarded, for=203.0.113.5",
            "X-Forwarded-For, 203.0.113.5",
            "CF-Connecting-IP, 203.0.113.5",
            "X-Real-IP, 203.0.113.5",
            "Host, parceiro.trycloudflare.com",
            "Host, 192.168.0.10:8080",
            "Host, [::1]:8080"})
    void recusaRequisicaoQueNaoEDaPropriaMaquina(String cabecalho, String valor) throws Exception {
        mvc.perform(get("/simulador/api/contatos").header(cabecalho, valor)).andExpect(status().isForbidden());
        mvc.perform(get("/simulador").header(cabecalho, valor)).andExpect(status().isForbidden());
        mvc.perform(post("/simulador/api/mensagens").header(cabecalho, valor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"telefone\":\"" + COLABORADOR + "\",\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recusaConexaoQueNaoEDeLoopback() throws Exception {
        mvc.perform(get("/simulador/api/contatos").with(r -> {
            r.setRemoteAddr("203.0.113.5");
            return r;
        })).andExpect(status().isForbidden());
    }

    @Test
    void aceitaHostLocal() throws Exception {
        mvc.perform(get("/simulador/api/contatos").header("Host", "127.0.0.1:8080")).andExpect(status().isOk());
        mvc.perform(get("/simulador/api/contatos").header("Host", "LOCALHOST:8080")).andExpect(status().isOk());
    }

    @Test
    void contatosJuntamOsPapeisDoMesmoTelefone() throws Exception {
        repositorios.cadastrar("+" + COLABORADOR);
        repositorios.cadastrarResponsavel("+" + COLABORADOR, Responsavel.GERENTE, RepositoriosEmMemoria.LOJA);
        repositorios.cadastrarResponsavel("+" + RH, Responsavel.RH, null);

        JsonNode contatos = JSON.readTree(mvc.perform(get("/simulador/api/contatos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(contatos).hasSize(2);
        assertThat(contatos.get(0).path("telefone").asText()).isEqualTo(COLABORADOR);
        assertThat(contatos.get(0).path("papeis").toString()).contains("Colaborador", "Gerente · Loja Teste");
        assertThat(contatos.get(1).path("papeis").toString()).contains("RH");
    }

    @Test
    void pedidoInvalidoDa400() throws Exception {
        for (String corpo : List.of("{}", "{\"telefone\":\"123\",\"texto\":\"oi\"}",
                "{\"telefone\":\"" + COLABORADOR + "\",\"texto\":\"\"}", "nao e json")) {
            mvc.perform(post("/simulador/api/mensagens").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void fluxoCompletoPeloSimulador(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + COLABORADOR);
        repositorios.cadastrarResponsavel("+" + GERENTE, Responsavel.GERENTE, RepositoriosEmMemoria.LOJA);
        repositorios.cadastrarResponsavel("+" + RH, Responsavel.RH, null);

        escrever(saida, COLABORADOR, "oi");
        assertThat(ultimas(COLABORADOR)).singleElement().satisfies(m -> {
            assertThat(m.path("texto").asText()).contains("responda ACEITO");
            assertThat(m.path("opcoes").toString()).contains("ACEITO");
        });

        escrever(saida, COLABORADOR, "ACEITO");
        assertThat(repositorios.consentimentos()).singleElement().extracting("ativo").isEqualTo(true);
        ultimas(COLABORADOR);

        agendador().tick();
        assertThat(ultimas(COLABORADOR)).singleElement().satisfies(m ->
                assertThat(m.path("opcoes").toString()).contains("\"1\"", "\"5\""));
        escrever(saida, COLABORADOR, "4");
        assertThat(repositorios.checkins()).singleElement().extracting("humor").isEqualTo(4);
        ultimas(COLABORADOR);

        // Em LIVRE, qualquer texto recebe o menu, com os atalhos das opções.
        escrever(saida, COLABORADOR, "oi");
        assertThat(ultimas(COLABORADOR)).singleElement().satisfies(m -> {
            assertThat(m.path("texto").asText()).contains(TextosConversa.PEDIDO_NUMERO);
            assertThat(m.path("opcoes").toString()).contains("\"1\"", "\"2\"");
        });
        escrever(saida, COLABORADOR, "1");
        assertThat(ultimas(COLABORADOR)).singleElement().extracting(m -> m.path("texto").asText())
                .isEqualTo(TextosConversa.aberturaRelato());

        classificador.sempre(ClassificadorFalso.categoria("VONTADE_DE_SAIR"));
        escrever(saida, COLABORADOR, RELATO);
        assertThat(ultimas(COLABORADOR)).singleElement().satisfies(m -> {
            assertThat(m.path("texto").asText()).endsWith(TextosConversa.OPCOES_CONFIRMACAO);
            assertThat(m.path("opcoes").toString()).contains("SIM", "NÃO");
        });
        assertThat(repositorios.alertas()).isEmpty();

        escrever(saida, COLABORADOR, "SIM");
        assertThat(ultimas(COLABORADOR)).singleElement().extracting(m -> m.path("texto").asText())
                .isEqualTo(TextosConversa.relatoEncaminhado());
        JsonNode alertaGerente = ultimas(GERENTE).get(0);
        JsonNode alertaRh = ultimas(RH).get(0);
        assertThat(alertaGerente.path("texto").asText()).contains("Loja: Loja Teste", ClassificadorFalso.ACAO)
                .doesNotContain(ClassificadorFalso.MOTIVO, RELATO, "Pessoa Ficticia");
        assertThat(alertaRh.path("texto").asText()).contains(ClassificadorFalso.MOTIVO).doesNotContain(RELATO);
        assertThat(alertaGerente.path("opcoes").toString()).contains("CONVERSEI", "NÃO CONSEGUI");

        escrever(saida, GERENTE, "CONVERSEI");
        assertThat(ultimas(GERENTE)).singleElement().extracting(m -> m.path("texto").asText())
                .isEqualTo(TextosConversa.respostaConversei());
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ATENDIDO");

        escrever(saida, RH, "não consegui");
        assertThat(repositorios.entregas()).extracting("resposta").containsExactlyInAnyOrder("CONVERSEI", "NAO_CONSEGUI");

        assertThat(saida.getAll()).doesNotContain(RELATO, COLABORADOR, GERENTE, RH, ClassificadorFalso.MOTIVO,
                "Pessoa Ficticia");
    }

    @Test
    void telefoneDigitadoSemONonoDigitoVeAsRespostas(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + COLABORADOR);
        String sem9 = COLABORADOR.substring(0, 4) + COLABORADOR.substring(5);

        escrever(saida, sem9, "oi");

        assertThat(ultimas(sem9)).singleElement().extracting(m -> m.path("numero").asText()).isEqualTo(COLABORADOR);
    }

    @Test
    void atalhosSoAparecemQuandoAMensagemPedeAResposta() {
        assertThat(SimuladorCanal.opcoes(TextosConversa.boasVindas())).isEmpty();
        assertThat(SimuladorCanal.opcoes(TextosConversa.checkin(2, "Pessoa"))).containsExactly("1", "2", "3", "4", "5");
        assertThat(SimuladorCanal.opcoes(TextosConversa.confirmacaoRelato("m", "GERENTE"))).containsExactly("SIM", "NÃO");
        assertThat(SimuladorCanal.opcoes(TextosConversa.alertaGerente("Loja", "Noite", "CANSACO", "a")))
                .containsExactly("CONVERSEI", "NÃO CONSEGUI");
        assertThat(SimuladorCanal.opcoes(TextosConversa.respostaConversei())).isEmpty();
        assertThat(SimuladorCanal.opcoes(TextosConversa.menu(List.of("1 - Contar algo", "2 - Fazer um elogio", "3 - Outra"))))
                .containsExactly("1", "2", "3");
        assertThat(SimuladorCanal.opcoes(TextosConversa.menuCurto(List.of("1 - Contar algo")))).containsExactly("1");
        assertThat(SimuladorCanal.opcoes(TextosConversa.aberturaRelato())).as("a página mostra o atalho menu").isEmpty();
    }

    @Test
    void filaGuardaAsUltimas200() {
        var canal = new SimuladorCanal();
        for (int i = 0; i < 250; i++) {
            canal.enviarTexto(COLABORADOR, "mensagem " + i);
        }

        var lista = canal.depois(COLABORADOR, 0);
        assertThat(lista).hasSize(SimuladorCanal.MAXIMO);
        assertThat(lista.get(0).texto()).isEqualTo("mensagem 50");
    }

    private final java.util.Map<String, Long> ultimaVista = new java.util.HashMap<>();

    /** Envia pela API do simulador e espera o processamento terminar. */
    private void escrever(CapturedOutput saida, String telefone, String texto) throws Exception {
        String resposta = mvc.perform(post("/simulador/api/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(java.util.Map.of("telefone", telefone, "texto", texto))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JSON.readTree(resposta).path("id").asText();
        var limite = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(limite) && saida.getAll().lines()
                .noneMatch(l -> l.contains("\"wamid\":\"" + id + "\"") && l.contains("mensagem processada"))) {
            Thread.sleep(25);
        }
        assertThat(saida.getAll()).as("processamento de " + id).contains("\"wamid\":\"" + id + "\"");
    }

    /** Lê as mensagens do robô para o telefone desde a última leitura deste teste. */
    private List<JsonNode> ultimas(String telefone) throws Exception {
        JsonNode corpo = JSON.readTree(mvc.perform(get("/simulador/api/mensagens")
                        .param("telefone", telefone).param("depois", String.valueOf(ultimaVista.getOrDefault(telefone, 0L))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<JsonNode> lista = new ArrayList<>();
        corpo.path("mensagens").forEach(lista::add);
        ultimaVista.put(telefone, corpo.path("ultima").asLong());
        return lista;
    }

    private AgendadorCheckin agendador() {
        Clock relogio = Clock.fixed(RepositoriosEmMemoria.INICIO.atTime(10, 0).atZone(SP).toInstant(), SP);
        return new AgendadorCheckin(repositorios, repositorios, repositorios, envioAcompanhamento, repositorios, relogio);
    }
}
