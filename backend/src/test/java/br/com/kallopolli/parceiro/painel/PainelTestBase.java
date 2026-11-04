package br.com.kallopolli.parceiro.painel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.kallopolli.parceiro.painel.PainelEmMemoria.Colab;
import br.com.kallopolli.parceiro.painel.PainelEmMemoria.Relato;
import br.com.kallopolli.parceiro.whatsapp.WebhookTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Apoio aos testes do painel: duas lojas fictícias, um RH, um gerente de cada loja e um usuário
 * do Supabase Auth sem linha em acessos_dashboard.
 */
abstract class PainelTestBase extends WebhookTestBase {

    static final ObjectMapper JSON = new ObjectMapper();
    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    static final UUID RH = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID GERENTE_A = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID GERENTE_B = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    static final UUID SEM_ACESSO = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    static final String LOJA_A = "Loja Centro";
    static final String LOJA_B = "Loja Norte";

    // Marcas fictícias: se aparecerem numa resposta do gerente da loja A, o teste falha.
    static final String NOME_B = "Pessoa Ficticia Norte";
    static final String TEXTO_A = "TEXTO-FICTICIO-DO-RELATO-A";
    static final String MOTIVO_A = "MOTIVO-FICTICIO-A";
    static final String MOTIVO_B = "MOTIVO-FICTICIO-B";
    static final String MOTIVO_RESTRITO = "MOTIVO-FICTICIO-RESTRITO";
    static final String TEXTO_RESTRITO = "TEXTO-FICTICIO-RESTRITO";
    static final String ACAO_B = "ACAO-FICTICIA-B";

    Colab colabA;
    Colab colabB;
    Relato cansacoA;
    Relato assedioA;

    void acessos() {
        painel.darAcesso(new Acesso(RH, Acesso.RH, null, "RH Ficticio"));
        painel.darAcesso(new Acesso(GERENTE_A, Acesso.GERENTE, PainelEmMemoria.normalizar(LOJA_A), "Gerente Ficticio A"));
        painel.darAcesso(new Acesso(GERENTE_B, Acesso.GERENTE, PainelEmMemoria.normalizar(LOJA_B), "Gerente Ficticio B"));
    }

    /** Dados fictícios de duas lojas, com categorias de gerente, restritas e agregadas. */
    void cenarioDuasLojas() {
        acessos();
        Instant agora = Instant.now();
        LocalDate hoje = LocalDate.now(SP);
        colabA = painel.colaborador("Pessoa Ficticia Centro", " loja  CENTRO ", hoje.minusDays(3), agora.minusSeconds(600));
        colabB = painel.colaborador(NOME_B, LOJA_B, hoje.minusDays(5), agora.minusSeconds(900));

        painel.checkin(colabA.id(), 1, agora.minus(Duration.ofDays(2)), agora.minus(Duration.ofDays(2)), 2);
        painel.checkin(colabA.id(), 2, agora.minus(Duration.ofDays(1)), null, null);
        painel.checkin(colabB.id(), 1, agora.minus(Duration.ofDays(4)), agora.minus(Duration.ofDays(4)), 1);
        painel.checkin(colabB.id(), 2, agora.minus(Duration.ofDays(3)), agora.minus(Duration.ofDays(3)), 1);

        cansacoA = painel.relato(colabA.id(), "CANSACO", agora.minus(Duration.ofHours(30)), TEXTO_A, MOTIVO_A,
                "Vale uma conversa ainda hoje");
        painel.alerta(cansacoA, "GERENTE", false, "ABERTO", agora.minus(Duration.ofHours(30)), null, null);

        assedioA = painel.relato(colabA.id(), "ASSEDIO", agora.minus(Duration.ofHours(5)), TEXTO_RESTRITO,
                MOTIVO_RESTRITO, "Contato do RH ainda hoje");
        painel.alerta(assedioA, "RH", true, "ABERTO", agora.minus(Duration.ofHours(5)), null, null);

        Relato chefiaA = painel.relato(colabA.id(), "CONFLITO_CHEFIA", agora.minus(Duration.ofHours(4)),
                TEXTO_RESTRITO, MOTIVO_RESTRITO, "Contato do RH");
        painel.alerta(chefiaA, "RH", false, "ATENDIDO", agora.minus(Duration.ofHours(4)), "CONVERSEI",
                agora.minus(Duration.ofHours(2)));

        // Categoria de gerente, mas com sinais graves: foi ao RH como urgente.
        Relato graveA = painel.relato(colabA.id(), "SOBRECARGA", agora.minus(Duration.ofHours(3)), TEXTO_RESTRITO,
                MOTIVO_RESTRITO, "Contato do RH");
        painel.alerta(graveA, "RH", true, "ABERTO", agora.minus(Duration.ofHours(3)), null, null);

        painel.relato(colabA.id(), "ELOGIO", agora.minus(Duration.ofHours(2)), TEXTO_A, MOTIVO_A, null);
        // O colaborador escolheu "Não enviar": sem alerta, não aparece para ninguém.
        painel.relato(colabA.id(), "DUVIDA", agora.minus(Duration.ofHours(1)), TEXTO_A, MOTIVO_A, "Orientar");

        Relato sairB = painel.relato(colabB.id(), "VONTADE_DE_SAIR", agora.minus(Duration.ofHours(6)),
                "TEXTO-FICTICIO-B", MOTIVO_B, ACAO_B);
        painel.alerta(sairB, "GERENTE_E_RH", false, "ATENDIDO", agora.minus(Duration.ofHours(6)), "CONVERSEI",
                agora.minus(Duration.ofHours(1)));
        Relato cansacoB = painel.relato(colabB.id(), "CANSACO", agora.minus(Duration.ofHours(7)),
                "TEXTO-FICTICIO-B", MOTIVO_B, ACAO_B);
        painel.alerta(cansacoB, "GERENTE", false, "ABERTO", agora.minus(Duration.ofHours(7)), null, null);
    }

    static Cookie sessao(String token) {
        return new Cookie(FiltroPainel.COOKIE, token);
    }

    static Cookie sessao(UUID usuario) {
        return sessao(SUPABASE.token(usuario));
    }

    static MockHttpServletRequestBuilder como(UUID usuario, String caminho) {
        return get(caminho).cookie(sessao(usuario));
    }

    JsonNode json(UUID usuario, String caminho) throws Exception {
        String corpo = mvc.perform(como(usuario, caminho)).andReturn().getResponse().getContentAsString();
        return JSON.readTree(corpo);
    }

    String texto(UUID usuario, String caminho) throws Exception {
        return mvc.perform(como(usuario, caminho)).andReturn().getResponse().getContentAsString();
    }
}
