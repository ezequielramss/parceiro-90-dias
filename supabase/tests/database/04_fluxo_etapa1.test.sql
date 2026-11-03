-- Etapa 1 — Migração 2 (fluxo completo). Dados 100% fictícios (+5511999990xxx).
-- Rodar: supabase test db
begin;
select no_plan();

-- Fechamento (H26) nas tabelas novas. O 01_negar_por_padrao repete a prova operação por operação.
select ok(c.relrowsecurity, format('RLS ligado em public.%s', t))
from unnest(array['responsaveis', 'conversa_estado', 'alerta_entregas']) as t
join pg_class c on c.relname = t and c.relnamespace = 'public'::regnamespace;

select is(
  (select count(*)::int
   from unnest(array['responsaveis', 'conversa_estado', 'alerta_entregas']) as t
   where has_table_privilege('anon', format('public.%I', t), 'select, insert, update, delete, truncate')
      or has_table_privilege('authenticated', format('public.%I', t), 'select, insert, update, delete, truncate')),
  0,
  'anon e authenticated não têm nenhum privilégio nas tabelas novas'
);

select ok(exists (select 1 from pg_policies
                  where schemaname = 'public' and tablename = t
                    and policyname = 'negar_anon_authenticated' and permissive = 'RESTRICTIVE'),
          format('public.%s tem a policy restritiva', t))
from unnest(array['responsaveis', 'conversa_estado', 'alerta_entregas']) as t;

select function_privs_are('public', 'normalizar_loja', array['text'], r.rolname, array[]::text[],
                          format('%s não executa normalizar_loja', r.rolname))
from (values ('anon'::name), ('authenticated'::name)) as r(rolname);

select is_empty(
  $$select c.conrelid::regclass, c.conname
    from pg_constraint c
    where c.contype = 'f'
      and c.conrelid::regclass::text in ('responsaveis', 'conversa_estado', 'alerta_entregas', 'checkins')
      and not exists (
        select 1 from pg_index i
        where i.indrelid = c.conrelid
          and (i.indkey::int2[])[0:array_length(c.conkey, 1) - 1] = c.conkey)$$,
  'toda chave estrangeira das tabelas novas e alteradas tem índice'
);

set local role anon;
select throws_ok($$select * from public.responsaveis$$, '42501', null, 'anon não lê responsaveis');
reset role;
set local role authenticated;
select throws_ok($$insert into public.alerta_entregas (alerta_id, responsavel_id) values (1, gen_random_uuid())$$,
                 '42501', null, 'authenticated não grava alerta_entregas');
reset role;

-- Dados fictícios.
insert into public.colaboradores (id, nome, telefone, loja, data_inicio) values
  ('00000000-0000-0000-0000-0000000000c1', 'Pessoa Ficticia Um', '+5511999990001', ' Loja  Centro ', '2026-11-01'),
  ('00000000-0000-0000-0000-0000000000c2', 'Pessoa Ficticia Dois', '+5511999990002', 'Loja Norte', '2026-11-01');

insert into public.mensagens (id, wamid, colaborador_id, direcao, tipo, texto) overriding system value values
  (9001, 'wamid.FIC-1', '00000000-0000-0000-0000-0000000000c1', 'ENTRADA', 'text', 'relato ficticio'),
  (9002, 'wamid.FIC-2', '00000000-0000-0000-0000-0000000000c1', 'SAIDA', 'text', 'checkin ficticio');

insert into public.checkins (id, colaborador_id, dia_jornada, mensagem_id) overriding system value values
  (9101, '00000000-0000-0000-0000-0000000000c1', 1, 9002),
  (9102, '00000000-0000-0000-0000-0000000000c2', 1, null);

insert into public.relatos (id, colaborador_id, mensagem_id, texto, categoria, motivo) overriding system value values
  (9201, '00000000-0000-0000-0000-0000000000c1', 9001, 'relato ficticio', 'CANSACO', 'cansaço relatado');

-- a) responsaveis
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel) values ('Gerente Ficticio', '+5511999990010', 'GERENTE')$$,
  '23514', null, 'GERENTE sem loja é recusado');
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel, loja) values ('Gerente Ficticio', '+5511999990010', 'GERENTE', '  ')$$,
  '23514', null, 'GERENTE com loja em branco é recusado');
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel, loja) values ('RH Ficticio', '+5511999990011', 'RH', 'Loja Centro')$$,
  '23514', null, 'RH com loja é recusado');
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel) values ('Outro Papel', '+5511999990012', 'DIRETOR')$$,
  '23514', null, 'papel fora de GERENTE/RH é recusado');
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel) values ('RH Ficticio', '11999990011', 'RH')$$,
  '23514', null, 'telefone fora do E.164 é recusado');
