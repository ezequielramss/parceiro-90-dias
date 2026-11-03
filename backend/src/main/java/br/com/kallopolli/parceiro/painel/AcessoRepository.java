package br.com.kallopolli.parceiro.painel;

import java.util.Optional;
import java.util.UUID;

/** Papel e loja de cada usuário do Supabase Auth no painel (public.acessos_dashboard). */
public interface AcessoRepository {

    Optional<Acesso> buscarAcesso(UUID userId);
}
