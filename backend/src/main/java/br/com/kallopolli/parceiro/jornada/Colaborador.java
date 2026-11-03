package br.com.kallopolli.parceiro.jornada;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Colaborador ativo, com o necessário para conversar, rotear alertas (loja e turno) e
 * agendar check-ins (data de início). O toString não mostra nome, telefone, loja nem turno.
 */
public record Colaborador(UUID id, String telefone, String nome, String loja, String turno, LocalDate dataInicio) {

    public Colaborador(UUID id, String telefone) {
        this(id, telefone, null, null, null, null);
    }

    /** Número no formato que a Graph API espera em "to" (E.164 sem o +). */
    public String numeroWhatsApp() {
        return telefone.startsWith("+") ? telefone.substring(1) : telefone;
    }

    /** Primeiro nome para a saudação do check-in; nulo se não houver nome. */
    public String primeiroNome() {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        return nome.strip().split("\\s+")[0];
    }

    @Override
    public String toString() {
        return "Colaborador[id=" + id + "]";
    }
}
