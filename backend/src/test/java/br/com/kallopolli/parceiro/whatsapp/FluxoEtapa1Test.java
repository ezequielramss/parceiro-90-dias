package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.classificacao.ClassificadorFalso;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Fluxo completo da Etapa 1 com repositórios em memória, Graph API falsa e classificador falso. */
@ExtendWith(OutputCaptureExtension.class)
class FluxoEtapa1Test extends FluxoTestBase {

    private static final String RELATO = "relato ficticio de cansaco no fechamento";

    @Test
    void fluxoFeliz(CapturedOutput saida) throws Exception {
        cenario(saida);

        agendador(RepositoriosEmMemoria.INICIO).tick();
        var checkin = enviadas();
        assertThat(checkin).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo(COLABORADOR);
            assertThat(e.texto()).contains("Oi, Pessoa!", "nota de 1 a 5");
        });

        texto(saida, "2");
        assertThat(repositorios.checkins()).singleElement().extracting("humor").isEqualTo(2);
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.respostaNota(2));

        texto(saida, RELATO);
        var relato = ultimoRelato();
        assertThat(relato.confirmacao()).isEqualTo("AGUARDANDO");
        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.texto()).contains(ClassificadorFalso.MOTIVO, "o gerente da sua loja");
            assertThat(e.botoes()).containsExactly("conf:" + relato.id() + ":sim", "conf:" + relato.id() + ":nao");
        });
        assertThat(repositorios.alertas()).as("nada sai antes do sim").isEmpty();

        tocar(saida, COLABORADOR, "conf:" + relato.id() + ":sim");
        var depoisDoSim = enviadas();
        var alertaGerente = para(depoisDoSim, GERENTE);
        assertThat(alertaGerente).singleElement().satisfies(e -> {
            assertThat(e.texto()).contains("Loja: Loja Teste", "Turno: Noite", "Cansaço", ClassificadorFalso.ACAO)
                    .doesNotContain(ClassificadorFalso.MOTIVO, "Pessoa Ficticia", RELATO);
            assertThat(e.botoes()).hasSize(2).allMatch(b -> b.startsWith("alerta:"));
        });
        assertThat(para(depoisDoSim, GERENTE_OUTRA_LOJA)).isEmpty();
        assertThat(para(depoisDoSim, RH)).isEmpty();
        assertThat(para(depoisDoSim, COLABORADOR)).singleElement().extracting("texto")
                .isEqualTo(TextosConversa.relatoEncaminhado());
        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");

        String botaoOk = alertaGerente.get(0).botoes().get(0);
        tocar(saida, GERENTE, botaoOk);
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ATENDIDO");
        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo(GERENTE);
            assertThat(e.texto()).isEqualTo(TextosConversa.respostaConversei());
        });
        assertThat(acoesAuditadas()).contains(
                "CHECKIN_ENVIADO", "RELATO_CONFIRMADO", "ALERTA_CRIADO", "ALERTA_ENVIADO", "TOQUE_CONVERSEI");
    }

    @Test
    void naoDescartaENadaSai(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        enviadas();

        texto(saida, "Não");

        assertThat(ultimoRelato().confirmacao()).isEqualTo("DESCARTADO");
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.nadaEnviado());
    }

    @Test
    void textoNovoDuranteAConfirmacaoReclassifica(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        enviadas();

        texto(saida, "na verdade e excesso de tarefas");

        assertThat(classificador.recebidos()).containsExactly(RELATO, "na verdade e excesso de tarefas");
        assertThat(repositorios.relatos()).singleElement().satisfies(r -> {
            assertThat(r.texto()).isEqualTo("na verdade e excesso de tarefas");
            assertThat(r.confirmacao()).isEqualTo("AGUARDANDO");
        });
        assertThat(enviadas()).singleElement().matches(Enviada::interativa);
        assertThat(repositorios.alertas()).isEmpty();
    }

    @Test
    void pararNoMeioLimpaOEstado(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);

        texto(saida, "PARAR");

        assertThat(ultimoRelato().confirmacao()).isEqualTo("DESCARTADO");
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
        assertThat(repositorios.consentimentos()).extracting("ativo").containsExactly(false);
        assertThat(repositorios.alertas()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE"})
    void relatoUrgenteVaiAoRhNaHoraSemConfirmacao(String categoria, CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria(categoria));

        texto(saida, "relato ficticio grave");

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).singleElement().satisfies(e -> {
            assertThat(e.texto()).startsWith("URGENTE").contains(ClassificadorFalso.MOTIVO)
                    .doesNotContain("Pessoa Ficticia", "relato ficticio grave");
            assertThat(e.botoes()).hasSize(2);
        });
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(para(enviadas, COLABORADOR)).singleElement().extracting("texto")
                .isEqualTo(TextosConversa.relatoUrgente());
        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");
        assertThat(repositorios.alertas()).singleElement().satisfies(a -> {
            assertThat(a.destino()).isEqualTo("RH");
            assertThat(a.urgente()).isTrue();
        });
    }

    @Test
    void sinaisGravesEmCategoriaComumVaoAoRhComoUrgente(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(new Classificacao("CANSACO", "m", "a", "alta", true, false));

        texto(saida, "relato ficticio");

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).singleElement().extracting("texto").asString().startsWith("URGENTE");
        assertThat(para(enviadas, GERENTE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFLITO_CHEFIA", "NAO_CLASSIFICADO"})
    void categoriaRestritaNuncaChegaAGerenteAlgum(String categoria, CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria(categoria));
        texto(saida, "relato ficticio");
        var confirmacao = enviadas();
        assertThat(confirmacao).singleElement().extracting("texto").asString().contains("o RH");

        tocar(saida, COLABORADOR, confirmacao.get(0).botoes().get(0));

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).hasSize(1);
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(para(enviadas, GERENTE_OUTRA_LOJA)).isEmpty();
    }

    @Test
    void iaForaDoArSalvaORelatoAvisaORhEOColaborador(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(Classificacao.falha());

        texto(saida, RELATO);

        assertThat(ultimoRelato().classificacao().falhaIa()).isTrue();
        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");
        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).singleElement().extracting("texto").asString()
                .contains("relato sem classificação").doesNotContain(RELATO);
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(para(enviadas, COLABORADOR)).singleElement().extracting("texto")
                .isEqualTo(TextosConversa.iaIndisponivel());
    }

    @Test
    void naoConseguiMantemAbertoDepoisConverseiAtendeERepeticoesSaoIgnoradas(CapturedOutput saida) throws Exception {
        String ok = alertaAoGerente(saida);
        String nao = ok.replace(":ok", ":nao");

        tocar(saida, GERENTE, nao);
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("NAO_CONSEGUI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ABERTO");
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.respostaNaoConsegui());

        tocar(saida, GERENTE, ok);
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ATENDIDO");
        enviadas();

        tocar(saida, GERENTE, ok);
        tocar(saida, GERENTE, nao);
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(enviadas()).isEmpty();
        assertThat(acoesAuditadas()).containsOnlyOnce("TOQUE_CONVERSEI", "TOQUE_NAO_CONSEGUI");
    }

    @Test
    void toqueDeOutroNumeroEIgnorado(CapturedOutput saida) throws Exception {
        String ok = alertaAoGerente(saida);

        tocar(saida, RH, ok);
        tocar(saida, OUTRO, ok);

        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isNull();
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ABERTO");
        assertThat(enviadas()).isEmpty();
        assertThat(acoesAuditadas()).filteredOn("TOQUE_IGNORADO"::equals).hasSize(2);
    }

    @Test
    void lojaSemGerenteCaiNoRh(CapturedOutput saida) throws Exception {
        cenario(saida);
        repositorios.limpar();
        colaborador = repositorios.cadastrar("+" + COLABORADOR, "Loja Sem Gerente", RepositoriosEmMemoria.INICIO);
        repositorios.cadastrarResponsavel("+" + GERENTE, "GERENTE", "Loja Teste");
        repositorios.cadastrarResponsavel("+" + RH, "RH", null);
        texto(saida, "aceito");
        texto(saida, RELATO);
        var confirmacao = enviadas().stream().filter(Enviada::interativa).toList();

        tocar(saida, COLABORADOR, confirmacao.get(0).botoes().get(0));

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).hasSize(1);
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(acoesAuditadas()).contains("ALERTA_LOJA_SEM_GERENTE");
    }

    @Test
    void janelaFechadaParaOResponsavelRegistraFalha(CapturedOutput saida) throws Exception {
        cenario(saida);
        GRAPH_API.fecharJanela(GERENTE);
        texto(saida, RELATO);
        var relato = ultimoRelato();

        tocar(saida, COLABORADOR, "conf:" + relato.id() + ":sim");

        assertThat(repositorios.entregas()).singleElement().satisfies(e -> {
            assertThat(e.falhaEnvio()).isTrue();
            assertThat(e.wamid()).isNull();
        });
        assertThat(acoesAuditadas()).contains("ALERTA_FALHA_ENVIO");
    }

    @Test
    void wamidRepetidoNaoDuplicaNada(CapturedOutput saida) throws Exception {
        cenario(saida);
        String corpo = payloadTexto("wamid.DUP-RELATO", COLABORADOR, RELATO);
        postar(saida, "wamid.DUP-RELATO", corpo);
        mvc.perform(postAssinado(corpo));
        esperarLog(saida, "REPETIDA_IGNORADA");

        assertThat(repositorios.relatos()).hasSize(1);
        assertThat(enviadas()).hasSize(1);

        var relato = ultimoRelato();
        String toque = payloadBotao("wamid.DUP-BOTAO", COLABORADOR, "conf:" + relato.id() + ":sim", "Sim");
        postar(saida, "wamid.DUP-BOTAO", toque);
        mvc.perform(postAssinado(toque));
        Thread.sleep(300);
        assertThat(repositorios.alertas()).hasSize(1);
        assertThat(repositorios.entregas()).hasSize(1);
    }

    @Test
    void numeroSemONonoDigitoEncontraOCadastro(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+5511999990404");

        texto(saida, "551199990404", "oi");

        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.para()).as("responde no número cadastrado").isEqualTo("5511999990404");
            assertThat(e.texto()).contains("responda ACEITO");
        });
    }

    @Test
    void numeroComONonoDigitoEncontraCadastroSemEle(CapturedOutput saida) throws Exception {
        repositorios.cadastrar("+551133330505");

        texto(saida, "5511933330505", "oi");

        assertThat(enviadas()).singleElement().extracting("para").isEqualTo("551133330505");
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio", "image", "video", "sticker"})
    void tipoNaoSuportadoRecebeTextoProprioSemMudarOEstado(String tipo, CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        var estadoAntes = repositorios.estadoDe(colaborador.id());
        enviadas();

        postar(saida, "wamid.TIPO-" + tipo, payloadTipo("wamid.TIPO-" + tipo, COLABORADOR, tipo));

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.tipoNaoSuportado());
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(estadoAntes);
        assertThat(repositorios.relatos()).hasSize(1);
    }

    @Test
    void textoSoComEspacosNaoViraRelato(CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, "   ");

        assertThat(repositorios.relatos()).isEmpty();
        assertThat(classificador.recebidos()).isEmpty();
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.conviteRelato());
    }

    @Test
    void elogioSoEAgradecido(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria("ELOGIO"));

        texto(saida, "elogio ficticio a equipe");

        assertThat(repositorios.alertas()).isEmpty();
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.elogioRecebido());
    }

    @Test
    void responsavelQueEscreveForaDeAlertaRecebeOTextoCurtoEDesconhecidoEIgnorado(CapturedOutput saida)
            throws Exception {
        cenario(saida);

        texto(saida, GERENTE, "oi");
        texto(saida, OUTRO, "oi");

        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo(GERENTE);
            assertThat(e.texto()).isEqualTo(TextosConversa.responsavelForaDeAlerta());
        });
        assertThat(saida.getAll()).contains("REMETENTE_NAO_CADASTRADO");
    }

    @Test
    void notaSoValeQuandoEstaSozinha(CapturedOutput saida) throws Exception {
        cenario(saida);
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, "dormi 2 horas e estou exausto");

        assertThat(repositorios.checkins()).singleElement().extracting("humor").isNull();
        assertThat(repositorios.relatos()).hasSize(1);
    }

    /** Leva até um alerta enviado ao gerente e devolve o id do botão "Conversei". */
    private String alertaAoGerente(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        tocar(saida, COLABORADOR, "conf:" + ultimoRelato().id() + ":sim");
        List<Enviada> alerta = para(enviadas(), GERENTE);
        assertThat(alerta).hasSize(1);
        return alerta.get(0).botoes().get(0);
    }

    private static void esperarLog(CapturedOutput saida, String trecho) throws InterruptedException {
        for (int i = 0; i < 200 && !saida.getAll().contains(trecho); i++) {
            Thread.sleep(25);
        }
        assertThat(saida.getAll()).contains(trecho);
    }
}
