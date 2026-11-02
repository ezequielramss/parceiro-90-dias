package br.com.kallopolli.parceiro.consentimento;

import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ConsentimentoRepository;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import org.springframework.stereotype.Service;

/**
 * H02 — aceite e retirada pelo WhatsApp.
 *
 * <ul>
 *   <li>"parar" (qualquer caixa, com ou sem acento e pontuação) retira o aceite e confirma;
 *   <li>sem aceite ativo, "aceito" registra o aceite com o texto do termo;
 *   <li>sem aceite ativo, qualquer outra mensagem recebe o pedido de aceite;
 *   <li>com aceite ativo, a mensagem fica registrada para a classificação (próxima história).
 * </ul>
 */
@Service
public class FluxoConsentimento {

    public enum Resultado { RETIRADA, ACEITE, PEDIDO_ACEITE, REGISTRADA }

    private static final String PARAR = "parar";
    private static final String ACEITO = "aceito";

    private final ConsentimentoRepository consentimentos;
    private final AuditoriaRepository auditoria;
    private final MensageiroWhatsApp mensageiro;

    public FluxoConsentimento(
            ConsentimentoRepository consentimentos, AuditoriaRepository auditoria, MensageiroWhatsApp mensageiro) {
        this.consentimentos = consentimentos;
        this.auditoria = auditoria;
        this.mensageiro = mensageiro;
    }

    public Resultado tratar(Colaborador colaborador, String texto) {
        String resposta = TextoNormalizado.normalizar(texto);

        if (PARAR.equals(resposta)) {
            consentimentos.registrarRetirada(colaborador.id()).ifPresent(id ->
                    auditoria.registrar(quem(colaborador), "CONSENTIMENTO_RETIRADO", "consentimento/" + id));
            mensageiro.enviar(colaborador, Textos.CONFIRMACAO_PARAR);
            return Resultado.RETIRADA;
        }

        if (consentimentos.temAtivo(colaborador.id())) {
            return Resultado.REGISTRADA;
        }

        if (ACEITO.equals(resposta)) {
            long id = consentimentos.registrarAceite(colaborador.id(), Textos.TERMO_V1);
            auditoria.registrar(quem(colaborador), "CONSENTIMENTO_ACEITO", "consentimento/" + id);
            mensageiro.enviar(colaborador, Textos.CONFIRMACAO_ACEITE);
            return Resultado.ACEITE;
        }

        mensageiro.enviar(colaborador, Textos.PEDIDO_ACEITE);
        return Resultado.PEDIDO_ACEITE;
    }

    private static String quem(Colaborador colaborador) {
        return "colaborador/" + colaborador.id();
    }
}
