package br.com.kallopolli.parceiro.agentes;

import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * "Contar algo": o fluxo do relato, sem mudança (classificação, confirmação com SIM ou NÃO, alertas,
 * urgente, falha da IA e agradecimento ao elogio). Reclamação não é agente separado: é um tipo de
 * relato, que a taxonomia já cobre.
 */
@Component
public class AgenteRelato implements Agente {

    static final String CODIGO = "RELATO";

    private final FluxoRelato fluxo;
    private final MensageiroWhatsApp mensageiro;

    public AgenteRelato(FluxoRelato fluxo, MensageiroWhatsApp mensageiro) {
        this.fluxo = fluxo;
        this.mensageiro = mensageiro;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nome() {
        return TextosConversa.NOME_AGENTE_RELATO;
    }

    @Override
    public int ordem() {
        return 10;
    }

    @Override
    public String abertura() {
        return TextosConversa.aberturaRelato();
    }

    @Override
    public List<String> nomesAceitos() {
        return List.of(nome(), codigo(), "contar algo", "reclamacao");
    }

    @Override
    public Desfecho tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
        if (mensagem.conteudo().isBlank()) {
            mensageiro.enviar(colaborador, TextosConversa.conviteRelato());
            return Desfecho.continua("RELATO_VAZIO");
        }
        return fluxo.novo(colaborador, mensagem.conteudo(), mensagemId);
    }
}
