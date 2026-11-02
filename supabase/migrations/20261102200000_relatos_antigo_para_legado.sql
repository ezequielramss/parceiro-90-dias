-- Etapa 1 — libera o nome public.relatos para o modelo novo.
--
-- A tabela relatos antiga (id_relatos, mensagem, id_funcionario -> funcionarios)
-- está vazia e é referenciada por respostas e encaminhamentos, também vazias.
-- Em vez de apagar, renomeia para relatos_legado: as chaves estrangeiras seguem
-- o rename e nada se perde. A limpeza das tabelas legadas fica para depois.

do $$
begin
  if exists (select 1 from public.relatos) then
    raise exception 'public.relatos antiga tem linhas; migre-as antes de renomear';
  end if;
end
$$;

alter table public.relatos rename to relatos_legado;

-- Nomes de índice são únicos no schema; libera relatos_pkey e relatos_categoria_idx.
alter table public.relatos_legado rename constraint relatos_pkey to relatos_legado_pkey;
alter table public.relatos_legado rename constraint relatos_categoria_fkey to relatos_legado_categoria_fkey;
alter table public.relatos_legado rename constraint relatos_id_funcionario_fkey to relatos_legado_id_funcionario_fkey;
alter index public.relatos_categoria_idx rename to relatos_legado_categoria_idx;
alter sequence public.relatos_id_relatos_seq rename to relatos_legado_id_relatos_seq;

comment on table public.relatos_legado is 'Modelo antigo de relatos (Etapa 0). Vazia; substituída por public.relatos.';