select lives_ok(
  $$insert into public.responsaveis (id, nome, telefone, papel, loja) values
      ('00000000-0000-0000-0000-0000000000a1', 'Gerente Ficticio', '+5511999990010', 'GERENTE', 'LOJA CENTRO'),
      ('00000000-0000-0000-0000-0000000000a2', 'RH Ficticio', '+5511999990010', 'RH', null)$$,
  'o mesmo telefone pode ser GERENTE e RH');
select throws_ok(
  $$insert into public.responsaveis (nome, telefone, papel) values ('RH Repetido', '+5511999990010', 'RH')$$,
  '23505', null, 'o mesmo telefone duas vezes como RH é recusado');
select is((select ativo from public.responsaveis where id = '00000000-0000-0000-0000-0000000000a1'), true,
          'responsável nasce ativo');

-- Lojas
select is(public.normalizar_loja(' Loja  Centro '), public.normalizar_loja('LOJA CENTRO'),
          'normalizar_loja ignora caixa e espaços');
select isnt(public.normalizar_loja('São Paulo'), public.normalizar_loja('Sao Paulo'),
            'normalizar_loja mantém acentos (decisão registrada na proposta)');
select results_eq(
  $$select r.id from public.responsaveis r join public.colaboradores c
      on public.normalizar_loja(r.loja) = public.normalizar_loja(c.loja)
    where c.id = '00000000-0000-0000-0000-0000000000c1' and r.papel = 'GERENTE' and r.ativo$$,
  $$values ('00000000-0000-0000-0000-0000000000a1'::uuid)$$,
  'gerente da loja é encontrado apesar da diferença de caixa e espaços');
select has_index('public', 'responsaveis', 'responsaveis_gerente_loja_idx', 'índice de gerentes por loja existe');

-- c) checkins
select throws_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada) values ('00000000-0000-0000-0000-0000000000c1', 1)$$,
  '23505', null, 'check-in do mesmo dia duplicado é recusado');
select lives_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada) values ('00000000-0000-0000-0000-0000000000c1', 1)
    on conflict (colaborador_id, dia_jornada) do nothing$$,
  'on conflict do nothing no mesmo dia não quebra');
select is((select count(*)::int from public.checkins
           where colaborador_id = '00000000-0000-0000-0000-0000000000c1' and dia_jornada = 1), 1,
          'continua um check-in por dia');
select throws_ok(
  $$insert into public.checkins (colaborador_id, dia_jornada, mensagem_id) values ('00000000-0000-0000-0000-0000000000c1', 2, 99999)$$,
  '23503', null, 'mensagem_id inexistente é recusado');

-- d) relatos: valores padrão e falha da IA
select is((select confirmacao from public.relatos where id = 9201), 'AGUARDANDO', 'relato nasce AGUARDANDO');
select is((select sinais_graves from public.relatos where id = 9201), false, 'sinais_graves padrão é false');
select is((select falha_ia from public.relatos where id = 9201), false, 'falha_ia padrão é false');
select is((select confianca from public.relatos where id = 9201), null, 'confianca padrão é nula');
select throws_ok($$update public.relatos set confirmacao = 'TALVEZ' where id = 9201$$,
                 '23514', null, 'confirmacao fora da lista é recusada');
select throws_ok($$update public.relatos set confianca = 'altissima' where id = 9201$$,
                 '23514', null, 'confianca fora da lista é recusada');
select throws_ok($$update public.relatos set falha_ia = true where id = 9201$$,
                 '23514', null, 'falha_ia com categoria diferente de NAO_CLASSIFICADO é recusada');
select lives_ok($$update public.relatos set falha_ia = true, categoria = 'NAO_CLASSIFICADO' where id = 9201$$,
                'falha_ia com NAO_CLASSIFICADO é aceita');

-- b) conversa_estado
select lives_ok(
  $$insert into public.conversa_estado (colaborador_id) values ('00000000-0000-0000-0000-0000000000c1')$$,
  'conversa nasce LIVRE sem referências');
select is((select estado from public.conversa_estado where colaborador_id = '00000000-0000-0000-0000-0000000000c1'),
          'LIVRE', 'estado padrão é LIVRE');
select throws_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_HUMOR' where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  '23514', null, 'AGUARDANDO_HUMOR sem checkin_id é recusado');
select throws_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_CONFIRMACAO_RELATO' where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  '23514', null, 'AGUARDANDO_CONFIRMACAO_RELATO sem relato_id é recusado');
select throws_ok(
  $$update public.conversa_estado set checkin_id = 9101 where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  '23514', null, 'LIVRE com referência é recusado');
