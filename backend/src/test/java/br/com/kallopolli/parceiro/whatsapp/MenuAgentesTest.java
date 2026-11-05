package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.classificacao.ClassificadorFalso;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.RepositoriosEmMemoria;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Menu de agentes: LIVRE, escolha, EM_AGENTE, expiração, precedência, Relato e Elogio. */
@ExtendWith(OutputCaptureExtension.class)
class MenuAgentesTest extends FluxoTestBase {

    private static final String RELATO = "relato ficticio de cansaco no fechamento";
    private static final String MENU = "1 - " + TextosConversa.NOME_AGENTE_RELATO + "\n2 - " + TextosConversa.NOME_AGENTE_ELOGIO
            + "\n\n" + TextosConversa.avisoUrgencia(1);
    private static final String AVISO_LONGO = TextosConversa.avisoTextoLongo(1);
    private static final int LIMITE = 120;

    private EstadoConversa estado() {
        return repositorios.estadoDe(colaborador.id());
    }

    // LIVRE

    @ParameterizedTest
    @ValueSource(strings = {"oi", "ok", "bom dia", "   ", "menu", "voltar", "sim", "não",
            "um texto bem longo contando que o gerente grita com todo mundo no fechamento e ninguem faz nada"})
    void qualquerTextoEmLivreDevolveOMenuSemRelatoNemClassificador(String texto, CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();

        texto(saida, texto);

        assertThat(enviadas()).singleElement().satisfies(e -> {
            assertThat(e.texto()).startsWith("Oi! Eu sou o Parceiro.").contains(TextosConversa.PEDIDO_NUMERO).endsWith(MENU);
        });
        assertThat(repositorios.relatos()).isEmpty();
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(classificador.recebidos()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
    }

    // Escolha

    @ParameterizedTest
    @ValueSource(strings = {"1", " 1. ", "Contar algo", "CONTAR ALGO!", "relato", "Reclamação",
            "contar algo (relato ou reclamação)"})
    void escolheORelatoPeloNumeroOuPeloNome(String escolha, CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, escolha);

        assertThat(estado()).isEqualTo(EstadoConversa.emAgente("RELATO"));
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.aberturaRelato());
        assertThat(repositorios.relatos()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "Fazer um elogio", "FAZER UM ELOGIO.", "elógio"})
    void escolheOElogioPeloNumeroOuPeloNome(String escolha, CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, escolha);

        assertThat(estado()).isEqualTo(EstadoConversa.emAgente("ELOGIO"));
        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.aberturaElogio());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", "0", "12"})
    void opcaoInvalidaRepeteOMenuCurto(String opcao, CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, opcao);

        assertThat(enviadas()).singleElement().extracting("texto").asString()
                .startsWith("Não encontrei essa opção.").endsWith(MENU);
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
    }

    // EM_AGENTE

    @ParameterizedTest
    @ValueSource(strings = {"menu", "MENU!", "voltar", "Voltar.", "0"})
    void menuVoltarEZeroSaemDoAgente(String saidaDoAgente, CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();
        texto(saida, "1");
        enviadas();

        texto(saida, saidaDoAgente);

        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
        assertThat(repositorios.relatos()).isEmpty();
    }

    @Test
    void depoisDe30MinutosOAgenteExpiraEOTextoRecebeOMenu(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();
        texto(saida, "1");
        enviadas();
        repositorios.envelhecerEstado(colaborador.id(), Duration.ofMinutes(31));

        texto(saida, RELATO);

        assertThat(repositorios.relatos()).isEmpty();
        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    @Test
    void antesDos30MinutosOAgenteContinua(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "1");
        repositorios.envelhecerEstado(colaborador.id(), Duration.ofMinutes(29));

        texto(saida, RELATO);

        assertThat(repositorios.relatos()).hasSize(1);
    }

    @Test
    void mensagemEmBrancoNoAgenteRenovaOPrazo(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "1");
        repositorios.envelhecerEstado(colaborador.id(), Duration.ofMinutes(29));

        texto(saida, "   ");
        repositorios.envelhecerEstado(colaborador.id(), Duration.ofMinutes(29));
        texto(saida, RELATO);

        assertThat(repositorios.relatos()).hasSize(1);
    }

    @Test
    void pararLimpaOAgenteAtivo(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "2");
        assertThat(estado().agente()).isEqualTo("ELOGIO");

        texto(saida, "PARAR");

        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(repositorios.consentimentos()).extracting("ativo").containsExactly(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "voltar", "0"})
    void menuDuranteAConfirmacaoDescartaAvisaEVoltaAoMenu(String saidaDoAgente, CapturedOutput saida) throws Exception {
        cenario(saida);
        contar(saida, RELATO);
        enviadas();

        texto(saida, saidaDoAgente);

        assertThat(ultimoRelato().confirmacao()).isEqualTo("DESCARTADO");
        assertThat(ultimoRelato().texto()).isEqualTo(RELATO);
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        var respostas = enviadas();
        assertThat(respostas).extracting("texto").hasSize(2).first().isEqualTo(TextosConversa.nadaEnviado());
        assertThat(respostas.get(1).texto()).as("o aviso vem antes do menu").endsWith(MENU);
        assertThat(repositorios.auditoria()).filteredOn(a -> a.acao().equals("RELATO_DESCARTADO"))
                .singleElement().satisfies(a -> assertThat(a.objeto()).isEqualTo("relato/" + ultimoRelato().id()));
    }

    @Test
    void depoisDoRelatoConcluidoVoltaALivre(CapturedOutput saida) throws Exception {
        cenario(saida);
        contar(saida, RELATO);
        texto(saida, "sim");
        enviadas();
        classificador.proibir();

        texto(saida, "agradeco");

        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    // Precedência

    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    void numeroDoMenuNoCheckinEEhNota(String nota, CapturedOutput saida) throws Exception {
        cenario(saida);
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, nota);

        assertThat(repositorios.checkins()).singleElement().extracting("humor").isEqualTo(Integer.parseInt(nota));
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
    }

    @Test
    void textoQueNaoENotaNoCheckinRecebeOMenuSemRelato(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, "estou com um problema com o gerente");

        assertThat(repositorios.checkins()).singleElement().extracting("humor").isNull();
        assertThat(repositorios.relatos()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    @Test
    void numeroDoMenuDentroDoAgenteEEhTextoDoAgente(CapturedOutput saida) throws Exception {
        cenario(saida);
        contar(saida, "2");

        assertThat(repositorios.relatos()).singleElement().extracting("texto").isEqualTo("2");
        assertThat(classificador.recebidos()).containsExactly("2");
    }

    @Test
    void numeroDoMenuNaConfirmacaoNaoEscolheOpcao(CapturedOutput saida) throws Exception {
        cenario(saida);
        contar(saida, RELATO);

        texto(saida, "2");

        assertThat(estado().tipo()).isEqualTo(EstadoConversa.Tipo.AGUARDANDO_CONFIRMACAO_RELATO);
        assertThat(ultimoRelato().texto()).as("reclassifica, como qualquer texto").isEqualTo("2");
    }

    @Test
    void mesmoTelefoneColaboradorGerenteERhSemColisao(CapturedOutput saida) throws Exception {
        String po = "5511999990160";
        colaborador = repositorios.cadastrar("+" + po);
        repositorios.cadastrarResponsavel("+" + po, Responsavel.GERENTE, RepositoriosEmMemoria.LOJA);
        repositorios.cadastrarResponsavel("+" + po, Responsavel.RH, null);
        texto(saida, po, "aceito");
        classificador.sempre(ClassificadorFalso.categoria("VONTADE_DE_SAIR"));

        // Menu, relato e SIM como colaborador: os alertas voltam para o mesmo número.
        contar(saida, po, RELATO);
        texto(saida, po, "SIM");
        assertThat(repositorios.entregas()).hasSize(2);
        enviadas();

        // Check-in chega com alerta aberto: o número é nota, não opção do menu nem resposta ao alerta.
        agendador(RepositoriosEmMemoria.INICIO).tick();
        texto(saida, po, "2");
        assertThat(repositorios.checkins()).singleElement().extracting("humor").isEqualTo(2);
        assertThat(repositorios.entregas()).extracting("resposta").containsOnlyNulls();

        // Em LIVRE, o mesmo número é opção do menu.
        texto(saida, po, "2");
        assertThat(estado()).isEqualTo(EstadoConversa.emAgente("ELOGIO"));

        // CONVERSEI e NÃO CONSEGUI vêm antes de tudo, mesmo dentro de um agente.
        texto(saida, po, "CONVERSEI");
        texto(saida, po, "NÃO CONSEGUI");
        assertThat(repositorios.entregas()).extracting("resposta")
                .containsExactlyInAnyOrder("CONVERSEI", "NAO_CONSEGUI");
        assertThat(repositorios.relatos()).as("as respostas ao alerta não viram texto do agente").hasSize(1);
        assertThat(estado()).isEqualTo(EstadoConversa.emAgente("ELOGIO"));
    }

    @Test
    void checkinNaoInterrompeQuemEstaNoAgenteEChegaDepoisDeExpirar(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "1");
        var agendador = agendador(RepositoriosEmMemoria.INICIO);
        var agora = RepositoriosEmMemoria.INICIO.atTime(10, 0).atZone(SP).toInstant();

        repositorios.marcarEstadoAtualizadoEm(colaborador.id(), agora.minus(Duration.ofMinutes(10)));
        agendador.tick();
        assertThat(repositorios.checkins()).as("ainda dentro do prazo").isEmpty();

        repositorios.marcarEstadoAtualizadoEm(colaborador.id(), agora.minus(Duration.ofMinutes(31)));
        agendador.tick();
        assertThat(repositorios.checkins()).hasSize(1);
    }

    // Texto longo

    private static String textoDe(int caracteres) {
        return "x".repeat(caracteres);
    }

    @Test
    void textoLongoEmLivreRecebeOAvisoSemRelatoNemClassificador(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();

        texto(saida, textoDe(LIMITE + 1));

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(AVISO_LONGO);
        assertThat(repositorios.relatos()).isEmpty();
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(classificador.recebidos()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(saida.getAll()).contains("TEXTO_LONGO").doesNotContain(textoDe(LIMITE + 1));
    }

    @Test
    void textoNoLimiteAindaRecebeSoOMenu(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();

        texto(saida, textoDe(LIMITE));

        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    @Test
    void espacosNasPontasNaoContamParaOLimite(CapturedOutput saida) throws Exception {
        cenario(saida);

        texto(saida, "   " + textoDe(LIMITE) + "   ");

        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    @Test
    void textoLongoNoCheckinRecebeOAvisoEVoltaALivre(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, textoDe(LIMITE + 1));

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(AVISO_LONGO);
        assertThat(repositorios.checkins()).singleElement().extracting("humor").isNull();
        assertThat(repositorios.relatos()).isEmpty();
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
    }

    @Test
    void textoNoLimiteNoCheckinRecebeSoOMenu(CapturedOutput saida) throws Exception {
        cenario(saida);
        agendador(RepositoriosEmMemoria.INICIO).tick();
        enviadas();

        texto(saida, textoDe(LIMITE));

        assertThat(enviadas()).singleElement().extracting("texto").asString().endsWith(MENU);
    }

    @Test
    void depoisDoAvisoAPessoaEscolheEContaDeNovo(CapturedOutput saida) throws Exception {
        cenario(saida);
        String longo = textoDe(LIMITE + 1);
        texto(saida, longo);
        enviadas();
        assertThat(repositorios.relatos()).isEmpty();

        contar(saida, longo);

        assertThat(repositorios.relatos()).singleElement().extracting("texto").isEqualTo(longo);
        assertThat(enviadas()).singleElement().matches(Enviada::pedeConfirmacao);
    }

    @Test
    void dentroDoAgenteTextoLongoEConteudoDoAgente(CapturedOutput saida) throws Exception {
        cenario(saida);
        String longo = textoDe(LIMITE + 50);

        contar(saida, longo);

        assertThat(repositorios.relatos()).singleElement().extracting("texto").isEqualTo(longo);
        assertThat(classificador.recebidos()).containsExactly(longo);
        assertThat(enviadas()).singleElement().matches(Enviada::pedeConfirmacao);
    }

    @Test
    void textoLongoNaConfirmacaoReclassificaSemAviso(CapturedOutput saida) throws Exception {
        cenario(saida);
        contar(saida, RELATO);
        enviadas();
        String longo = textoDe(LIMITE + 50);

        texto(saida, longo);

        assertThat(ultimoRelato().texto()).isEqualTo(longo);
        assertThat(enviadas()).singleElement().matches(Enviada::pedeConfirmacao);
    }

    // Elogio

    @Test
    void elogioComumAgradeceSemAlertaEContaComoElogio(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria("ELOGIO"));
        texto(saida, "2");
        enviadas();

        texto(saida, "elogio ficticio a equipe");

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.elogioRecebido());
        assertThat(repositorios.alertas()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
        assertThat(ultimoRelato().classificacao().categoria()).as("os indicadores contam por aqui").isEqualTo("ELOGIO");
        assertThat(ultimoRelato().confirmacao()).isEqualTo("CONFIRMADO");
        assertThat(classificador.recebidos()).containsExactly("elogio ficticio a equipe");
    }

    @Test
    void elogioComProblemaGraveVaiAoRhComoUrgente(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.sempre(ClassificadorFalso.categoria("ASSEDIO"));
        texto(saida, "2");
        enviadas();

        texto(saida, "elogio ficticio que esconde um problema");

        var enviadas = enviadas();
        assertThat(para(enviadas, RH)).singleElement().extracting("texto").asString().startsWith("URGENTE");
        assertThat(para(enviadas, GERENTE)).isEmpty();
        assertThat(para(enviadas, COLABORADOR)).singleElement().extracting("texto")
                .isEqualTo(TextosConversa.relatoUrgente());
        assertThat(estado()).isEqualTo(EstadoConversa.LIVRE);
    }

    @Test
    void elogioQueEOutraCoisaSegueAConfirmacaoDoRelato(CapturedOutput saida) throws Exception {
        cenario(saida);
        texto(saida, "2");
        enviadas();

        texto(saida, "elogio ficticio que e cansaco");

        assertThat(enviadas()).singleElement().matches(Enviada::pedeConfirmacao);
        assertThat(repositorios.alertas()).as("nada sai antes do sim").isEmpty();
        assertThat(estado().tipo()).isEqualTo(EstadoConversa.Tipo.AGUARDANDO_CONFIRMACAO_RELATO);

        texto(saida, "SIM");
        assertThat(repositorios.alertas()).hasSize(1);
    }

    @Test
    void elogioEmBrancoPedeDeNovoENaoGravaRelato(CapturedOutput saida) throws Exception {
        cenario(saida);
        classificador.proibir();
        texto(saida, "2");
        enviadas();

        texto(saida, "   ");

        assertThat(enviadas()).singleElement().extracting("texto").isEqualTo(TextosConversa.aberturaElogio());
        assertThat(repositorios.relatos()).isEmpty();
        assertThat(estado()).isEqualTo(EstadoConversa.emAgente("ELOGIO"));
    }
}
