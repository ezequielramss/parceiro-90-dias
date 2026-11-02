package br.com.kallopolli.parceiro.jornada;

import java.util.Optional;

public interface ColaboradorRepository {

    /** Busca pelo telefone em E.164 (com +); só colaboradores ativos. */
    Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164);
}
