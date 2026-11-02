-- H26 — anon e authenticated não leem nem escrevem em nenhuma tabela de public.
-- Cobre todas as tabelas existentes no momento do teste, inclusive as futuras.
-- Rodar: supabase test db
begin;
select no_plan();

-- Estrutura: RLS ligado, nenhuma policy aberta, nenhum grant.
select ok(c.relrowsecurity, format('RLS ligado em public.%s', c.relname))
from pg_class c
where c.relnamespace = 'public'::regnamespace and c.relkind in ('r', 'p');

select is(
  (select count(*)::int from pg_policies
   where schemaname = 'public'
     and permissive = 'PERMISSIVE'
     and roles && array['anon', 'authenticated', 'public']::name[]),
  0,
  'nenhuma policy permissiva de public vale para anon, authenticated ou PUBLIC'
);

select ok(exists (select 1 from pg_policies p
                  where p.schemaname = 'public' and p.tablename = c.relname
                    and p.policyname = 'negar_anon_authenticated'
                    and p.permissive = 'RESTRICTIVE' and p.qual = 'false'),
          format('public.%s tem a policy restritiva negar_anon_authenticated', c.relname))
from pg_class c
where c.relnamespace = 'public'::regnamespace and c.relkind in ('r', 'p');

select table_privs_are('public', t.tablename, r.rolname, array[]::text[],
                       format('%s sem nenhum privilégio em public.%s', r.rolname, t.tablename))
from pg_tables t
cross join (values ('anon'::name), ('authenticated'::name)) as r(rolname)
where t.schemaname = 'public';

select sequence_privs_are('public', s.sequencename, r.rolname, array[]::text[],
                          format('%s sem nenhum privilégio em public.%s', r.rolname, s.sequencename))
from pg_sequences s
cross join (values ('anon'::name), ('authenticated'::name)) as r(rolname)
where s.schemaname = 'public';

select function_privs_are('public', 'handle_new_user', array[]::text[], r.rolname, array[]::text[],
                          format('%s não executa public.handle_new_user()', r.rolname))
from (values ('anon'::name), ('authenticated'::name)) as r(rolname);

-- Comportamento: cada operação é recusada com permission denied (42501).
-- A lista de comandos é montada como postgres e executada depois de trocar de role.
create temporary table _comandos on commit drop as
select t.tablename,
       op,
       case op
         when 'SELECT'   then format('select * from public.%I limit 1', t.tablename)
         when 'INSERT'   then format('insert into public.%I default values', t.tablename)
         when 'UPDATE'   then format('update public.%1$I set %2$I = %2$I', t.tablename, a.attname)
         when 'DELETE'   then format('delete from public.%I', t.tablename)
         when 'TRUNCATE' then format('truncate public.%I', t.tablename)
       end as sql
from pg_tables t
cross join unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE']) as op
cross join lateral (
  select attname from pg_attribute
  where attrelid = format('public.%I', t.tablename)::regclass and attnum > 0 and not attisdropped
  order by attnum limit 1
) a
where t.schemaname = 'public';
grant select on _comandos to anon, authenticated, service_role;

set local role anon;
select throws_ok(sql, '42501', null, format('anon: %s em public.%s é recusado', op, tablename))
from _comandos;
reset role;

set local role authenticated;
set local request.jwt.claims = '{"sub": "00000000-0000-0000-0000-000000000001", "role": "authenticated"}';
select throws_ok(sql, '42501', null, format('authenticated: %s em public.%s é recusado', op, tablename))
from _comandos;
reset role;

-- O backend (service_role) continua lendo tudo.
set local role service_role;
select lives_ok(sql, format('service_role: SELECT em public.%s funciona', tablename))
from _comandos where op = 'SELECT';
reset role;

select * from finish();
rollback;
