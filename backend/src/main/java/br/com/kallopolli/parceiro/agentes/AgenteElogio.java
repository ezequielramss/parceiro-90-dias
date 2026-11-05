package br.com.kallopolli.parceiro.agentes;

import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import org.springframework.stereotype.Component;

/**
 * "Fazer um elogio". O texto passa pelo mesmo classificador do relato, porque um elogio pode
 * esconder um problema ou um sinal grave. Elogio de verdade é agradecido, sem alerta, e conta nos
 * indicadores como qualquer relato de categoria ELOGIO. Outra categoria segue o fluxo normal do
 * relato. O texto segue a mesma retenção de 90 dias.
 */
@Component
public class AgenteElogio implements Agente {

    static final String CODIGO = "ELOGIO";

    private final FluxoRelato fluxo;
    private final MensageiroWhatsApp mensageiro;

    public AgenteElogio(FluxoRelato fluxo, MensageiroWhatsApp mensageiro) {
        this.fluxo = fluxo;
        this.mensageiro = mensageiro;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nome() {
        return TextosConversa.NOME_AGENTE_ELOGIO;
    }

    @Override
    public int ordem() {
        return 20;
    }

    @Override
    public String abertura() {
        return TextosConversa.aberturaElogio();
    }

    @Override
    public Desfecho tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
        if (mensagem.conteudo().isBlank()) {
            mensageiro.enviar(colaborador, TextosConversa.aberturaElogio());
            return Desfecho.continua("ELOGIO_VAZIO");
        }
        return fluxo.novo(colaborador, mensagem.conteudo(), mensagemId);
    }
}
