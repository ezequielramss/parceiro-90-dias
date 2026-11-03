package br.com.kallopolli.parceiro.classificacao;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decide para quem o relato vai (H19). Regra pura, sem banco nem rede.
 *
 * <ul>
 *   <li>falha da IA: RH, sem urgência e sem confirmação (não há resumo para confirmar; H37);
 *   <li>categoria urgente ou sinais graves: RH, urgente, sem esperar confirmação (decisão do PO);
 *   <li>AGREGADO (elogio): sem alerta;
 *   <li>demais: o destino da tabela categorias, com confirmação do colaborador.
 * </ul>
 * A confiança não muda o destino. Categoria restrita nunca vai ao gerente, mesmo que a tabela
 * esteja errada.
 */
@Component
public class RoteadorRelato {

    public static final String GERENTE = "GERENTE";
    public static final String GERENTE_E_RH = "GERENTE_E_RH";
    public static final String RH = "RH";
    public static final String AGREGADO = "AGREGADO";

    /** Vão só ao RH (tabela categorias); a guarda abaixo protege contra erro de cadastro. */
    static final Set<String> RESTRITAS =
            Set.of("CONFLITO_CHEFIA", "ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE", "NAO_CLASSIFICADO");

    public DecisaoEncaminhamento decidir(Classificacao classificacao, Categoria categoria) {
        if (classificacao == null || classificacao.falhaIa() || categoria == null) {
            return new DecisaoEncaminhamento(RH, false, false, false);
        }
        if (classificacao.sinaisGraves() || categoria.urgente()) {
            return new DecisaoEncaminhamento(RH, true, false, false);
        }
        if (RESTRITAS.contains(categoria.codigo()) || RESTRITAS.contains(classificacao.categoria())) {
            return new DecisaoEncaminhamento(RH, false, true, false);
        }
        if (AGREGADO.equals(categoria.destino())) {
            return new DecisaoEncaminhamento(AGREGADO, false, false, true);
        }
        String destino = Set.of(GERENTE, GERENTE_E_RH, RH).contains(categoria.destino()) ? categoria.destino() : RH;
        return new DecisaoEncaminhamento(destino, false, true, false);
    }
}
