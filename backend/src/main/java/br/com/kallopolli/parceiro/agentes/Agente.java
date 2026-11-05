package br.com.kallopolli.parceiro.agentes;

import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import java.util.List;

/**
 * Uma opção do menu do colaborador, com comportamento próprio. Um agente novo é só uma classe
 * {@code @Component} que implementa esta interface: o {@link RegistroAgentes} o encontra, e o
 * orquestrador não muda.
 *
 * <p>Regras que todo agente segue: texto do colaborador nunca vai para log nem auditoria; nada
 * sai para gerente ou RH fora das regras que já existem; texto livre passa pela mesma regra de
 * sinais graves do relato (use {@link FluxoRelato}).
 */
public interface Agente {

    /** Identificador estável, gravado em conversa_estado.agente (ex.: RELATO). Nunca reutilize. */
    String codigo();

    /** Como aparece no menu. */
    String nome();

    /** Posição no menu, do menor para o maior. */
    int ordem();

    /** A abertura enviada ao escolher a opção. */
    String abertura();

    /**
     * Trata uma mensagem de quem está neste agente. O agente envia as próprias respostas e diz o
     * que acontece depois. "menu", "voltar" e "0" nem chegam aqui: o orquestrador cuida deles.
     *
     * @param mensagemId id da mensagem já gravada em mensagens
     */
    Desfecho tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId);

    /** O agente pode se desligar sozinho; além disso vale parceiro.agentes.&lt;codigo&gt;.ativo. */
    default boolean ativo() {
        return true;
    }

    /** O que a pessoa pode digitar, além do número, para escolher esta opção. */
    default List<String> nomesAceitos() {
        return List.of(nome(), codigo());
    }

    /** O que acontece depois de uma mensagem. O resultado vai para o log: só códigos, nunca texto. */
    record Desfecho(String resultado, Proximo proximo) {

        public enum Proximo {
            /** A próxima mensagem também vai para este agente. */
            CONTINUA,
            /** A interação acabou: a pessoa volta a LIVRE. */
            LIVRE,
            /** O próprio agente já gravou o próximo estado (ex.: aguardando confirmação do relato). */
            ESTADO_DEFINIDO
        }

        public static Desfecho continua(String resultado) {
            return new Desfecho(resultado, Proximo.CONTINUA);
        }

        public static Desfecho livre(String resultado) {
            return new Desfecho(resultado, Proximo.LIVRE);
        }

        public static Desfecho estadoDefinido(String resultado) {
            return new Desfecho(resultado, Proximo.ESTADO_DEFINIDO);
        }
    }
}
