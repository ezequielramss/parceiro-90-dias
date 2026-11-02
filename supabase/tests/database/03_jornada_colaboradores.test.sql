-- Etapa 1 — H02, H31, H39. Dados 100% fictícios (+5500000000xxx).
-- Rodar: supabase test db
begin;
select plan(34);

-- As sete tabelas existem e estão fechadas para anon e authenticated.
-- (01_negar_por_padrao.test.sql repete a prova operação por operação em todas as tabelas.)
select has_table('public', t, format('tabela public.%s existe', t))
from unnest(array['colaboradores', 'consentimentos', 'mensagens', 'checkins',
                  'relatos', 'alertas', 'auditoria']) as t;

select is(
  (select count(*)::int
   from unnest(array['colaboradores', 'consentimentos', 'mensagens', 'checkins',
                     'relatos', 'alertas', 'auditoria']) as t
   where has_table_privilege('anon', format('public.%I', t), 'select, insert, update, delete, truncate')
      or has_table_privilege('authenticated', format('public.%I', t), 'select, insert, update, delete, truncate')),
  0,
  'anon e authenticated não têm nenhum privilégio nas sete tabelas novas'
);

-- Índices nas chaves estrangeiras.
select is_empty(
  $$select c.conrelid::regclass, c.conname
    from pg_constraint c
    where c.contype = 'f'
      and c.conrelid::regclass::text in ('consentimentos', 'mensagens', 'checkins', 'relatos', 'alertas')
      and not exists (
        select 1 from pg_index i
        where i.indrelid = c.conrelid
          and (i.indkey::int2[])[0:array_length(c.conkey, 1) - 1] = c.conkey)$$,
  'toda chave estrangeira das tabelas novas tem índice'
);

-- Dados fictícios para os testes de comportamento.
insert into public.colaboradores (id, nome, telefone, loja, turno, data_inicio)
values ('00000000-0000-0000-0000-0000000000a1', 'Pessoa Ficticia Um', '+5500000000001', 'Loja Teste', 'Noite', '2026-10-01');

insert into public.mensagens (wamid, colaborador_id, direcao, tipo, texto)
values ('wamid.TESTE-1', '00000000-0000-0000-0000-0000000000a1', 'ENTRADA', 'text', 'texto ficticio');

-- Telefone.
select throws_ok(
  $$insert into public.colaboradores (nome, telefone, loja, data_inicio)
    values ('Pessoa Ficticia Dois', '+5500000000001', 'Loja Teste', '2026-10-01')$$,
  '23505', null, 'telefone repetido é rejeitado'
);
select throws_ok(
  $$insert into public.colaboradores (nome, telefone, loja, data_inicio)
    values ('Pessoa Ficticia Tres', '5500000000003', 'Loja Teste', '2026-10-01')$$,
  '23514', null, 'telefone fora do E.164 (sem +) é rejeitado'
);
select throws_ok(
  $$insert into public.colaboradores (nome, telefone, loja, data_inicio)
    values ('Pessoa Ficticia Quatro', '+55 00 0000-0004', 'Loja Teste', '2026-10-01')$$,
  '23514', null, 'telefone com espaço ou traço é rejeitado'
);

-- wamid (idempotência).
select throws_ok(
  $$insert into public.mensagens (wamid, colaborador_id, direcao, tipo)
    values ('wamid.TESTE-1', '00000000-0000-0000-0000-0000000000a1', 'ENTRADA', 'text')$$,
  '23505', null, 'wamid repetido é rejeitado'
);
select lives_ok(
  $$insert into public.mensagens (wamid, colaborador_id, direcao, tipo)
    values ('wamid.TESTE-1', '00000000-0000-0000-0000-0000000000a1', 'ENTRADA', 'text')
    on conflict (wamid) do nothing$$,
  'reprocessar o mesmo wamid com on conflict do nothing não quebra'
);
select is((select count(*)::int from public.mensagens where wamid = 'wamid.TESTE-1'), 1,
          'continua existindo uma única mensagem com aquele wamid');
select has_index('public', 'mensagens', 'mensagens_wamid_key', 'wamid tem índice único');
select throws_ok(
  $$insert into public.mensagens (wamid, colaborador_id, direcao, tipo)
    values ('wamid.TESTE-2', '00000000-0000-0000-0000-0000000000a1', 'LADO', 'text')$$,
  '23514', null, 'direção fora de ENTRADA/SAIDA é rejeitada'
);

