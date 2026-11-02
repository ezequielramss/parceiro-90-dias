-- H27 — Taxonomia única de categorias.
--
-- A tabela antiga public.categorias (id_categoria bigint, nome_categoria) está
-- vazia e só é referenciada por relatos.categoria, que também está vazia.
-- Ela é substituída por public.categorias com chave natural `codigo`, e
-- relatos.categoria passa a ser text referenciando categorias(codigo).
-- Os guards abaixo abortam a migração se alguma dessas colunas tiver dados,
-- para nunca apagar nada em silêncio.
--
-- relatos_ia (base de rótulos legados) não perde nenhum dado: ganha a coluna
-- categoria_codigo, preenchida a partir de public.categorias_legado_mapa.
-- Não existe tabela de alertas ainda; quando existir, a coluna de categoria
-- dela deve referenciar public.categorias(codigo) da mesma forma.

do $$
begin
  if exists (select 1 from public.categorias) then
    raise exception 'public.categorias antiga tem linhas; migre-as antes de substituir a tabela';
  end if;
  if exists (select 1 from public.relatos where categoria is not null) then
    raise exception 'public.relatos.categoria tem valores; mapeie-os antes de trocar o tipo';
  end if;
end
$$;

alter table public.relatos drop constraint relatos_categoria_fkey;
drop table public.categorias;

create table public.categorias (
  codigo    text primary key check (codigo ~ '^[A-Z][A-Z_]*$'),
  descricao text not null,
  destino   text not null check (destino in ('GERENTE', 'GERENTE_E_RH', 'RH', 'AGREGADO')),
  urgente   boolean not null default false
);

comment on table public.categorias is 'Taxonomia única de categorias de relatos e alertas.';
comment on column public.categorias.destino is 'Quem recebe: GERENTE, GERENTE_E_RH, RH ou AGREGADO (só entra em indicadores).';
comment on column public.categorias.urgente is 'Exige tratamento imediato pelo RH.';

insert into public.categorias (codigo, descricao, destino, urgente) values
  ('CANSACO',          'Cansaço físico ou mental',                         'GERENTE',      false),
  ('SOBRECARGA',       'Excesso de tarefas, ritmo ou jornada',             'GERENTE',      false),
  ('DESMOTIVACAO',     'Falta de motivação ou de propósito',               'GERENTE',      false),
  ('DUVIDA',           'Dúvida sobre rotina, processo ou benefício',       'GERENTE',      false),
  ('CONFLITO_COLEGA',  'Conflito com colega de trabalho',                  'GERENTE',      false),
  ('VONTADE_DE_SAIR',  'Intenção de deixar a empresa',                     'GERENTE_E_RH', false),
  ('CONFLITO_CHEFIA',  'Conflito com a chefia',                            'RH',           false),
  ('ASSEDIO',          'Assédio moral ou sexual',                          'RH',           true),
  ('HUMILHACAO',       'Humilhação ou constrangimento',                    'RH',           true),
  ('SITUACAO_GRAVE',   'Situação grave: risco à integridade ou à saúde',   'RH',           true),
  ('ELOGIO',           'Elogio ou experiência positiva',                   'AGREGADO',     false),
  ('NAO_CLASSIFICADO', 'Relato que não se encaixa em nenhuma categoria',   'RH',           false);

-- relatos.categoria: bigint -> text com FK para a taxonomia (coluna vazia, ver guard).
alter table public.relatos
  alter column categoria type text using null,
  add constraint relatos_categoria_fkey
    foreign key (categoria) references public.categorias (codigo);
create index relatos_categoria_idx on public.relatos (categoria);

-- Mapeamento dos rótulos legados de relatos_ia (comparação em maiúsculas, sem
-- espaços nas pontas). Fica em tabela para ser auditável e revisável.
create table public.categorias_legado_mapa (
  valor_legado text primary key,
  codigo       text not null references public.categorias (codigo)
);

comment on table public.categorias_legado_mapa is 'De-para dos rótulos antigos de relatos_ia para a taxonomia única.';

insert into public.categorias_legado_mapa (valor_legado, codigo) values
  ('DESABAFO LEVE',             'CANSACO'),
  ('RECLAMAÇÃO SOBRE A ROTINA', 'SOBRECARGA'),
  ('PEDIDO DE APOIO',           'DUVIDA'),
  ('RELATO FRAGMENTADO',        'NAO_CLASSIFICADO'),
  ('RELATO CONTRADITÓRIO',      'NAO_CLASSIFICADO'),
  ('PEDIDO INCERTO',            'NAO_CLASSIFICADO'),
  ('REFERÊNCIA PERDIDA',        'NAO_CLASSIFICADO'),
  ('CAUSA INDEFINIDA',          'NAO_CLASSIFICADO'),
  ('VAGO',                      'NAO_CLASSIFICADO');

-- Colunas originais ("CATEGORIA", "CATEGORIA (REVISOR)") ficam intactas.
-- A categoria do revisor tem prioridade sobre a do codificador.
-- Linhas sem rótulo continuam com categoria_codigo nulo.
alter table public.relatos_ia
  add column categoria_codigo text references public.categorias (codigo);
create index relatos_ia_categoria_codigo_idx on public.relatos_ia (categoria_codigo);
create index categorias_legado_mapa_codigo_idx on public.categorias_legado_mapa (codigo);

update public.relatos_ia r
set categoria_codigo = m.codigo
from public.categorias_legado_mapa m
where m.valor_legado = upper(btrim(coalesce(r."CATEGORIA (REVISOR)", r."CATEGORIA")));

-- Nenhum rótulo legado pode ficar sem mapeamento.
do $$
declare
  sem_mapa text;
begin
  select string_agg(distinct upper(btrim(coalesce("CATEGORIA (REVISOR)", "CATEGORIA"))), ', ')
    into sem_mapa
  from public.relatos_ia
  where coalesce("CATEGORIA (REVISOR)", "CATEGORIA") is not null
    and categoria_codigo is null;
  if sem_mapa is not null then
    raise exception 'Rótulos de relatos_ia sem mapeamento: %', sem_mapa;
  end if;
end
$$;

-- Tabelas novas nascem fechadas, como o resto do schema (H26).
alter table public.categorias enable row level security;
alter table public.categorias_legado_mapa enable row level security;
revoke all on public.categorias, public.categorias_legado_mapa from anon, authenticated;
create policy negar_anon_authenticated on public.categorias as restrictive for all
  to anon, authenticated using (false) with check (false);
create policy negar_anon_authenticated on public.categorias_legado_mapa as restrictive for all
  to anon, authenticated using (false) with check (false);
