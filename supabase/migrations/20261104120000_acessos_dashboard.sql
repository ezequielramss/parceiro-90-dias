-- Etapa 1 — Java C: acesso ao dashboard e retenção dos relatos.
--
-- Só mudanças aditivas. Acesso só pelo backend; a tabela nova fica fechada como na H26.

-- a) Quem entra no painel. O login é do Supabase Auth; papel e loja vêm SEMPRE daqui,
--    nunca do token nem do navegador. Sem linha, o backend responde 403.
create table public.acessos_dashboard (
  user_id   uuid primary key references auth.users (id) on delete cascade,
  papel     text not null check (papel in ('GERENTE', 'RH')),
  loja      text,
  nome      text check (nome is null or btrim(nome) <> ''),
  criado_em timestamptz not null default now(),
  -- GERENTE vê uma loja só, gravada já normalizada (ex.: public.normalizar_loja('Loja Centro'));
  -- RH vê todas e não tem loja.
  constraint acessos_dashboard_loja_por_papel check (
    (papel = 'GERENTE' and loja is not null and btrim(loja) <> '' and loja = public.normalizar_loja(loja))
    or (papel = 'RH' and loja is null))
);

comment on table public.acessos_dashboard is
  'Papel e loja de cada usuário do Supabase Auth no dashboard. Inserido pelo PO, nunca pelo navegador.';

-- b) Retenção (termo de participação: relatos guardados por até 90 dias). A tarefa diária do
--    backend apaga o texto e o motivo; categoria, destino, datas e contagens continuam.
alter table public.relatos alter column texto drop not null;

comment on column public.relatos.texto is
  'Texto do colaborador. Apagado (null) pela retenção 90 dias depois de criado_em.';

-- c) Fechamento padrão (H26).
alter table public.acessos_dashboard enable row level security;
revoke all on public.acessos_dashboard from anon, authenticated;
create policy negar_anon_authenticated on public.acessos_dashboard as restrictive for all
  to anon, authenticated using (false) with check (false);
