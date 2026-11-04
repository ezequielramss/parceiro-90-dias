package br.com.kallopolli.parceiro.whatsapp;

import br.com.kallopolli.parceiro.consentimento.TextoNormalizado;
import java.util.Optional;

/**
 * As opções que antes eram botões, agora digitadas. Ignora maiúsculas, acentos e pontuação.
 * Números não são opção: 1 a 5 é a nota do check-in.
 *
 * <ul>
 *   <li>confirmação do relato: SIM ou NÃO (só vale quando a conversa aguarda a confirmação);
 *   <li>alerta: CONVERSEI ou NÃO CONSEGUI (vale para o alerta mais recente ainda aberto do número).
 *       NÃO CONSEGUI é conferido antes de NÃO.
 * </ul>
 */
public final class RespostaPorTexto {

    public enum Alerta { CONVERSEI, NAO_CONSEGUI }

    private RespostaPorTexto() {}

    /** Resposta ao alerta, se o texto for exatamente CONVERSEI ou NÃO CONSEGUI. */
    public static Optional<Alerta> alerta(String texto) {
        String t = TextoNormalizado.normalizar(texto);
        if ("nao consegui".equals(t)) {
            return Optional.of(Alerta.NAO_CONSEGUI);
        }
        if ("conversei".equals(t)) {
            return Optional.of(Alerta.CONVERSEI);
        }
        return Optional.empty();
    }

    /** "sim", "nao" ou vazio. "não consegui" não é "não". */
    public static Optional<String> confirmacao(String texto) {
        String t = TextoNormalizado.normalizar(texto);
        return "sim".equals(t) || "nao".equals(t) ? Optional.of(t) : Optional.empty();
    }

    /** Ação interna do alerta, a mesma dos antigos botões: alerta:&lt;entregaId&gt;:ok|nao. */
    public static String acaoAlerta(long entregaId, Alerta resposta) {
        return "alerta:" + entregaId + (resposta == Alerta.CONVERSEI ? ":ok" : ":nao");
    }

    /** Ação interna da confirmação: conf:&lt;relatoId&gt;:sim|nao. */
    public static String acaoConfirmacao(long relatoId, String resposta) {
        return "conf:" + relatoId + ":" + resposta;
    }
}
