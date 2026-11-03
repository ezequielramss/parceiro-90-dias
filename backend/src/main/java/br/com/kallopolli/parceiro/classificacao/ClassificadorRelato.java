package br.com.kallopolli.parceiro.classificacao;

public interface ClassificadorRelato {

    /**
     * Classifica um relato na taxonomia. Nunca lança exceção: qualquer falha devolve
     * {@link Classificacao#falha()}.
     */
    Classificacao classificar(String textoDoRelato);
}
