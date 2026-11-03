-- Etapa 1 — Migração 2: o que o fluxo completo precisa (H04, H13, H16, H17, H19, H20, H37).
--
-- Proposta aprovada em docs/etapa-1b-banco.md. Só mudanças aditivas: nada existente é
-- apagado nem renomeado. Acesso só pelo backend; tabelas novas fechadas como na H26.

-- Comparação de lojas sem diferença de caixa e espaços (colaboradores.loja x responsaveis.loja).
create function public.normalizar_loja(loja text)
returns text
language sql
immutable
set search_path = ''
as $$
  select lower(regexp_replace(btrim(loja), '\s+', ' ', 'g'))
$$;

-- a) Quem recebe alertas: gerente de uma loja ou RH (todas as lojas).
create table public.responsaveis (
  id        uuid primary key default gen_random_uuid(),
  nome      text not null check (btrim(nome) <> ''),
  telefone  text not null check (telefone ~ '^\+[1-9][0-9]{7,14}$'),
  papel     text not null check (papel in ('GERENTE', 'RH')),
  loja      text,
  ativo     boolean not null default true,
  criado_em timestamptz not null default now(),
  constraint responsaveis_telefone_papel_key unique (telefone, papel),
  constraint responsaveis_loja_por_papel check (
    (papel = 'GERENTE' and loja is not null and btrim(loja) <> '')
    or (papel = 'RH' and loja is null))
);

comment on column public.responsaveis.telefone is 'WhatsApp em E.164. O mesmo número pode ser GERENTE e RH.';

create index responsaveis_gerente_loja_idx
  on public.responsaveis (public.normalizar_loja(loja))
  where papel = 'GERENTE' and ativo;

-- Permite que conversa_estado aponte só para check-in e relato da mesma pessoa.
alter table public.checkins
  add constraint checkins_id_colaborador_key unique (id, colaborador_id);
alter table public.relatos
  add constraint relatos_id_colaborador_key unique (id, colaborador_id);

-- c) Um check-in por dia de jornada; mensagem de saída opcional.
alter table public.checkins
  add constraint checkins_um_por_dia unique (colaborador_id, dia_jornada),
  add column mensagem_id bigint references public.mensagens (id);
create index checkins_mensagem_id_idx on public.checkins (mensagem_id);

-- d) Relatos: confirmação do colaborador e dados da classificação.
alter table public.relatos
  add column confirmacao   text not null default 'AGUARDANDO'
    check (confirmacao in ('AGUARDANDO', 'CONFIRMADO', 'DESCARTADO')),
  add column confianca     text check (confianca in ('baixa', 'media', 'alta')),
  add column sinais_graves boolean not null default false,
  add column falha_ia      boolean not null default false,
  add constraint relatos_falha_ia_sem_categoria check (not falha_ia or categoria = 'NAO_CLASSIFICADO');

comment on column public.relatos.motivo is 'Resumo neutro mostrado ao colaborador e ao RH.';

-- e) Alertas: urgência e resolução (contato realizado = ATENDIDO, que não é resolvido; H20).
alter table public.alertas
  add column urgente       boolean not null default false,
  add column resolvido_em  timestamptz,
  add column resolvido_por text,
  add constraint alertas_resolucao_completa check ((resolvido_em is null) = (resolvido_por is null)),
  add constraint alertas_resolvido_depois_de_criado check (resolvido_em >= criado_em);

-- b) Estado da conversa: uma linha por colaborador.
create table public.conversa_estado (
  colaborador_id uuid primary key references public.colaboradores (id),
  estado         text not null default 'LIVRE'
    check (estado in ('LIVRE', 'AGUARDANDO_HUMOR', 'AGUARDANDO_CONFIRMACAO_RELATO')),
  checkin_id     bigint,
  relato_id      bigint,
  atualizado_em  timestamptz not null default now(),
  constraint conversa_estado_checkin_fkey
    foreign key (checkin_id, colaborador_id) references public.checkins (id, colaborador_id),
  constraint conversa_estado_relato_fkey
    foreign key (relato_id, colaborador_id) references public.relatos (id, colaborador_id),
  constraint conversa_estado_coerente check (
    (estado = 'LIVRE' and checkin_id is null and relato_id is null)
    or (estado = 'AGUARDANDO_HUMOR' and checkin_id is not null and relato_id is null)
    or (estado = 'AGUARDANDO_CONFIRMACAO_RELATO' and relato_id is not null and checkin_id is null))
);

-- Mesmas colunas, na mesma ordem, das chaves estrangeiras compostas.
create index conversa_estado_checkin_idx on public.conversa_estado (checkin_id, colaborador_id);
create index conversa_estado_relato_idx on public.conversa_estado (relato_id, colaborador_id);

create trigger conversa_estado_tocar_atualizado_em
  before update on public.conversa_estado
  for each row execute function public.tocar_atualizado_em();

-- f) Entregas de alerta: quem recebeu, quando e o que respondeu (H17).
create table public.alerta_entregas (
  id             bigint generated always as identity primary key,
  alerta_id      bigint not null references public.alertas (id),
  responsavel_id uuid not null references public.responsaveis (id),
  enviado_em     timestamptz not null default now(),
  wamid_envio    text,
  falha_envio    boolean not null default false,
  resposta       text check (resposta in ('CONVERSEI', 'NAO_CONSEGUI')),
  respondido_em  timestamptz,
  constraint alerta_entregas_alerta_responsavel_key unique (alerta_id, responsavel_id),
  constraint alerta_entregas_resposta_completa check ((resposta is null) = (respondido_em is null)),
  constraint alerta_entregas_falha_sem_wamid_nem_resposta
    check (not (falha_envio and (wamid_envio is not null or resposta is not null))),
  constraint alerta_entregas_respondido_depois_de_enviado check (respondido_em >= enviado_em)
);

create index alerta_entregas_responsavel_id_idx on public.alerta_entregas (responsavel_id);
create unique index alerta_entregas_wamid_envio_key
  on public.alerta_entregas (wamid_envio) where wamid_envio is not null;

-- g) Fechamento padrão (H26).
do $$
declare
  t text;
begin
  foreach t in array array['responsaveis', 'conversa_estado', 'alerta_entregas']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format(
      'create policy negar_anon_authenticated on public.%I as restrictive for all '
      'to anon, authenticated using (false) with check (false)', t);
  end loop;
end
$$;

revoke all on function public.normalizar_loja(text) from public, anon, authenticated;
grant execute on function public.normalizar_loja(text) to service_role;
