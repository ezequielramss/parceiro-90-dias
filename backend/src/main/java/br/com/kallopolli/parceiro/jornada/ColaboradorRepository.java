package br.com.kallopolli.parceiro.jornada;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ColaboradorRepository {

    /** Busca pelo telefone em E.164 (com +); só colaboradores ativos. */
    Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164);

    /** Colaboradores ativos, com aceite ativo, que começaram entre as duas datas (inclusive). */
    List<Colaborador> ativosComAceiteIniciadosEntre(LocalDate de, LocalDate ate);

    /** Todos os colaboradores ativos (lista de contatos do simulador local). */
    List<Colaborador> colaboradoresAtivos();
}
