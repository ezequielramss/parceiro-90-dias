-- Java E — estado EM_AGENTE e coluna agente em conversa_estado. Dados 100% fictícios (+5511999990xxx).
-- Rodar: supabase test db
begin;
select no_plan();

-- Fechamento (H26) continua. O 01_negar_por_padrao repete a prova operação por operação.
select ok(c.relrowsecurity, 'RLS continua ligado em public.conversa_estado')
from pg_class c
where c.relname = 'conversa_estado' and c.relnamespace = 'public'::regnamespace;

select table_privs_are('public', 'conversa_estado', r.rolname, array[]::text[],
                       format('%s sem nenhum privilégio em conversa_estado', r.rolname))
from (values ('anon'::name), ('authenticated'::name)) as r(rolname);

select col_is_null('public', 'conversa_estado', 'agente', 'agente é nula');

insert into public.colaboradores (id, nome, telefone, loja, data_inicio) values
  ('00000000-0000-0000-0000-0000000000e1', 'Pessoa Ficticia Agente', '+5511999990061', 'Loja Centro', '2026-11-01');

select lives_ok(
  $$insert into public.conversa_estado (colaborador_id, estado, agente)
    values ('00000000-0000-0000-0000-0000000000e1', 'EM_AGENTE', 'RELATO')$$,
  'EM_AGENTE com o código do agente é aceito');
select is((select agente from public.conversa_estado where colaborador_id = '00000000-0000-0000-0000-0000000000e1'),
          'RELATO', 'o código do agente fica gravado');

select throws_ok(
  $$update public.conversa_estado set agente = null where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  '23514', null, 'EM_AGENTE sem agente é recusado');
select throws_ok(
  $$update public.conversa_estado set agente = '  ' where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  '23514', null, 'agente em branco é recusado');
select throws_ok(
  $$update public.conversa_estado set estado = 'LIVRE' where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  '23514', null, 'LIVRE com agente é recusado');
select lives_ok(
  $$update public.conversa_estado set estado = 'LIVRE', agente = null
    where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  'voltar a LIVRE limpa o agente');
select throws_ok(
  $$update public.conversa_estado set estado = 'OUTRO' where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  '23514', null, 'estado fora da lista é recusado');

-- Os estados que já existiam continuam valendo, sem agente.
insert into public.checkins (id, colaborador_id, dia_jornada) overriding system value values
  (9301, '00000000-0000-0000-0000-0000000000e1', 1);
select lives_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_HUMOR', checkin_id = 9301
    where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  'AGUARDANDO_HUMOR continua aceito');
select throws_ok(
  $$update public.conversa_estado set agente = 'RELATO' where colaborador_id = '00000000-0000-0000-0000-0000000000e1'$$,
  '23514', null, 'AGUARDANDO_HUMOR com agente é recusado');

select * from finish();
rollback;
