package br.com.kallopolli.parceiro.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TextosConversaTest {

    // Dados fictícios.
    private static final String NOME = "Pessoa Ficticia";
    private static final String MOTIVO = "Relato ficticio de cansaço depois de três fechamentos seguidos.";
    private static final String TEXTO_RELATO = "texto ficticio do relato que nunca vai em alerta";
    private static final String ACAO = "Conversar sobre a escala da semana.";
    private static final String LONGO = "x".repeat(5_000);

    private static final List<String> CATEGORIAS = List.of(
            "CANSACO", "SOBRECARGA", "DESMOTIVACAO", "DUVIDA", "CONFLITO_COLEGA", "VONTADE_DE_SAIR",
            "CONFLITO_CHEFIA", "ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE", "ELOGIO", "NAO_CLASSIFICADO");

    private static final List<String> PROIBIDOS = List.of(
            "100%", "depress", "ansiedade", "transtorno", "diagnóstico", "diagnostico", "doença mental",
            "doenca mental", "monitor", "vigi");

    /** Todos os textos que o assistente pode montar, com dados fictícios. */
    static List<String> todosOsTextos() {
        List<String> textos = new ArrayList<>(List.of(
                TextosConversa.boasVindas(),
                TextosConversa.conviteRelato(),
                TextosConversa.relatoEncaminhado(),
                TextosConversa.nadaEnviado(),
                TextosConversa.relatoUrgente(),
                TextosConversa.iaIndisponivel(),
                TextosConversa.elogioRecebido(),
                TextosConversa.tipoNaoSuportado(),
                TextosConversa.respostaConversei(),
                TextosConversa.respostaNaoConsegui(),
                TextosConversa.responsavelForaDeAlerta(),
                TextosConversa.menu(List.of("1 - " + TextosConversa.NOME_AGENTE_RELATO, "2 - " + TextosConversa.NOME_AGENTE_ELOGIO)),
                TextosConversa.menuCurto(List.of("1 - " + TextosConversa.NOME_AGENTE_RELATO, "2 - " + TextosConversa.NOME_AGENTE_ELOGIO)),
                TextosConversa.menuVazio(),
                TextosConversa.aberturaRelato(),
                TextosConversa.aberturaElogio(),
                TextosConversa.alertaRhSemClassificacao("Loja Teste", "Noite")));
        for (int dia = 1; dia <= 10; dia++) {
            textos.add(TextosConversa.checkin(dia, "Pessoa"));
            textos.add(TextosConversa.checkin(dia, null));
        }
        for (int nota = 1; nota <= 5; nota++) {
            textos.add(TextosConversa.respostaNota(nota));
        }
        for (String destino : List.of("GERENTE", "RH", "GERENTE_E_RH")) {
            textos.add(TextosConversa.confirmacaoRelato(MOTIVO, destino));
        }
        for (String categoria : CATEGORIAS) {
            textos.add(TextosConversa.alertaGerente("Loja Teste", "Noite", categoria, ACAO));
            textos.add(TextosConversa.alertaRh("Loja Teste", "Noite", categoria, MOTIVO, ACAO));
            textos.add(TextosConversa.alertaRhUrgente("Loja Teste", "Noite", categoria, MOTIVO, ACAO));
            textos.add(TextosConversa.rotuloCategoria(categoria));
        }
        return textos;
    }

    /** Textos com campos variáveis: até 1024 mesmo com entradas enormes, para provar o corte. */
    static List<String> corposLongos() {
        return List.of(
                TextosConversa.confirmacaoRelato(LONGO, "GERENTE_E_RH"),
                TextosConversa.alertaGerente(LONGO, LONGO, "ASSEDIO", LONGO),
                TextosConversa.alertaRh(LONGO, LONGO, "ASSEDIO", LONGO, LONGO),
                TextosConversa.alertaRhUrgente(LONGO, LONGO, "SITUACAO_GRAVE", LONGO, LONGO),
                TextosConversa.alertaRhSemClassificacao(LONGO, LONGO));
    }

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void textoCabeEmMilCaracteres(String texto) {
        assertThat(texto.length()).isLessThanOrEqualTo(1000);
    }

    @ParameterizedTest
    @MethodSource("corposLongos")
    void corpoCabeEm1024MesmoComEntradaEnorme(String corpo) {
        assertThat(corpo.length()).isLessThanOrEqualTo(1024);
    }

    @Test
    void opcoesVaoEscritasNoTextoSemBotoes() {
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "GERENTE"))
                .endsWith("Responda SIM para enviar ou NÃO para descartar.");
        assertThat(List.of(
                TextosConversa.alertaGerente("Loja Teste", "Noite", "CANSACO", ACAO),
                TextosConversa.alertaRh("Loja Teste", "Noite", "CANSACO", MOTIVO, ACAO),
                TextosConversa.alertaRhUrgente("Loja Teste", "Noite", "ASSEDIO", MOTIVO, ACAO),
                TextosConversa.alertaRhSemClassificacao("Loja Teste", "Noite")))
                .allSatisfy(t -> assertThat(t).endsWith("responda CONVERSEI ou NÃO CONSEGUI."));
        assertThat(TextosConversa.respostaNaoConsegui()).contains("responda CONVERSEI");
        assertThat(TextosConversa.responsavelForaDeAlerta()).contains("responder CONVERSEI ou NÃO CONSEGUI");
    }

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void nenhumTextoFalaEmBotaoOuToque(String texto) {
        assertThat(texto.toLowerCase(Locale.ROOT)).doesNotContain("botão", "botões", "toque", "janela de 24");
    }

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void semPalavrasProibidas(String texto) {
        String minusculo = texto.toLowerCase(Locale.ROOT);
        assertThat(PROIBIDOS).noneMatch(minusculo::contains);
    }

    /** Palavras flexionadas no masculino ou no feminino quando dirigidas ao colaborador. */
    private static final Pattern GENERO = Pattern.compile(
            "\\b(obrigad[oa]s?|bem-vind[oa]s?|tranquil[oa]s?|sozinh[oa]s?|cansad[oa]s?|preocupad[oa]s?"
                    + "|animad[oa]s?|acolhid[oa]s?|seguro|segura|sozinh[oa])\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void linguagemNeutra(String texto) {
        assertThat(GENERO.matcher(texto).find()).as("palavra com gênero em: %s", texto).isFalse();
    }

    @Test
    void documentoDeRevisaoTambemUsaLinguagemNeutra() throws IOException {
        String doc = Files.readString(Path.of("..", "docs", "textos-conversa.md"), StandardCharsets.UTF_8);
        // A seção de regras cita os exemplos proibidos entre aspas; os textos ficam nos blocos ```text.
        Matcher blocos = Pattern.compile("```text\\n(.*?)```", Pattern.DOTALL).matcher(doc.replace("\r\n", "\n"));
        int encontrados = 0;
        while (blocos.find()) {
            encontrados++;
            assertThat(GENERO.matcher(blocos.group(1)).find()).as("bloco: %s", blocos.group(1)).isFalse();
        }
        assertThat(encontrados).isGreaterThan(10);
    }

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void anonimoSoAparecePrecedidoDeNao(String texto) {
        Matcher m = Pattern.compile("an[oô]nim", Pattern.CASE_INSENSITIVE).matcher(texto);
        while (m.find()) {
            String antes = texto.substring(0, m.start()).toLowerCase(Locale.ROOT);
            assertThat(antes).as("antes de \"anônimo\" em: %s", texto).endsWith("não ");
        }
    }

    @ParameterizedTest
    @MethodSource("todosOsTextos")
    void noMaximoUmEmoji(String texto) {
        assertThat(emojis(texto)).isLessThanOrEqualTo(1);
    }

    @Test
    void mensagensGravesNaoTemEmoji() {
        assertThat(List.of(
                TextosConversa.relatoUrgente(),
                TextosConversa.alertaRhUrgente("Loja Teste", "Noite", "SITUACAO_GRAVE", MOTIVO, ACAO),
                TextosConversa.respostaNota(1),
                TextosConversa.respostaNota(2)))
                .allSatisfy(t -> assertThat(emojis(t)).isZero());
    }

    @Test
    void relatoUrgenteNaoTemTelefoneNemOrientacaoDeEmergencia() {
        assertThat(TextosConversa.relatoUrgente()).doesNotContainPattern("\\d").doesNotContainIgnoringCase("ligue");
    }

    @Test
    void privacidadeDizConfidencialNaoAnonimo() {
        assertThat(TextosConversa.boasVindas()).contains("confidencial, não anônimo");
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "RH")).contains("confidencial, não anônimo");
    }

    @Test
    void boasVindasExplicaQuemVeEComoParar() {
        assertThat(TextosConversa.boasVindas()).contains("voluntário", "o RH e o gerente", "PARAR");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10})
    void checkinPedeNotaDe1A5ComAEscala(int dia) {
        assertThat(TextosConversa.checkin(dia, "Pessoa"))
                .contains("nota de 1 a 5", "1 = muito mal", "5 = muito bem");
    }

    @Test
    void checkinDoDia1SeApresentaEOsDemaisNao() {
        assertThat(TextosConversa.checkin(1, "Pessoa")).contains("Oi, Pessoa!", "Parceiro dos 90 dias");
        assertThat(TextosConversa.checkin(2, "Pessoa")).doesNotContain("Parceiro dos 90 dias");
        assertThat(TextosConversa.checkin(3, " ")).startsWith("Oi!");
    }

    @Test
    void checkinForaDosDias1A10ENotaForaDe1A5SaoRecusados() {
        assertThatThrownBy(() -> TextosConversa.checkin(0, "Pessoa")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextosConversa.checkin(11, "Pessoa")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextosConversa.respostaNota(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notasBaixasConvidamSemPrometerAviso() {
        String texto = TextosConversa.respostaNota(1).toLowerCase(Locale.ROOT);
        assertThat(texto).contains("se quiser").doesNotContain("avis", "gerente", "rh");
    }

    @Test
    void confirmacaoMostraMotivoEDizParaQuem() {
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "GERENTE")).contains(MOTIVO, "o gerente da sua loja");
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "RH")).contains("para o RH");
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "GERENTE_E_RH")).contains("o gerente da sua loja e o RH");
        assertThat(TextosConversa.confirmacaoRelato(MOTIVO, "RH")).contains("escrever de novo");
        assertThatThrownBy(() -> TextosConversa.confirmacaoRelato(MOTIVO, "AGREGADO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alertaAoGerenteNaoTemMotivoNomeNemTextoDoRelato() {
        String alerta = TextosConversa.alertaGerente("Loja Teste", "Noite", "SOBRECARGA", ACAO);
        assertThat(alerta)
                .contains("Loja Teste", "Noite", "Sobrecarga de trabalho", ACAO)
                .doesNotContain(MOTIVO, NOME, TEXTO_RELATO, "Resumo:");
    }

    @Test
    void alertaAoRhTemMotivoEAvisoDeIdentificacaoSemNomeNemTexto() {
        for (String alerta : List.of(
                TextosConversa.alertaRh("Loja Teste", "Noite", "ASSEDIO", MOTIVO, ACAO),
                TextosConversa.alertaRhUrgente("Loja Teste", "Noite", "ASSEDIO", MOTIVO, ACAO))) {
            assertThat(alerta)
                    .contains(MOTIVO, "Loja Teste", "Noite", "Assédio", "podem permitir identificar a pessoa")
                    .doesNotContain(NOME, TEXTO_RELATO);
        }
        assertThat(TextosConversa.alertaRhUrgente("L", "T", "ASSEDIO", MOTIVO, ACAO)).startsWith("URGENTE");
        assertThat(TextosConversa.alertaRhSemClassificacao("Loja Teste", null))
                .contains("Turno: não informado", "podem permitir identificar a pessoa");
    }

    @Test
    void converseiNaoEResolvido() {
        assertThat(TextosConversa.respostaConversei()).contains("contato realizado", "até o RH ou o gestor marcar como resolvido");
        assertThat(TextosConversa.respostaNaoConsegui()).contains("continua pendente");
    }

    @Test
    void asDozeCategoriasTemRotulo() {
        assertThat(TextosConversa.rotulos()).containsOnlyKeys(CATEGORIAS);
        assertThat(CATEGORIAS).allSatisfy(c ->
                assertThat(TextosConversa.rotuloCategoria(c)).isNotBlank().isNotEqualTo("Assunto não identificado"));
        assertThat(TextosConversa.rotuloCategoria("OUTRA")).isEqualTo("Assunto não identificado");
    }

    @Test
    void documentoDeRevisaoTemOsMesmosTextos() throws IOException {
        String doc = Files.readString(Path.of("..", "docs", "textos-conversa.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertThat(List.of(
                TextosConversa.boasVindas(),
                TextosConversa.checkin(1, "{primeiroNome}"),
                TextosConversa.checkin(2, "{primeiroNome}"),
                TextosConversa.respostaNota(1),
                TextosConversa.respostaNota(3),
                TextosConversa.respostaNota(4),
                TextosConversa.conviteRelato(),
                TextosConversa.relatoEncaminhado(),
                TextosConversa.nadaEnviado(),
                TextosConversa.relatoUrgente(),
                TextosConversa.iaIndisponivel(),
                TextosConversa.elogioRecebido(),
                TextosConversa.tipoNaoSuportado(),
                TextosConversa.respostaConversei(),
                TextosConversa.respostaNaoConsegui(),
                TextosConversa.responsavelForaDeAlerta(),
                TextosConversa.OPCOES_CONFIRMACAO,
                TextosConversa.NOME_AGENTE_RELATO,
                TextosConversa.NOME_AGENTE_ELOGIO,
                TextosConversa.menu(List.of("1 - " + TextosConversa.NOME_AGENTE_RELATO, "2 - " + TextosConversa.NOME_AGENTE_ELOGIO)),
                TextosConversa.menuCurto(List.of("1 - " + TextosConversa.NOME_AGENTE_RELATO, "2 - " + TextosConversa.NOME_AGENTE_ELOGIO)),
                TextosConversa.menuVazio(),
                TextosConversa.aberturaRelato(),
                TextosConversa.aberturaElogio(),
                TextosConversa.alertaRhSemClassificacao("{loja}", "{turno}")))
                .allSatisfy(texto -> assertThat(doc).as("docs/textos-conversa.md").contains(texto));
        TextosConversa.rotulos().forEach((codigo, rotulo) ->
                assertThat(doc).contains("| " + codigo + " | " + rotulo + " |"));
    }

    private static long emojis(String texto) {
        return texto.codePoints().filter(cp -> Character.getType(cp) == Character.OTHER_SYMBOL).count();
    }
}
