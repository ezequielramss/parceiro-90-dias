package br.com.kallopolli.parceiro.classificacao;

/** Resultado da classificação de um relato. O toString não mostra motivo nem ação. */
public record Classificacao(
        String categoria,
        String motivo,
        String acaoSugerida,
        String confianca,
        boolean sinaisGraves,
        boolean falhaIa) {

    public static final String NAO_CLASSIFICADO = "NAO_CLASSIFICADO";

    /** A IA não respondeu ou respondeu algo inválido: o relato vai ao RH sem classificação (H37). */
    public static Classificacao falha() {
        return new Classificacao(NAO_CLASSIFICADO, null, null, null, false, true);
    }

    @Override
    public String toString() {
        return "Classificacao[categoria=" + categoria + ", confianca=" + confianca
                + ", sinaisGraves=" + sinaisGraves + ", falhaIa=" + falhaIa + "]";
    }
}
