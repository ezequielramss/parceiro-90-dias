package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.alerta.Responsavel;
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

/**
 * Fluxo completo da Etapa 1 pelo canal Evolution (Evolution falsa), com repositórios em memória e
 * classificador falso. As opções são digitadas: SIM ou NÃO na confirmação, CONVERSEI ou NÃO
 * CONSEGUI no alerta.
 */
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
            assertThat(e.pedeConfirmacao()).isTrue();
        });
        assertThat(repositorios.alertas()).as("nada sai antes do sim").isEmpty();

        texto(saida, "SIM");
        var depoisDoSim = enviadas();
        var alertaGerente = para(depoisDoSim, GERENTE);
        assertThat(alertaGerente).singleElement().satisfies(e -> {
            assertThat(e.texto()).contains("Loja: Loja Teste", "Turno: Noite", "Cansaço", ClassificadorFalso.ACAO)
                    .doesNotContain(ClassificadorFalso.MOTIVO, "Pessoa Ficticia", RELATO);
            assertThat(e.alerta()).isTrue();
        });
        assertThat(para(depoisDoSim, GERENTE_OUTRA_LOJA)).isEmpty();
        assertThat(para(depoisDoSim, RH)).isEmpty();
        assertThat(para(depoisDoSim, COLABORADOR)).singleElement().extracting("texto")
                .isEqualTo(TextosConversa.relatoEncaminhado());
        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");

        texto(saida, GERENTE, "Conversei!");
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ATENDIDO");
        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo(GERENTE);
            assertThat(e.texto()).isEqualTo(TextosConversa.respostaConversei());
        });
        assertThat(acoesAuditadas()).contains(
                "CHECKIN_ENVIADO", "RELATO_CONFIRMADO", "ALERTA_CRIADO", "ALERTA_ENVIADO", "TOQUE_CONVERSEI");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Não", "nao", "NÃO.", " não! "})
    void naoDescartaENadaSai(String resposta, CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        enviadas();

        texto(saida, resposta);

        assertThat(ultimoRelato().confirmacao()).isEqualTo("DESCARTADO");
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.nadaEnviado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sim", "Sim!", "SÍM", "  sim. "})
    void simComVariacoesEncaminha(String resposta, CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        enviadas();

        texto(saida, resposta);

        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");
        assertThat(repositorios.alertas()).hasSize(1);
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
        assertThat(enviadas()).singleElement().matches(Enviada::pedeConfirmacao);
        assertThat(repositorios.alertas()).isEmpty();
    }

    @Test
    void naoConseguiNaConfirmacaoNaoViraNao(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        enviadas();

        texto(saida, "NÃO CONSEGUI");

        assertThat(ultimoRelato().confirmacao()).as("não descartou").isEqualTo("AGUARDANDO");
        assertThat(ultimoRelato().texto()).isEqualTo("NÃO CONSEGUI");
        assertThat(repositorios.alertas()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sim", "não"})
    void simENaoForaDaConfirmacaoSeguemOFluxoNormal(String texto, CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, texto);

        assertThat(repositorios.relatos()).as("vira relato, como qualquer texto").singleElement()
                .extracting("texto").isEqualTo(texto);
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
            assertThat(e.alerta()).isTrue();
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

        texto(saida, "sim");

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).hasSize(1);
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(para(enviadas, GERENTE_OUTRA_LOJA)).isEmpty();
    }

    @Test
    void alertaAoRhLevaOMotivoEAoGerenteNao(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria("VONTADE_DE_SAIR"));
        texto(saida, RELATO);
        enviadas();

        texto(saida, "sim");

        var enviadas = enviadas();
        assertThat(para(enviadas, GERENTE)).singleElement().extracting("texto").asString()
                .doesNotContain(ClassificadorFalso.MOTIVO);
        assertThat(para(enviadas, RH)).singleElement().extracting("texto").asString()
                .contains(ClassificadorFalso.MOTIVO);
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
        alertaAoGerente(saida);

        texto(saida, GERENTE, "Não consegui.");
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("NAO_CONSEGUI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ABERTO");
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.respostaNaoConsegui());

        texto(saida, GERENTE, "CONVERSEI");
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ATENDIDO");
        enviadas();

        // Sem alerta aberto, CONVERSEI e NÃO CONSEGUI seguem o fluxo normal: texto curto de responsável.
        texto(saida, GERENTE, "conversei");
        texto(saida, GERENTE, "não consegui");
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(enviadas()).extracting("texto").containsOnly(TextosConversa.responsavelForaDeAlerta());
        assertThat(acoesAuditadas()).containsOnlyOnce("TOQUE_CONVERSEI", "TOQUE_NAO_CONSEGUI");
    }

    @Test
    void respostaDeOutroNumeroNaoMexeNoAlerta(CapturedOutput saida) throws Exception {
        alertaAoGerente(saida);

        texto(saida, RH, "CONVERSEI");
        texto(saida, OUTRO, "CONVERSEI");

        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isNull();
        assertThat(repositorios.alertas()).singleElement().extracting("estado").isEqualTo("ABERTO");
        assertThat(saida.getAll()).contains("RESPONSAVEL_FORA_DE_ALERTA", "REMETENTE_NAO_CADASTRADO");
    }

    @Test
    void converseiValeParaOAlertaMaisRecenteAindaAberto(CapturedOutput saida) throws Exception {
        alertaAoGerente(saida);
        texto(saida, RELATO + " de novo");
        texto(saida, "sim");
        enviadas();
        var entregas = repositorios.entregas();
        assertThat(entregas).hasSize(2);

        texto(saida, GERENTE, "CONVERSEI");

        assertThat(repositorios.entregas()).extracting("resposta").containsExactly(null, "CONVERSEI");
        texto(saida, GERENTE, "CONVERSEI");
        assertThat(repositorios.entregas()).extracting("resposta").containsExactly("CONVERSEI", "CONVERSEI");
    }

    @Test
    void mesmoTelefoneComoColaboradorGerenteERh(CapturedOutput saida) throws Exception {
        String po = "5511999990150";
        colaborador = repositorios.cadastrar("+" + po);
        repositorios.cadastrarResponsavel("+" + po, Responsavel.GERENTE, RepositoriosEmMemoria.LOJA);
        repositorios.cadastrarResponsavel("+" + po, Responsavel.RH, null);
        texto(saida, po, "aceito");
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, po, "3");
        assertThat(repositorios.checkins()).singleElement().extracting("humor").as("nota continua nota").isEqualTo(3);

        classificador.sempre(ClassificadorFalso.categoria("VONTADE_DE_SAIR"));
        texto(saida, po, RELATO);
        texto(saida, po, "SIM");
        var alertas = enviadas().stream().filter(Enviada::alerta).toList();
        assertThat(alertas).hasSize(2).allMatch(e -> e.para().equals(po));
        assertThat(alertas).anyMatch(e -> e.texto().contains(ClassificadorFalso.MOTIVO))
                .anyMatch(e -> !e.texto().contains(ClassificadorFalso.MOTIVO));

        texto(saida, po, "CONVERSEI");
        texto(saida, po, "NÃO CONSEGUI");
        assertThat(repositorios.entregas()).extracting("resposta")
                .containsExactlyInAnyOrder("CONVERSEI", "NAO_CONSEGUI");
        assertThat(repositorios.relatos()).as("as respostas ao alerta não viram relato").hasSize(1);
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
        assertThat(enviadas()).anyMatch(Enviada::pedeConfirmacao);

        texto(saida, "sim");

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).hasSize(1);
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(acoesAuditadas()).contains("ALERTA_LOJA_SEM_GERENTE");
    }

    @Test
    void falhaDeEnvioAoResponsavelRegistraFalha(CapturedOutput saida) throws Exception {
        cenario(saida);
        EVOLUTION.falharPara(GERENTE);
        texto(saida, RELATO);

        texto(saida, "sim");

        assertThat(repositorios.entregas()).singleElement().satisfies(e -> {
            assertThat(e.falhaEnvio()).isTrue();
            assertThat(e.wamid()).isNull();
        });
        assertThat(acoesAuditadas()).contains("ALERTA_FALHA_ENVIO");
        assertThat(repositorios.auditoria()).filteredOn(a -> a.acao().equals("ALERTA_FALHA_ENVIO"))
                .allSatisfy(a -> assertThat(a.objeto()).startsWith("entrega/"));

        // Entrega com falha não recebe a resposta digitada.
        texto(saida, GERENTE, "CONVERSEI");
        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isNull();
    }

    @Test
    void idRepetidoNaoDuplicaNada(CapturedOutput saida) throws Exception {
        cenario(saida);
        String corpo = payloadTexto("EVO-DUP-RELATO", COLABORADOR, RELATO);
        postar(saida, "EVO-DUP-RELATO", corpo);
        mvc.perform(postWebhook(corpo));
        esperarLog(saida, "REPETIDA_IGNORADA");

        assertThat(repositorios.relatos()).hasSize(1);
        assertThat(enviadas()).hasSize(1);

        String sim = payloadTexto("EVO-DUP-SIM", COLABORADOR, "sim");
        postar(saida, "EVO-DUP-SIM", sim);
        mvc.perform(postWebhook(sim));
        Thread.sleep(300);
        assertThat(repositorios.alertas()).hasSize(1);
        assertThat(repositorios.entregas()).hasSize(1);
        enviadas();

        String conversei = payloadTexto("EVO-DUP-CONV", GERENTE, "CONVERSEI");
        postar(saida, "EVO-DUP-CONV", conversei);
        mvc.perform(postWebhook(conversei));
        Thread.sleep(300);
        assertThat(acoesAuditadas()).containsOnlyOnce("TOQUE_CONVERSEI");
        assertThat(enviadas()).hasSize(1);
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

    @Test
    void responsavelSemONonoDigitoRespondeAoAlerta(CapturedOutput saida) throws Exception {
        alertaAoGerente(saida);
        String gerenteSem9 = GERENTE.substring(0, 4) + GERENTE.substring(5);

        texto(saida, gerenteSem9, "CONVERSEI");

        assertThat(repositorios.entregas()).singleElement().extracting("resposta").isEqualTo("CONVERSEI");
        assertThat(enviadas()).singleElement().extracting("para").isEqualTo(GERENTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio", "image", "video", "sticker"})
    void tipoNaoSuportadoRecebeTextoProprioSemMudarOEstado(String tipo, CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        var estadoAntes = repositorios.estadoDe(colaborador.id());
        enviadas();

        postar(saida, "EVO-TIPO-" + tipo, payloadTipo("EVO-TIPO-" + tipo, COLABORADOR, tipo));

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.tipoNaoSuportado());
        assertThat(repositorios.estadoDe(colaborador.id())).isEqualTo(estadoAntes);
        assertThat(repositorios.relatos()).hasSize(1);
    }

    @Test
    void textoEstendidoTambemELido(CapturedOutput saida) throws Exception {
        cenario(saida);

        postar(saida, "EVO-EXT-1", payloadTextoEstendido("EVO-EXT-1", COLABORADOR, RELATO));

        assertThat(repositorios.relatos()).singleElement().extracting("texto").isEqualTo(RELATO);
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

    @ParameterizedTest
    @ValueSource(strings = {"1", "5", "nota 4"})
    void notaDe1A5ContinuaSendoNotaNoCheckin(String nota, CapturedOutput saida) throws Exception {
        cenario(saida);
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, nota);

        assertThat(repositorios.checkins()).singleElement().extracting("humor")
                .isEqualTo(Integer.parseInt(nota.replace("nota ", "")));
        assertThat(repositorios.relatos()).isEmpty();
    }

    /** Leva até um alerta enviado ao gerente. */
    private void alertaAoGerente(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, RELATO);
        texto(saida, "sim");
        List<Enviada> alerta = para(enviadas(), GERENTE);
        assertThat(alerta).hasSize(1);
    }

    private static void esperarLog(CapturedOutput saida, String trecho) throws InterruptedException {
        for (int i = 0; i < 200 && !saida.getAll().contains(trecho); i++) {
            Thread.sleep(25);
        }
        assertThat(saida.getAll()).contains(trecho);
    }
}
