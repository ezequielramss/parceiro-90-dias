package br.com.kallopolli.parceiro.whatsapp;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.consentimento.EnvioAcompanhamento;
import br.com.kallopolli.parceiro.conversa.AgendadorCheckin;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;

/** Apoio aos testes do fluxo da Etapa 1. Números fictícios a partir de +5511999990000. */
abstract class FluxoTestBase extends WebhookTestBase {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final String COLABORADOR = "5511999990101";
    static final String GERENTE = "5511999990201";
    static final String GERENTE_OUTRA_LOJA = "5511999990202";
    static final String RH = "5511999990301";
    static final String OUTRO = "5511999990999";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    EnvioAcompanhamento envioAcompanhamento;

    /** Uma mensagem enviada à Graph API falsa, já lida. */
    record Enviada(String para, String texto, List<String> botoes) {
        boolean interativa() {
            return !botoes.isEmpty();
        }
    }

    Colaborador colaborador;
    Responsavel gerente;
    Responsavel gerenteOutraLoja;
    Responsavel rh;

    /** Colaborador com aceite, gerentes de duas lojas e RH; descarta as mensagens do aceite. */
    void cenario(CapturedOutput saida) throws Exception {
        colaborador = repositorios.cadastrar("+" + COLABORADOR);
        gerente = repositorios.cadastrarResponsavel("+" + GERENTE, Responsavel.GERENTE, " loja  TESTE ");
        gerenteOutraLoja = repositorios.cadastrarResponsavel("+" + GERENTE_OUTRA_LOJA, Responsavel.GERENTE, "Loja Norte");
        rh = repositorios.cadastrarResponsavel("+" + RH, Responsavel.RH, null);
        texto(saida, "aceito");
        enviadas();
    }

    String texto(CapturedOutput saida, String texto) throws Exception {
        return texto(saida, COLABORADOR, texto);
    }

    String texto(CapturedOutput saida, String de, String texto) throws Exception {
        String wamid = "wamid.FLX-" + SEQUENCIA.incrementAndGet();
        postar(saida, wamid, payloadTexto(wamid, de, texto));
        return wamid;
    }

    String tocar(CapturedOutput saida, String de, String botaoId) throws Exception {
        String wamid = "wamid.BTN-" + SEQUENCIA.incrementAndGet();
        postar(saida, wamid, payloadBotao(wamid, de, botaoId, "titulo"));
        return wamid;
    }

    void postar(CapturedOutput saida, String wamid, String corpo) throws Exception {
        mvc.perform(postAssinado(corpo)).andExpect(status().isOk());
        esperarProcessamento(saida, wamid);
    }

    /** Lê e esvazia o que foi enviado à Graph API falsa. */
    List<Enviada> enviadas() throws Exception {
        List<GraphApiFalsa.Requisicao> brutas = new ArrayList<>();
        GRAPH_API.recebidas().drainTo(brutas);
        List<Enviada> lidas = new ArrayList<>();
        for (var r : brutas) {
            JsonNode corpo = JSON.readTree(r.corpo());
            List<String> botoes = new ArrayList<>();
            corpo.path("interactive").path("action").path("buttons")
                    .forEach(b -> botoes.add(b.path("reply").path("id").asText()));
            String texto = corpo.path("type").asText().equals("interactive")
                    ? corpo.path("interactive").path("body").path("text").asText()
                    : corpo.path("text").path("body").asText();
            lidas.add(new Enviada(corpo.path("to").asText(), texto, botoes));
        }
        return lidas;
    }

    List<Enviada> para(List<Enviada> enviadas, String numero) {
        return enviadas.stream().filter(e -> e.para().equals(numero)).toList();
    }

    AgendadorCheckin agendador(LocalDate dia) {
        Clock relogio = Clock.fixed(dia.atTime(10, 0).atZone(SP).toInstant(), SP);
        return new AgendadorCheckin(repositorios, repositorios, repositorios, envioAcompanhamento, repositorios, relogio);
    }

    RepositoriosEmMemoria.Relato ultimoRelato() {
        var lista = repositorios.relatos();
        return lista.get(lista.size() - 1);
    }

    List<String> acoesAuditadas() {
        return repositorios.auditoria().stream().map(RepositoriosEmMemoria.Auditoria::acao).toList();
    }
}
