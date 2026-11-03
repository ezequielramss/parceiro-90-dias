package br.com.kallopolli.parceiro.alerta;

import java.util.UUID;

/** Quem recebe alertas: gerente de uma loja ou RH. O toString não mostra nome nem telefone. */
public record Responsavel(UUID id, String nome, String telefone, String papel, String loja) {

    public static final String GERENTE = "GERENTE";
    public static final String RH = "RH";

    /** Número no formato que a Graph API espera em "to" (E.164 sem o +). */
    public String numeroWhatsApp() {
        return telefone.startsWith("+") ? telefone.substring(1) : telefone;
    }

    @Override
    public String toString() {
        return "Responsavel[id=" + id + ", papel=" + papel + "]";
    }
}
