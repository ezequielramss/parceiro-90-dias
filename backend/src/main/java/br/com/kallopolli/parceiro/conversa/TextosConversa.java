package br.com.kallopolli.parceiro.conversa;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todos os textos que o assistente envia na conversa da Etapa 1 (H03, H04, H16, H17, H37).
 *
 * <p>Os mesmos textos estão em docs/textos-conversa.md para revisão do PO, do RH e do jurídico.
 * O termo de aceite v1 continua em consentimento/Textos.java.
 *
 * <p>Regras de tom: português do Brasil, "você", curto, no máximo um emoji e nenhum nas mensagens
 * graves; nada de diagnóstico, pontuação ou vigilância; o canal é "confidencial, não anônimo".
 * Os alertas nunca recebem o nome do colaborador nem o texto do relato; o do gerente também não
 * recebe o motivo.
 */
public final class TextosConversa {

    private TextosConversa() {}

    /** Rótulos de botão: no máximo 20 caracteres (limite do WhatsApp). */
    public static final String BOTAO_ENVIAR = "Sim, pode enviar";
    public static final String BOTAO_NAO_ENVIAR = "Não enviar";
    public static final String BOTAO_CONVERSEI = "Conversei";
    public static final String BOTAO_NAO_CONSEGUI = "Não consegui";

    private static final String ESCALA = "1 = muito mal, 2 = mal, 3 = mais ou menos, 4 = bem, 5 = muito bem";
    private static final int MAX_CAMPO_CURTO = 60;
    private static final int MAX_CAMPO_LONGO = 300;

    private static final Map<String, String> ROTULOS = new LinkedHashMap<>();

    static {
        ROTULOS.put("CANSACO", "Cansaço");
        ROTULOS.put("SOBRECARGA", "Sobrecarga de trabalho");
        ROTULOS.put("DESMOTIVACAO", "Desmotivação");
        ROTULOS.put("DUVIDA", "Dúvida sobre rotina ou benefícios");
        ROTULOS.put("CONFLITO_COLEGA", "Conflito com colega");
        ROTULOS.put("VONTADE_DE_SAIR", "Vontade de sair da empresa");
        ROTULOS.put("CONFLITO_CHEFIA", "Conflito com a chefia");
        ROTULOS.put("ASSEDIO", "Assédio");
        ROTULOS.put("HUMILHACAO", "Humilhação ou constrangimento");
        ROTULOS.put("SITUACAO_GRAVE", "Situação grave");
        ROTULOS.put("ELOGIO", "Elogio");
        ROTULOS.put("NAO_CLASSIFICADO", "Assunto não classificado");
    }

    // 1. Boas-vindas depois do aceite (H03)

    public static String boasVindas() {
        return """
                Que bom ter você por aqui! 👋 Eu sou o Parceiro dos 90 dias, o assistente que \
                acompanha seus primeiros 90 dias na empresa pelo WhatsApp.

                De vez em quando vou perguntar como você está, com uma nota de 1 a 5. Se quiser, \
                você também pode me contar o que está acontecendo, a qualquer hora.

                Participar é voluntário. Este canal é confidencial, não anônimo: o RH e o gerente \
                da sua loja veem a nota de ânimo que você informar.

                Para parar a qualquer momento, escreva PARAR.""";
    }

    // 2. Check-in (H04)

    /** Dia 1 se apresenta; dias 2 a 10 são mais curtos. Os dois pedem nota de 1 a 5. */
    public static String checkin(int dia, String primeiroNome) {
        if (dia < 1 || dia > 10) {
            throw new IllegalArgumentException("check-in só existe nos dias 1 a 10");
        }
        String saudacao = primeiroNome == null || primeiroNome.isBlank()
                ? "Oi!"
                : "Oi, " + limitar(primeiroNome.strip(), MAX_CAMPO_CURTO) + "!";
        if (dia == 1) {
            return saudacao + " Aqui é o Parceiro dos 90 dias. Hoje começa a sua jornada e eu "
                    + "queria saber: como você está?\n\n"
                    + "Responda com uma nota de 1 a 5 (" + ESCALA + ").";
        }
        return saudacao + " Como você está hoje?\n\n"
                + "Responda com uma nota de 1 a 5 (" + ESCALA + ").";
    }

    // 3. Resposta à nota

    public static String respostaNota(int nota) {
        return switch (nota) {
            case 1, 2 -> "Obrigado por contar. Sinto muito que hoje não esteja sendo um bom dia. "
                    + "Se quiser, me conte com suas palavras o que está acontecendo. "
                    + "Se preferir não falar agora, tudo bem.";
            case 3 -> "Obrigado por responder. Se quiser contar algo sobre o seu dia, é só escrever.";
            case 4, 5 -> "Que bom saber disso! 🙂 Obrigado por responder.";
            default -> throw new IllegalArgumentException("a nota vai de 1 a 5");
        };
    }

    // 4. Confirmação do relato (H16)

    /**
     * @param motivo  resumo neutro gerado na classificação
     * @param destino GERENTE, RH ou GERENTE_E_RH (o destino de AGREGADO não pede confirmação)
     */
    public static String confirmacaoRelato(String motivo, String destino) {
        return "Entendi assim: " + limitar(motivo, MAX_CAMPO_LONGO) + "\n\n"
                + "Posso encaminhar para " + destinatario(destino) + "? "
                + "Este canal é confidencial, não anônimo.\n\n"
                + "Se eu entendi errado, é só escrever de novo do seu jeito.";
    }

    // 5. Encaminhado e nada enviado

