package br.com.kallopolli.parceiro.consentimento;

/** Textos do fluxo de aceite. O termo é gravado inteiro em consentimentos.texto_aceito. */
public final class Textos {

    private Textos() {}

    public static final String TERMO_V1 = """
            Termo de participação v1 — Parceiro dos 90 dias.
            Durante seus primeiros 90 dias, vamos conversar com você por aqui para saber como você está. \
            Suas respostas ajudam a cuidar do seu bem-estar. Relatos ficam guardados por até 90 dias, \
            e assuntos sensíveis vão só para o RH. Você pode sair quando quiser escrevendo PARAR.""";

    public static final String PEDIDO_ACEITE = TERMO_V1 + """


            Para participar, responda ACEITO. Se não quiser receber mensagens, responda PARAR.""";

    public static final String CONFIRMACAO_ACEITE =
            "Obrigado! Seu aceite foi registrado. Para sair a qualquer momento, escreva PARAR.";

    public static final String CONFIRMACAO_PARAR =
            "Pronto, você não vai mais receber mensagens de acompanhamento. "
                    + "Se mudar de ideia, é só escrever ACEITO.";
}
