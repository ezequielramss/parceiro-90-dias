-- Etapa 1 — H02, H31, H39: modelo de dados da jornada de 90 dias.
--
-- Acesso só pelo backend (service_role). Toda tabela nasce com RLS ligado, sem
-- grants e com a policy restritiva negar_anon_authenticated, como na H26.
-- Estados seguem docs/contrato-api.md.

-- Colaborador, identificado pelo número de WhatsApp cadastrado pelo RH.
create table public.colaboradores (
  id          uuid primary key default gen_random_uuid(),
  nome        text not null check (btrim(nome) <> ''),
  telefone    text not null unique check (telefone ~ '^\+[1-9][0-9]{7,14}$'),
  loja        text not null check (btrim(loja) <> ''),
  turno       text,
  data_inicio date not null,
  ativo       boolean not null default true,
  criado_em   timestamptz not null default now()
);

comment on column public.colaboradores.telefone is 'Número de WhatsApp em E.164 (ex.: +5511999990000). Único.';

-- Aceite do termo e retirada ("parar"). Só um consentimento ativo por colaborador.
create table public.consentimentos (
  id             bigint generated always as identity primary key,
  colaborador_id uuid not null references public.colaboradores (id),
  aceito_em      timestamptz not null default now(),
  retirado_em    timestamptz check (retirado_em >= aceito_em),
  texto_aceito   text not null check (btrim(texto_aceito) <> '')
);

create index consentimentos_colaborador_id_idx on public.consentimentos (colaborador_id);
create unique index consentimentos_um_ativo_por_colaborador
  on public.consentimentos (colaborador_id) where retirado_em is null;

-- Mensagens do WhatsApp. O wamid único garante idempotência do webhook:
-- reprocessar o mesmo evento esbarra na constraint.
create table public.mensagens (
  id             bigint generated always as identity primary key,
  wamid          text not null,
  colaborador_id uuid not null references public.colaboradores (id),
  direcao        text not null check (direcao in ('ENTRADA', 'SAIDA')),
  tipo           text not null check (tipo ~ '^[a-z_]+$'),
  texto          text,
  criada_em      timestamptz not null default now(),
  constraint mensagens_wamid_key unique (wamid)
);

create index mensagens_colaborador_id_idx on public.mensagens (colaborador_id);

-- Check-in diário da jornada.
create table public.checkins (
  id             bigint generated always as identity primary key,
  colaborador_id uuid not null references public.colaboradores (id),
  dia_jornada    smallint not null check (dia_jornada between 1 and 90),
  enviado_em     timestamptz not null default now(),
  respondido_em  timestamptz check (respondido_em >= enviado_em),
  humor          smallint check (humor between 1 and 5)
);

create index checkins_colaborador_id_dia_idx on public.checkins (colaborador_id, dia_jornada);

-- Relato classificado na taxonomia única. Retenção de 90 dias: expira_em é
-- sempre criado_em + 90 dias (trigger, porque timestamptz + interval não é
-- imutável e não pode ser coluna gerada).
create table public.relatos (
  id             bigint generated always as identity primary key,
  colaborador_id uuid not null references public.colaboradores (id),
  mensagem_id    bigint not null references public.mensagens (id),
  texto          text not null,
  categoria      text not null default 'NAO_CLASSIFICADO' references public.categorias (codigo),
  motivo         text,
  acao_sugerida  text,
  estado         text not null default 'NOVO' check (estado in ('NOVO', 'EM_ANALISE', 'CONCLUIDO')),
  criado_em      timestamptz not null default now(),
  expira_em      timestamptz not null
);

create index relatos_colaborador_id_idx on public.relatos (colaborador_id);
create index relatos_mensagem_id_idx on public.relatos (mensagem_id);
create index relatos_categoria_idx on public.relatos (categoria);
create index relatos_expira_em_idx on public.relatos (expira_em);

create function public.relatos_definir_expiracao()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.expira_em := new.criado_em + interval '90 days';
  return new;
end;
$$;

create trigger relatos_definir_expiracao
  before insert or update of criado_em, expira_em on public.relatos
  for each row execute function public.relatos_definir_expiracao();

-- Alerta para gerente e/ou RH a partir de um relato.
create table public.alertas (
  id            bigint generated always as identity primary key,
  relato_id     bigint not null references public.relatos (id),
  destino       text not null check (destino in ('GERENTE', 'RH', 'GERENTE_E_RH')),
  responsavel   text,
  estado        text not null default 'ABERTO' check (estado in ('ABERTO', 'ATENDIDO', 'SEM_RETORNO_24H')),
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);

create index alertas_relato_id_idx on public.alertas (relato_id);

create function public.tocar_atualizado_em()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.atualizado_em := now();
  return new;
end;
$$;

create trigger alertas_tocar_atualizado_em
  before update on public.alertas
  for each row execute function public.tocar_atualizado_em();

-- Trilha de auditoria: só inserção. UPDATE, DELETE e TRUNCATE são recusados
-- por trigger, inclusive para o backend.
create table public.auditoria (
  id     bigint generated always as identity primary key,
  quem   text not null,
  acao   text not null,
  objeto text not null,
  quando timestamptz not null default now()
);

create function public.auditoria_somente_insercao()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception 'auditoria aceita só inserção (% recusado)', tg_op
    using errcode = 'insufficient_privilege';
end;
$$;

create trigger auditoria_sem_update_delete
  before update or delete on public.auditoria
  for each row execute function public.auditoria_somente_insercao();

create trigger auditoria_sem_truncate
  before truncate on public.auditoria
  for each statement execute function public.auditoria_somente_insercao();

revoke update, delete, truncate on public.auditoria from service_role;

-- Fechamento padrão (H26): RLS, sem grants e negação explícita para anon e authenticated.
do $$
declare
  t text;
begin
  foreach t in array array['colaboradores', 'consentimentos', 'mensagens', 'checkins',
                           'relatos', 'alertas', 'auditoria']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format(
      'create policy negar_anon_authenticated on public.%I as restrictive for all '
      'to anon, authenticated using (false) with check (false)', t);
  end loop;
end
$$;

revoke all on function public.relatos_definir_expiracao() from public, anon, authenticated;
revoke all on function public.tocar_atualizado_em() from public, anon, authenticated;
revoke all on function public.auditoria_somente_insercao() from public, anon, authenticated;