    public static String relatoEncaminhado() {
        return "Pronto, encaminhei. Obrigado por confiar e contar. Se quiser acrescentar algo, é só escrever.";
    }

    public static String nadaEnviado() {
        return "Tudo bem, não enviei nada. Se mudar de ideia ou quiser contar de outro jeito, é só escrever.";
    }

    // 6. Relato urgente: vai direto ao RH, sem confirmação. Sem emoji.

    public static String relatoUrgente() {
        return "Obrigado por contar. O que você relatou é sério, e por isso o RH já foi avisado. "
                + "Uma pessoa do RH vai procurar você para conversar. "
                + "Você não precisa fazer mais nada agora.";
    }

    // 7. IA indisponível (H37)

    public static String iaIndisponivel() {
        return "Recebi seu relato, obrigado por contar. Não consegui analisar a mensagem agora, "
                + "então uma pessoa do RH vai olhar com atenção.";
    }

    // 8. Elogio

    public static String elogioRecebido() {
        return "Que bom ler isso! 😊 Obrigado por compartilhar.";
    }

    // 9. Tipos não suportados (áudio, imagem, vídeo, figurinha)

    public static String tipoNaoSuportado() {
        return "Por enquanto eu só consigo ler mensagens de texto. "
                + "Se quiser, escreva com suas palavras o que queria me mandar.";
    }

    // 10. Alerta ao gerente (H17): nunca nome, motivo nem texto do relato.

    public static String alertaGerente(String loja, String turno, String categoria, String acaoSugerida) {
        return "Parceiro dos 90 dias: novo alerta de acompanhamento\n\n"
                + campos(loja, turno, categoria)
                + "Sugestão: " + limitar(acaoSugerida, MAX_CAMPO_LONGO) + "\n\n"
                + "Este alerta é confidencial. Depois do acompanhamento, toque em uma das opções abaixo.";
    }

    // 11. Alertas ao RH: com o motivo; nunca nome nem texto do relato.

    public static String alertaRh(String loja, String turno, String categoria, String motivo, String acaoSugerida) {
        return "Parceiro dos 90 dias: novo relato para o RH\n\n" + corpoRh(loja, turno, categoria, motivo, acaoSugerida);
    }

    public static String alertaRhUrgente(String loja, String turno, String categoria, String motivo,
                                         String acaoSugerida) {
        return "URGENTE: relato que pede contato ainda hoje\n\n" + corpoRh(loja, turno, categoria, motivo, acaoSugerida);
    }

    /** A IA falhou: não há categoria nem motivo; o relato está registrado no sistema. */
    public static String alertaRhSemClassificacao(String loja, String turno) {
        return "Parceiro dos 90 dias: relato sem classificação\n\n"
                + "Loja: " + campo(loja) + "\n"
                + "Turno: " + campo(turno) + "\n\n"
                + "A análise automática não funcionou para este relato. "
                + "Ele está registrado no sistema e precisa da leitura de uma pessoa do RH.\n\n"
                + AVISO_IDENTIFICACAO + "\n\n"
                + "Depois do contato, toque em uma das opções abaixo.";
    }

    private static final String AVISO_IDENTIFICACAO =
            "Atenção: loja e turno podem permitir identificar a pessoa. Trate com confidencialidade.";

    private static String corpoRh(String loja, String turno, String categoria, String motivo, String acaoSugerida) {
        return campos(loja, turno, categoria)
                + "Resumo: " + limitar(motivo, MAX_CAMPO_LONGO) + "\n"
                + "Sugestão: " + limitar(acaoSugerida, MAX_CAMPO_LONGO) + "\n\n"
                + AVISO_IDENTIFICACAO + "\n\n"
                + "Depois do contato, toque em uma das opções abaixo.";
    }

    // 12. Respostas ao responsável depois do toque

    public static String respostaConversei() {
        return "Registrado: contato realizado. O caso segue em acompanhamento até o RH ou o gestor "
                + "marcar como resolvido.";
    }

    public static String respostaNaoConsegui() {
        return "Registrado. O alerta continua pendente. Quando conseguir conversar, toque em Conversei.";
    }

    // 13. Responsável que escreve fora de um alerta

    public static String responsavelForaDeAlerta() {
        return "Oi! Por aqui eu envio os alertas de acompanhamento quando houver algum. "
                + "Quando chegar um, é só usar os botões da mensagem.";
    }

    // 14. Rótulos das categorias

    /** Rótulo amigável de uma categoria da tabela public.categorias. */
    public static String rotuloCategoria(String codigo) {
        return ROTULOS.getOrDefault(codigo, "Assunto não identificado");
    }

    /** Os 12 códigos com rótulo, na ordem da taxonomia. */
    public static Map<String, String> rotulos() {
        return Map.copyOf(ROTULOS);
    }

    private static String campos(String loja, String turno, String categoria) {
        return "Loja: " + campo(loja) + "\n"
                + "Turno: " + campo(turno) + "\n"
                + "Assunto: " + rotuloCategoria(categoria) + "\n";
    }

    private static String campo(String valor) {
        return valor == null || valor.isBlank() ? "não informado" : limitar(valor.strip(), MAX_CAMPO_CURTO);
    }

    private static String destinatario(String destino) {
        return switch (destino) {
            case "GERENTE" -> "o gerente da sua loja";
            case "RH" -> "o RH";
            case "GERENTE_E_RH" -> "o gerente da sua loja e o RH";
            default -> throw new IllegalArgumentException("destino sem confirmação: " + destino);
        };
    }

    /** Corta textos longos para a mensagem caber no limite do WhatsApp. */
    private static String limitar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String limpo = texto.strip();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo - 1).strip() + "…";
    }
}
