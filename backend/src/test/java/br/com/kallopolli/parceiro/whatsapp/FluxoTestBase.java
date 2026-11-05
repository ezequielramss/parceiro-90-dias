package br.com.kallopolli.parceiro.whatsapp;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.consentimento.EnvioAcompanhamento;
import br.com.kallopolli.parceiro.conversa.AgendadorCheckin;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
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

    /** Uma mensagem enviada à Evolution falsa, já lida. */
    record Enviada(String para, String texto) {
        /** A confirmação do relato: termina com as opções SIM e NÃO. */
        boolean pedeConfirmacao() {
            return texto.endsWith(TextosConversa.OPCOES_CONFIRMACAO);
        }

        /** Alerta a gerente ou RH: termina com as opções CONVERSEI e NÃO CONSEGUI. */
        boolean alerta() {
            return texto.endsWith(TextosConversa.OPCOES_ALERTA);
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


    /** Escolhe "Contar algo" no menu (descarta a abertura) e conta o relato. */
    String contar(CapturedOutput saida, String texto) throws Exception {
        return contar(saida, COLABORADOR, texto);
    }

    String contar(CapturedOutput saida, String de, String texto) throws Exception {
        texto(saida, de, "1");
        enviadas();
        return texto(saida, de, texto);
    }

    void postar(CapturedOutput saida, String wamid, String corpo) throws Exception {
        mvc.perform(postWebhook(corpo)).andExpect(status().isOk());
        esperarProcessamento(saida, wamid);
    }

    /** Lê e esvazia o que foi enviado à Evolution falsa ({"number","text"}). */
    List<Enviada> enviadas() throws Exception {
        List<EvolutionFalsa.Requisicao> brutas = new ArrayList<>();
        EVOLUTION.recebidas().drainTo(brutas);
        List<Enviada> lidas = new ArrayList<>();
        for (var r : brutas) {
            JsonNode corpo = JSON.readTree(r.corpo());
            lidas.add(new Enviada(corpo.path("number").asText(), corpo.path("text").asText()));
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
