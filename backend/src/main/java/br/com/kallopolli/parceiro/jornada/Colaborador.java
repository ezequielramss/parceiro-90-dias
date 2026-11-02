package br.com.kallopolli.parceiro.jornada;

import java.util.UUID;

/** Colaborador ativo, só com o necessário para conversar pelo WhatsApp. */
public record Colaborador(UUID id, String telefone) {

    /** Número no formato que a Graph API espera em "to" (E.164 sem o +). */
    public String numeroWhatsApp() {
        return telefone.startsWith("+") ? telefone.substring(1) : telefone;
    }

    @Override
    public String toString() {
        return "Colaborador[id=" + id + "]";
    }
}
