package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.classificacao.ClassificadorGemini;
import br.com.kallopolli.parceiro.classificacao.GeminiFalso;
import br.com.kallopolli.parceiro.classificacao.GeminiProperties;
import br.com.kallopolli.parceiro.classificacao.PromptClassificacao;
import br.com.kallopolli.parceiro.jornada.BancoProperties;
import br.com.kallopolli.parceiro.painel.Acesso;
import br.com.kallopolli.parceiro.painel.PainelProperties;
import br.com.kallopolli.parceiro.painel.SupabaseAuthFalso;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/** Nenhum segredo, texto de mensagem ou telefone aparece no log, nem nos caminhos de erro. */
@ExtendWith(OutputCaptureExtension.class)
class LogsSemSegredosTest extends FluxoTestBase {

    private static final String NUMERO = "5500999990000";
    private static final String TEXTO = "texto ficticio de teste";

    @Autowired
    WhatsAppClient client;

    @Test
    void fluxoNormalNaoLogaSegredosTextoNemTelefone(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-1", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-1");

        assertThat(saida.getAll()).contains("mensagem recebida", "\"tipo\":\"text\"", "PEDIDO_ACEITE");
        semSegredos(saida);
    }

    @Test
    void falhaNoProcessamentoLogaSoWamidETipoDoErro(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        repositorios.falharNaProximaGravacao(new IllegalStateException(
                "erro com dados: " + TEXTO + " " + NUMERO + " " + TOKEN_FALSO + " " + SENHA_BANCO_FALSA));

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-2", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-2");

        assertThat(saida.getAll()).contains("falha ao processar mensagem", "\"erro\":\"IllegalStateException\"");
        semSegredos(saida);
    }

    @Test
    void erroDaMetaRepetindoOTokenNaoVaza(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+" + NUMERO);
        GRAPH_API.responder(401, """
                {"error":{"message":"Invalid OAuth access token %s","type":"OAuthException","code":190}}
                """.formatted(TOKEN_FALSO));

        mvc.perform(postAssinado(payloadTexto("wamid.LOG-3", NUMERO, TEXTO))).andExpect(status().isOk());
        esperarProcessamento(saida, "wamid.LOG-3");

        assertThat(saida.getAll()).contains("falha ao processar mensagem", "WhatsAppClientException");
        semSegredos(saida);
    }

    @Test
    void assinaturaInvalidaNaoLogaOCorpo(CapturedOutput saida) throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=00")
                        .content(payloadTexto("wamid.LOG-4", NUMERO, TEXTO)))
                .andExpect(status().isUnauthorized());

        assertThat(saida.getAll()).contains("assinatura do webhook ausente ou inválida");
        semSegredos(saida);
    }

    @Test
    void timeoutViraExcecaoSemToken(CapturedOutput saida) {
        GRAPH_API.atrasar(2_000);

        assertThatThrownBy(() -> client.enviarTexto(NUMERO, TEXTO))
                .isInstanceOf(WhatsAppClientException.class)
                .hasMessageContaining("timeout")
                .hasMessageNotContaining(TOKEN_FALSO);
        semSegredos(saida);
    }

    @Test
    void propriedadesNaoImprimemSegredos(@Autowired WhatsAppProperties whatsapp, @Autowired BancoProperties banco,
                                         @Autowired GeminiProperties gemini) {
        assertThat(whatsapp.toString()).doesNotContain(TOKEN_FALSO, VERIFY_TOKEN_FALSO, APP_SECRET_FALSO);
        assertThat(banco.toString()).doesNotContain(SENHA_BANCO_FALSA);
        assertThat(gemini.toString()).doesNotContain(CHAVE_GEMINI_FALSA);
    }

    @Test
    void classificadorNaoLogaRelatoRespostaNemChave(CapturedOutput saida, @Autowired ObjectMapper objectMapper,
                                                    @Autowired PromptClassificacao prompt) throws Exception {
        String motivo = "MOTIVO-FICTICIO-DA-RESPOSTA";
        try (GeminiFalso gemini = new GeminiFalso()) {
            var props = new GeminiProperties(CHAVE_GEMINI_FALSA, "modelo-falso", gemini.url(),
                    Duration.ofSeconds(2), Duration.ofMillis(300), 2000, 2, Duration.ofMillis(5), Duration.ofMillis(20));
            var classificador = new ClassificadorGemini(RestClient.builder(), props, objectMapper, prompt);

            // Pior caso: o erro repete a chave e o relato; depois vem uma resposta inválida e uma válida.
            var erroComEco = new GeminiFalso.Resposta(429, """
                    {"error":{"code":429,"message":"chave %s relato %s"}}""".formatted(CHAVE_GEMINI_FALSA, TEXTO), 0);
            gemini.responder(erroComEco, GeminiFalso.classificacao("INVENTADA"));
            assertThat(classificador.classificar(TEXTO).falhaIa()).isTrue();

            gemini.responder(new GeminiFalso.Resposta(500, "{\"erro\":\"" + TEXTO + "\"}", 0));
            assertThat(classificador.classificar(TEXTO).falhaIa()).isTrue();

            gemini.responder(GeminiFalso.sucesso("""
                    {"categoria":"CANSACO","motivo":"%s","acao_sugerida":"ACAO-FICTICIA",\
                    "confianca":"alta","sinais_graves":false}""".formatted(motivo)));
            assertThat(classificador.classificar(TEXTO + " " + NUMERO).motivo()).isEqualTo(motivo);
        }

        assertThat(saida.getAll()).contains("falha ao classificar relato", "\"status_http\":429", "RespostaInvalida");
        assertThat(saida.getAll()).doesNotContain(CHAVE_GEMINI_FALSA, motivo, "ACAO-FICTICIA");
        semSegredos(saida);
    }

    @Test
    void fluxoCompletoNaoLogaRelatoTelefoneNomeNemMotivo(CapturedOutput saida) throws Exception {
        String relato = "RELATO-FICTICIO-QUE-NAO-PODE-IR-PARA-LOG";
        cenario(saida);
        agendador(br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria.INICIO).tick();
        texto(saida, "3");
        texto(saida, relato);
        tocar(saida, COLABORADOR, "conf:" + ultimoRelato().id() + ":sim");
        String botao = para(enviadas(), GERENTE).get(0).botoes().get(0);
        tocar(saida, GERENTE, botao);
        GRAPH_API.fecharJanela(RH);
        classificador.sempre(br.com.kallopolli.parceiro.classificacao.ClassificadorFalso.categoria("ASSEDIO"));
        texto(saida, relato + " grave");

        assertThat(saida.getAll()).contains("HUMOR_REGISTRADO", "AGUARDANDO_CONFIRMACAO", "ENCAMINHADO",
                "CONVERSEI", "URGENTE", "falha ao enviar alerta");
        assertThat(saida.getAll()).doesNotContain(relato, COLABORADOR, GERENTE, RH, "Pessoa Ficticia",
                "Responsavel Ficticio", br.com.kallopolli.parceiro.classificacao.ClassificadorFalso.MOTIVO,
                br.com.kallopolli.parceiro.classificacao.ClassificadorFalso.ACAO);
        semSegredos(saida);
    }

    @Test
    void loginDoPainelNaoLogaEmailSenhaTokenNemChave(CapturedOutput saida, @Autowired PainelProperties painelProps)
            throws Exception {
        UUID usuario = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        String email = "email.ficticio.log@exemplo.invalid";
        String senha = "SENHA-FICTICIA-DE-LOG-8c2f";
        SUPABASE.cadastrar(usuario, email, senha);
        painel.darAcesso(new Acesso(usuario, Acesso.RH, null, "Responsavel Ficticio"));

        var ok = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String cookie = ok.getHeader("Set-Cookie");
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        mvc.perform(get("/api/kpis").cookie(new Cookie("parceiro_sessao", token))).andExpect(status().isOk());
        mvc.perform(get("/api/kpis").cookie(new Cookie("parceiro_sessao", token + "x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"errada-%s\"}".formatted(email, senha)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email))
                .andExpect(status().isUnauthorized());
        SUPABASE.responderToken(500);
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/logout").cookie(new Cookie("parceiro_sessao", token))).andExpect(status().isNoContent());

        assertThat(saida.getAll()).contains("login no painel", "\"resultado\":\"OK\"",
                "\"resultado\":\"RECUSADO_CREDENCIAIS\"", "\"resultado\":\"AUTH_INDISPONIVEL\"");
        assertThat(saida.getAll()).doesNotContain(email, senha, token, token.substring(token.lastIndexOf('.') + 1),
                SupabaseAuthFalso.CHAVE_PUBLICAVEL_FALSA, "parceiro_sessao=");
        assertThat(painelProps.toString()).doesNotContain(SupabaseAuthFalso.CHAVE_PUBLICAVEL_FALSA);
        semSegredos(saida);
    }

    private static void semSegredos(CapturedOutput saida) throws RuntimeException {
        try {
            GRAPH_API.recebidas().poll(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(saida.getAll()).doesNotContain(
                TOKEN_FALSO, VERIFY_TOKEN_FALSO, APP_SECRET_FALSO, SENHA_BANCO_FALSA, TEXTO, NUMERO);
    }
}
