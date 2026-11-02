package br.com.kallopolli.parceiro.whatsapp;

/**
 * Falha ao chamar a Graph API. A mensagem nunca carrega token, corpo da requisição
 * nem texto do usuário: só o status HTTP e o código de erro da Meta.
 */
public class WhatsAppClientException extends RuntimeException {

    private final int status;
    private final String codigoMeta;

    public WhatsAppClientException(int status, String codigoMeta) {
        super("Graph API recusou o envio: status=" + status + ", codigoMeta=" + codigoMeta);
        this.status = status;
        this.codigoMeta = codigoMeta;
    }

    public WhatsAppClientException(String motivo) {
        super("Falha de comunicação com a Graph API: " + motivo);
        this.status = 0;
        this.codigoMeta = null;
    }

    public int status() {
        return status;
    }

    public String codigoMeta() {
        return codigoMeta;
    }
}
