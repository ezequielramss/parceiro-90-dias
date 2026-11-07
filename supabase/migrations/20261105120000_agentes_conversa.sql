-- Java E — Menu de agentes: o colaborador escolhe o que quer fazer.
--
-- Só mudanças aditivas: nenhuma linha é alterada e nada é apagado. A tabela continua fechada (H26).
-- O novo estado EM_AGENTE guarda o código do agente ativo. Ele vale até a pessoa concluir, voltar ao
-- menu ou ficar 30 minutos sem escrever: o backend decide isso pela data atualizado_em ao ler.

alter table public.conversa_estado
  add column agente text check (agente is null or btrim(agente) <> '');

comment on column public.conversa_estado.agente is
  'Código do agente ativo (ex.: RELATO, ELOGIO). Preenchido só em EM_AGENTE.';

alter table public.conversa_estado
  drop constraint conversa_estado_estado_check,
  add constraint conversa_estado_estado_check
    check (estado in ('LIVRE', 'AGUARDANDO_HUMOR', 'AGUARDANDO_CONFIRMACAO_RELATO', 'EM_AGENTE'));

alter table public.conversa_estado
  drop constraint conversa_estado_coerente,
  add constraint conversa_estado_coerente check (
    (estado = 'LIVRE' and checkin_id is null and relato_id is null and agente is null)
    or (estado = 'AGUARDANDO_HUMOR' and checkin_id is not null and relato_id is null and agente is null)
    or (estado = 'AGUARDANDO_CONFIRMACAO_RELATO' and relato_id is not null and checkin_id is null and agente is null)
    or (estado = 'EM_AGENTE' and agente is not null and checkin_id is null and relato_id is null));
