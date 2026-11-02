-- H26 — Banco nega por padrão para anon e authenticated.
--
-- Decisão de arquitetura: o dashboard fala só com a API Java; nenhum navegador
-- acessa o banco. Por isso anon e authenticated não leem nem escrevem nada no
-- schema public. As regras de perfil (RH vê todas as lojas, GERENTE só a dele,
-- gerente envolvido não vê o relato) ficam no backend, que acessa o banco por
-- conexão de servidor (role dona das tabelas / service_role, que ignoram RLS).
--
-- Idempotente: pode rodar de novo sem efeito colateral.

-- 1. RLS ligado em todas as tabelas do schema public (inclusive as futuras
--    criadas antes desta migração rodar; as posteriores são cobertas pelo teste).
do $$
declare
  t record;
begin
  for t in
    select c.oid::regclass as tabela
    from pg_class c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
  loop
    execute format('alter table %s enable row level security', t.tabela);
  end loop;
end
$$;

-- 2. Remove toda policy permissiva que valha para anon, authenticated ou PUBLIC
--    e deixa a negação explícita com uma policy restritiva `false` por tabela
--    (documenta a intenção e zera o lint rls_enabled_no_policy).
do $$
declare
  p record;
  t record;
begin
  for p in
    select schemaname, tablename, policyname
    from pg_policies
    where schemaname = 'public'
      and permissive = 'PERMISSIVE'
      and roles && array['anon', 'authenticated', 'public']::name[]
  loop
    execute format('drop policy %I on %I.%I', p.policyname, p.schemaname, p.tablename);
  end loop;

  for t in
    select c.oid::regclass as tabela, c.relname
    from pg_class c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
      and not exists (select 1 from pg_policies
                      where schemaname = 'public' and tablename = c.relname
                        and policyname = 'negar_anon_authenticated')
  loop
    execute format(
      'create policy negar_anon_authenticated on %s as restrictive for all '
      'to anon, authenticated using (false) with check (false)', t.tabela);
  end loop;
end
$$;

-- 3. Revoga grants. Mesmo com RLS, TRUNCATE e REFERENCES não passam por policy,
--    então o grant precisa sair também.
revoke all on all tables    in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;

-- 4. Objetos criados no futuro pelo role postgres também nascem fechados.
--    (Os default privileges de supabase_admin só podem ser alterados pelo
--    próprio supabase_admin; o teste pgTAP pega qualquer tabela nova aberta.)
alter default privileges for role postgres in schema public
  revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke all on functions from public, anon, authenticated;

-- 5. handle_new_user é SECURITY DEFINER e estava exposta em /rest/v1/rpc.
--    O trigger em auth.users continua funcionando: EXECUTE não é checado
--    quando o trigger dispara.
revoke execute on function public.handle_new_user() from public, anon, authenticated;
