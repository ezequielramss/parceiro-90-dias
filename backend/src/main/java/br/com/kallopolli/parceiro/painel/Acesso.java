package br.com.kallopolli.parceiro.painel;

import java.util.UUID;

/**
 * Quem está usando o painel, lido de public.acessos_dashboard. Papel e loja vêm SEMPRE do banco,
 * nunca do token nem do navegador. Para o GERENTE, a loja já está normalizada (normalizar_loja).
 */
public record Acesso(UUID userId, String papel, String loja, String nome) {

    public static final String GERENTE = "GERENTE";
    public static final String RH = "RH";

    public boolean rh() {
        return RH.equals(papel);
    }

    /** Loja usada no filtro do SQL; null para o RH (todas as lojas). */
    public String lojaDoFiltro() {
        return rh() ? null : loja;
    }
}
