package br.com.kallopolli.parceiro.whatsapp;

/**
 * Canal de saída do WhatsApp. Escolhido por parceiro.whatsapp.canal: evolution (padrão) ou
 * simulador. Orquestrador, alertas e agendador dependem só desta interface.
 */
public interface CanalSaida {

    /**
     * Envia um texto ao número (só dígitos, com DDI) e devolve o id da mensagem criada, ou null
     * se o canal não informar.
     *
     * @throws FalhaEnvio se o canal recusar ou não responder
     */
    String enviarTexto(String numero, String texto);
}
