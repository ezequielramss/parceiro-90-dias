-- Etapa 1 — Java C: acessos ao dashboard e retenção. Dados 100% fictícios (+5511999990xxx).
-- Rodar: supabase test db
begin;
select no_plan();

-- Fechamento (H26). O 01_negar_por_padrao repete a prova operação por operação.
select ok(c.relrowsecurity, 'RLS ligado em public.acessos_dashboard')
from pg_class c
where c.relname = 'acessos_dashboard' and c.relnamespace = 'public'::regnamespace;

select ok(exists (select 1 from pg_policies
                  where schemaname = 'public' and tablename = 'acessos_dashboard'
                    and policyname = 'negar_anon_authenticated' and permissive = 'RESTRICTIVE'),
          'public.acessos_dashboard tem a policy restritiva');

select table_privs_are('public', 'acessos_dashboard', r.rolname, array[]::text[],
                       format('%s sem nenhum privilégio em acessos_dashboard', r.rolname))
from (values ('anon'::name), ('authenticated'::name)) as r(rolname);

select col_is_pk('public', 'acessos_dashboard', 'user_id', 'user_id é a chave primária');
select fk_ok('public', 'acessos_dashboard', 'user_id', 'auth', 'users', 'id', 'user_id aponta para auth.users');

-- Usuários fictícios do Supabase Auth (sem senha).
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a1', 'gerente.ficticio@exemplo.invalid'),
  ('00000000-0000-0000-0000-0000000000a2', 'rh.ficticio@exemplo.invalid'),
  ('00000000-0000-0000-0000-0000000000a3', 'outro.ficticio@exemplo.invalid');

select lives_ok(
  $$insert into public.acessos_dashboard (user_id, papel, loja, nome)
    values ('00000000-0000-0000-0000-0000000000a1', 'GERENTE', public.normalizar_loja(' Loja  Centro '), 'Gerente Ficticio')$$,
  'GERENTE com loja normalizada é aceito');
select is((select loja from public.acessos_dashboard where user_id = '00000000-0000-0000-0000-0000000000a1'),
          'loja centro', 'a loja fica gravada normalizada');
select lives_ok(
  $$insert into public.acessos_dashboard (user_id, papel) values ('00000000-0000-0000-0000-0000000000a2', 'RH')$$,
  'RH sem loja é aceito');
select isnt((select criado_em from public.acessos_dashboard where user_id = '00000000-0000-0000-0000-0000000000a2'),
            null, 'criado_em tem valor padrão');

select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel) values ('00000000-0000-0000-0000-0000000000a3', 'GERENTE')$$,
  '23514', null, 'GERENTE sem loja é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel, loja) values ('00000000-0000-0000-0000-0000000000a3', 'GERENTE', '  ')$$,
  '23514', null, 'GERENTE com loja em branco é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel, loja) values ('00000000-0000-0000-0000-0000000000a3', 'GERENTE', 'Loja Centro')$$,
  '23514', null, 'GERENTE com loja sem normalizar é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel, loja) values ('00000000-0000-0000-0000-0000000000a3', 'RH', 'loja centro')$$,
  '23514', null, 'RH com loja é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel) values ('00000000-0000-0000-0000-0000000000a3', 'DIRETOR')$$,
  '23514', null, 'papel fora de GERENTE/RH é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel) values ('00000000-0000-0000-0000-0000000000a9', 'RH')$$,
  '23503', null, 'usuário que não existe no Supabase Auth é recusado');
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel, nome) values ('00000000-0000-0000-0000-0000000000a3', 'RH', '  ')$$,
  '23514', null, 'nome em branco é recusado');

-- O gatilho legado handle_new_user cria uma linha em funcionarios para cada usuário novo.
delete from public.funcionarios where id_funcionarios = '00000000-0000-0000-0000-0000000000a2';
delete from auth.users where id = '00000000-0000-0000-0000-0000000000a2';
select is((select count(*)::int from public.acessos_dashboard where user_id = '00000000-0000-0000-0000-0000000000a2'),
          0, 'apagar o usuário no Supabase Auth apaga o acesso');

set local role anon;
select throws_ok($$select * from public.acessos_dashboard$$, '42501', null, 'anon não lê acessos_dashboard');
reset role;
set local role authenticated;
select throws_ok(
  $$insert into public.acessos_dashboard (user_id, papel) values ('00000000-0000-0000-0000-0000000000a3', 'RH')$$,
  '42501', null, 'authenticated não se dá acesso ao painel');
reset role;

-- Retenção: o texto do relato aceita nulo; categoria, motivo e datas continuam como antes.
select col_is_null('public', 'relatos', 'texto', 'relatos.texto aceita nulo (retenção de 90 dias)');
select col_not_null('public', 'relatos', 'categoria', 'relatos.categoria continua obrigatória');

insert into public.colaboradores (id, nome, telefone, loja, data_inicio) values
  ('00000000-0000-0000-0000-0000000000c5', 'Pessoa Ficticia', '+5511999990051', 'Loja Centro', '2026-07-01');
insert into public.mensagens (id, wamid, colaborador_id, direcao, tipo, texto) overriding system value values
  (9501, 'wamid.FIC-RET-1', '00000000-0000-0000-0000-0000000000c5', 'ENTRADA', 'text', 'relato ficticio');
insert into public.relatos (id, colaborador_id, mensagem_id, texto, categoria, motivo, criado_em) overriding system value values
  (9601, '00000000-0000-0000-0000-0000000000c5', 9501, 'relato ficticio', 'CANSACO', 'cansaço relatado', now() - interval '91 days');

update public.relatos set texto = null, motivo = null where id = 9601;
select results_eq(
  $$select texto, motivo, categoria, expira_em = criado_em + interval '90 days' from public.relatos where id = 9601$$,
  $$values (null::text, null::text, 'CANSACO'::text, true)$$,
  'texto e motivo apagados; categoria e prazo continuam');

select * from finish();
rollback;
