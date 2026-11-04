package br.com.kallopolli.parceiro.whatsapp;

/**
 * Falha ao enviar pelo canal. A mensagem nunca carrega chave, URL com segredo, corpo da
 * requisição nem texto do usuário: só o status HTTP (0 sem resposta) e um motivo curto.
 */
public class FalhaEnvio extends RuntimeException {

    private final int status;

    public FalhaEnvio(int status, String motivo) {
        super("envio recusado: status=" + status + ", motivo=" + motivo);
        this.status = status;
    }

    public FalhaEnvio(String motivo) {
        super("falha de comunicação com o canal: " + motivo);
        this.status = 0;
    }

    public int status() {
        return status;
    }
}
