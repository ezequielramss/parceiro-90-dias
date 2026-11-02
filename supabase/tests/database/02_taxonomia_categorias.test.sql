-- H27 — taxonomia única de categorias.
-- Rodar: supabase test db
begin;
select plan(9);

select has_table('public', 'categorias', 'tabela de taxonomia existe');
select columns_are('public', 'categorias', array['codigo', 'descricao', 'destino', 'urgente'],
                   'categorias tem codigo, descricao, destino e urgente');

select set_eq(
  $$select codigo, destino from public.categorias$$,
  $$values ('CANSACO', 'GERENTE'), ('SOBRECARGA', 'GERENTE'), ('DESMOTIVACAO', 'GERENTE'),
           ('DUVIDA', 'GERENTE'), ('CONFLITO_COLEGA', 'GERENTE'),
           ('VONTADE_DE_SAIR', 'GERENTE_E_RH'),
           ('CONFLITO_CHEFIA', 'RH'), ('ASSEDIO', 'RH'), ('HUMILHACAO', 'RH'), ('SITUACAO_GRAVE', 'RH'),
           ('ELOGIO', 'AGREGADO'),
           ('NAO_CLASSIFICADO', 'RH')$$,
  'as 12 categorias com o destino certo'
);

select set_eq(
  $$select codigo from public.categorias where urgente$$,
  array['ASSEDIO', 'HUMILHACAO', 'SITUACAO_GRAVE'],
  'só ASSEDIO, HUMILHACAO e SITUACAO_GRAVE são urgentes'
);

select fk_ok('public', 'relatos', 'categoria', 'public', 'categorias', 'codigo',
             'relatos.categoria referencia categorias.codigo');
select fk_ok('public', 'relatos_ia', 'categoria_codigo', 'public', 'categorias', 'codigo',
             'relatos_ia.categoria_codigo referencia categorias.codigo');

select throws_ok(
  $$insert into public.categorias (codigo, descricao, destino) values ('X', 'x', 'LOJA')$$,
  '23514', null, 'destino fora da lista é recusado'
);

select is_empty(
  $$select 1 from public.relatos_ia
    where coalesce("CATEGORIA (REVISOR)", "CATEGORIA") is not null and categoria_codigo is null$$,
  'todo rótulo legado de relatos_ia foi mapeado'
);

select is_empty(
  $$select 1 from public.relatos_ia
    where coalesce("CATEGORIA (REVISOR)", "CATEGORIA") is null and categoria_codigo is not null$$,
  'linhas sem rótulo legado não ganharam categoria inventada'
);

select * from finish();
rollback;
