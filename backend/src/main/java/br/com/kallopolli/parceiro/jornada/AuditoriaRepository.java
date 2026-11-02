package br.com.kallopolli.parceiro.jornada;

public interface AuditoriaRepository {

    /** Só inserção; a tabela recusa UPDATE, DELETE e TRUNCATE. */
    void registrar(String quem, String acao, String objeto);
}
