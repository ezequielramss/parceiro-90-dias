package br.com.kallopolli.parceiro.alerta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResponsavelRepository {

    /** Gerentes ativos da loja, comparando sem diferença de caixa e espaços (normalizar_loja). */
    List<Responsavel> gerentesAtivosDaLoja(String loja);

    /** Todos os responsáveis ativos com papel RH. */
    List<Responsavel> rhAtivos();

    /** Responsáveis ativos com qualquer um dos telefones (E.164); o mesmo número pode ter dois papéis. */
    List<Responsavel> ativosPorTelefones(List<String> telefonesE164);

    Optional<Responsavel> buscarResponsavel(UUID id);

    /** Todos os responsáveis ativos (lista de contatos do simulador local). */
    List<Responsavel> responsaveisAtivos();
}