-- Consentimento: um ativo por vez; retirada ("parar") libera novo aceite.
insert into public.consentimentos (colaborador_id, texto_aceito)
values ('00000000-0000-0000-0000-0000000000a1', 'Termo ficticio v1');
select throws_ok(
  $$insert into public.consentimentos (colaborador_id, texto_aceito)
    values ('00000000-0000-0000-0000-0000000000a1', 'Termo ficticio v1')$$,
  '23505', null, 'só um consentimento ativo por colaborador'
);
update public.consentimentos set retirado_em = now() + interval '1 minute'
where colaborador_id = '00000000-0000-0000-0000-0000000000a1';
select lives_ok(
  $$insert into public.consentimentos (colaborador_id, texto_aceito)
    values ('00000000-0000-0000-0000-0000000000a1', 'Termo ficticio v2')$$,
  'depois de "parar", um novo aceite é aceito'
);

-- Check-ins.
select throws_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada) values ('00000000-0000-0000-0000-0000000000a1', 0)$$,
  '23514', null, 'dia da jornada 0 é rejeitado'
);
select throws_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada) values ('00000000-0000-0000-0000-0000000000a1', 91)$$,
  '23514', null, 'dia da jornada 91 é rejeitado'
);
select throws_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada, humor) values ('00000000-0000-0000-0000-0000000000a1', 5, 6)$$,
  '23514', null, 'humor 6 é rejeitado'
);
select lives_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada) values ('00000000-0000-0000-0000-0000000000a1', 90)$$,
  'check-in do dia 90 sem humor é aceito'
);

-- Relatos: retenção de 90 dias e taxonomia.
insert into public.relatos (colaborador_id, mensagem_id, texto, categoria, criado_em, expira_em)
select '00000000-0000-0000-0000-0000000000a1', id, 'relato ficticio', 'SOBRECARGA',
       '2026-10-01 12:00:00+00', '2030-01-01 00:00:00+00'
from public.mensagens where wamid = 'wamid.TESTE-1';

select is((select expira_em from public.relatos where texto = 'relato ficticio'),
          '2026-12-30 12:00:00+00'::timestamptz,
          'expira_em = criado_em + 90 dias, mesmo se o insert mandar outro valor');
update public.relatos set expira_em = '2099-01-01' where texto = 'relato ficticio';
select is((select expira_em from public.relatos where texto = 'relato ficticio'),
          '2026-12-30 12:00:00+00'::timestamptz,
          'expira_em não pode ser estendido por update');
select is((select estado from public.relatos where texto = 'relato ficticio'), 'NOVO',
          'relato nasce no estado NOVO');
select throws_ok(
  $$insert into public.relatos (colaborador_id, mensagem_id, texto, categoria)
    select '00000000-0000-0000-0000-0000000000a1', id, 'x', 'INEXISTENTE' from public.mensagens limit 1$$,
  '23503', null, 'categoria fora da taxonomia é rejeitada'
);

-- Alertas.
insert into public.alertas (relato_id, destino, atualizado_em)
select id, 'GERENTE_E_RH', '2020-01-01' from public.relatos where texto = 'relato ficticio';
select throws_ok(
  $$insert into public.alertas (relato_id, destino) select id, 'LOJA' from public.relatos limit 1$$,
  '23514', null, 'destino fora de GERENTE, RH ou GERENTE_E_RH é rejeitado'
);
update public.alertas set estado = 'ATENDIDO';
select ok((select atualizado_em > '2020-01-01' from public.alertas limit 1),
          'atualizado_em muda sozinho no update do alerta');

-- Auditoria: só inserção, inclusive para o backend.
insert into public.auditoria (quem, acao, objeto) values ('teste', 'CRIOU', 'colaborador/ficticio');
select throws_ok($$update public.auditoria set quem = 'outro'$$, '42501', null, 'auditoria recusa UPDATE');
select throws_ok($$delete from public.auditoria$$, '42501', null, 'auditoria recusa DELETE');
select throws_ok($$truncate public.auditoria$$, '42501', null, 'auditoria recusa TRUNCATE');

set local role service_role;
select lives_ok($$insert into public.auditoria (quem, acao, objeto) values ('backend', 'LEU', 'relato/1')$$,
                'service_role insere na auditoria');
select throws_ok($$delete from public.auditoria$$, '42501', null, 'service_role não apaga auditoria');
reset role;

select * from finish();
rollback;
