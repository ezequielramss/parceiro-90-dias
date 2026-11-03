package br.com.kallopolli.parceiro.classificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoteadorRelatoTest {

    private final RoteadorRelato roteador = new RoteadorRelato();
    private final CategoriasEmMemoria categorias = new CategoriasEmMemoria();

    private static final List<String> RESTRITAS =
            List.of("CONFLITO_CHEFIA", "ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE", "NAO_CLASSIFICADO");

    private DecisaoEncaminhamento decidir(String codigo, String confianca, boolean sinaisGraves, boolean falhaIa) {
        var c = falhaIa ? Classificacao.falha()
                : new Classificacao(codigo, "motivo", "acao", confianca, sinaisGraves, false);
        return roteador.decidir(c, categorias.buscar(c.categoria()).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANSACO", "SOBRECARGA", "DESMOTIVACAO", "DUVIDA", "CONFLITO_COLEGA"})
    void categoriaComumVaiAoGerenteComConfirmacao(String codigo) {
        assertThat(decidir(codigo, "alta", false, false))
                .isEqualTo(new DecisaoEncaminhamento("GERENTE", false, true, false));
    }

    @Test
    void vontadeDeSairVaiAoGerenteEAoRhComConfirmacao() {
        assertThat(decidir("VONTADE_DE_SAIR", "alta", false, false))
                .isEqualTo(new DecisaoEncaminhamento("GERENTE_E_RH", false, true, false));
    }

    @Test
    void conflitoComChefiaENaoClassificadoVaoAoRhComConfirmacao() {
        assertThat(decidir("CONFLITO_CHEFIA", "alta", false, false))
                .isEqualTo(new DecisaoEncaminhamento("RH", false, true, false));
        assertThat(decidir("NAO_CLASSIFICADO", "baixa", false, false))
                .isEqualTo(new DecisaoEncaminhamento("RH", false, true, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE"})
    void categoriaUrgenteVaiAoRhNaHoraSemConfirmacao(String codigo) {
        assertThat(decidir(codigo, "alta", false, false))
                .isEqualTo(new DecisaoEncaminhamento("RH", true, false, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANSACO", "VONTADE_DE_SAIR", "ELOGIO", "CONFLITO_COLEGA"})
    void sinaisGravesEmQualquerCategoriaVaoAoRhComoUrgente(String codigo) {
        assertThat(decidir(codigo, "baixa", true, false))
                .isEqualTo(new DecisaoEncaminhamento("RH", true, false, false));
    }

    @Test
    void elogioNaoGeraAlerta() {
        assertThat(decidir("ELOGIO", "alta", false, false))
                .isEqualTo(new DecisaoEncaminhamento("AGREGADO", false, false, true));
    }

    @Test
    void falhaDaIaVaiAoRhSemUrgenciaESemConfirmacao() {
        assertThat(decidir("CANSACO", null, false, true))
                .isEqualTo(new DecisaoEncaminhamento("RH", false, false, false));
    }

    @Test
    void confiancaNaoMudaODestino() {
        for (String codigo : categorias.todas().keySet()) {
            var alta = decidir(codigo, "alta", false, false);
            assertThat(decidir(codigo, "media", false, false)).isEqualTo(alta);
            assertThat(decidir(codigo, "baixa", false, false)).isEqualTo(alta);
            assertThat(decidir(codigo, null, false, false)).isEqualTo(alta);
        }
    }

    @Test
    void invarianciaCategoriaRestritaNuncaChegaAoGerente() {
        int combinacoes = 0;
        for (String codigo : categorias.todas().keySet()) {
            for (String confianca : Arrays.asList("baixa", "media", "alta", null)) {
                for (boolean sinais : new boolean[] {false, true}) {
                    for (boolean falha : new boolean[] {false, true}) {
                        combinacoes++;
                        var decisao = decidir(codigo, confianca, sinais, falha);
                        if (RESTRITAS.contains(codigo) || falha) {
                            assertThat(decisao.destino())
                                    .as("%s confianca=%s sinais=%s falha=%s", codigo, confianca, sinais, falha)
                                    .isNotIn("GERENTE", "GERENTE_E_RH");
                        }
                        if (sinais && !falha) {
                            assertThat(decisao.destino()).isEqualTo("RH");
                            assertThat(decisao.urgente()).isTrue();
                        }
                    }
                }
            }
        }
        assertThat(combinacoes).isEqualTo(12 * 4 * 2 * 2);
    }

    @Test
    void restritaNuncaVaiAoGerenteMesmoComTabelaErrada() {
        var errada = new CategoriasEmMemoria();
        errada.colocar(new Categoria("CONFLITO_CHEFIA", "GERENTE", false));
        errada.colocar(new Categoria("NAO_CLASSIFICADO", "AGREGADO", false));
        for (String codigo : List.of("CONFLITO_CHEFIA", "NAO_CLASSIFICADO")) {
            var c = new Classificacao(codigo, "m", "a", "alta", false, false);
            assertThat(roteador.decidir(c, errada.buscar(codigo).orElseThrow()))
                    .isEqualTo(new DecisaoEncaminhamento("RH", false, true, false));
        }
    }

    @Test
    void categoriaAusenteOuDestinoDesconhecidoVaiAoRh() {
        var c = new Classificacao("CANSACO", "m", "a", "alta", false, false);
        assertThat(roteador.decidir(c, null).destino()).isEqualTo("RH");
        assertThat(roteador.decidir(c, new Categoria("CANSACO", "LOJA", false)).destino()).isEqualTo("RH");
    }
}