select throws_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_HUMOR', checkin_id = 9101, relato_id = 9201
    where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  '23514', null, 'AGUARDANDO_HUMOR com relato_id é recusado');
select throws_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_HUMOR', checkin_id = 9102
    where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  '23503', null, 'check-in de outro colaborador é recusado');
update public.conversa_estado set atualizado_em = '2020-01-01'
where colaborador_id = '00000000-0000-0000-0000-0000000000c1';
select lives_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_HUMOR', checkin_id = 9101
    where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  'AGUARDANDO_HUMOR com o próprio check-in é aceito');
select ok((select atualizado_em > '2020-01-02' from public.conversa_estado
           where colaborador_id = '00000000-0000-0000-0000-0000000000c1'),
          'atualizado_em muda sozinho no update');
select lives_ok(
  $$update public.conversa_estado set estado = 'AGUARDANDO_CONFIRMACAO_RELATO', checkin_id = null, relato_id = 9201
    where colaborador_id = '00000000-0000-0000-0000-0000000000c1'$$,
  'AGUARDANDO_CONFIRMACAO_RELATO com o próprio relato é aceito');
select throws_ok(
  $$insert into public.conversa_estado (colaborador_id) values ('00000000-0000-0000-0000-0000000000c1')$$,
  '23505', null, 'uma linha de estado por colaborador');

-- e) alertas
insert into public.alertas (id, relato_id, destino) overriding system value values (9301, 9201, 'RH');
select is((select urgente from public.alertas where id = 9301), false, 'alerta nasce não urgente');
select is((select resolvido_em from public.alertas where id = 9301), null, 'alerta nasce não resolvido');
select throws_ok($$update public.alertas set resolvido_em = now() where id = 9301$$,
                 '23514', null, 'resolvido_em sem resolvido_por é recusado');
select throws_ok($$update public.alertas set resolvido_em = '2000-01-01', resolvido_por = 'rh-ficticio' where id = 9301$$,
                 '23514', null, 'resolvido_em antes de criado_em é recusado');
select lives_ok($$update public.alertas set estado = 'ATENDIDO' where id = 9301$$,
                'ATENDIDO (contato realizado) não exige resolução');

-- f) alerta_entregas
select lives_ok(
  $$insert into public.alerta_entregas (alerta_id, responsavel_id, wamid_envio)
    values (9301, '00000000-0000-0000-0000-0000000000a2', 'wamid.ENTREGA-1')$$,
  'entrega registrada');
select is((select falha_envio from public.alerta_entregas where wamid_envio = 'wamid.ENTREGA-1'), false,
          'falha_envio padrão é false');
select throws_ok(
  $$insert into public.alerta_entregas (alerta_id, responsavel_id) values (9301, '00000000-0000-0000-0000-0000000000a2')$$,
  '23505', null, 'entrega duplicada (mesmo alerta e responsável) é recusada');
select throws_ok(
  $$insert into public.alerta_entregas (alerta_id, responsavel_id, wamid_envio)
    values (9301, '00000000-0000-0000-0000-0000000000a1', 'wamid.ENTREGA-1')$$,
  '23505', null, 'o mesmo wamid em duas entregas é recusado');
select throws_ok(
  $$update public.alerta_entregas set resposta = 'TALVEZ', respondido_em = now() where wamid_envio = 'wamid.ENTREGA-1'$$,
  '23514', null, 'resposta fora da lista é recusada');
select throws_ok(
  $$update public.alerta_entregas set resposta = 'CONVERSEI' where wamid_envio = 'wamid.ENTREGA-1'$$,
  '23514', null, 'resposta sem respondido_em é recusada');
select lives_ok(
  $$update public.alerta_entregas set resposta = 'NAO_CONSEGUI', respondido_em = now() where wamid_envio = 'wamid.ENTREGA-1'$$,
  'NAO_CONSEGUI registrado');
select lives_ok(
  $$update public.alerta_entregas set resposta = 'CONVERSEI', respondido_em = now() where wamid_envio = 'wamid.ENTREGA-1'$$,
  'CONVERSEI depois de NAO_CONSEGUI é aceito');
select throws_ok(
  $$insert into public.alerta_entregas (alerta_id, responsavel_id, falha_envio, wamid_envio)
    values (9301, '00000000-0000-0000-0000-0000000000a1', true, 'wamid.FALHOU')$$,
  '23514', null, 'envio com falha não tem wamid');
select lives_ok(
  $$insert into public.alerta_entregas (alerta_id, responsavel_id, falha_envio)
    values (9301, '00000000-0000-0000-0000-0000000000a1', true)$$,
  'envio com falha registrado sem wamid');

select * from finish();
rollback;
