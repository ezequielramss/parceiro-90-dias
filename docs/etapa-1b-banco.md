# Etapa 1 — Migração 2 do banco (proposta)

Proposta para o fluxo completo da Etapa 1: check-in nos dias 1 a 10, relato por texto,
classificação, confirmação do colaborador e alerta ao gerente ou ao RH com os botões
"Conversei" e "Não consegui". **Ainda não existe migração**: ela só será escrita depois do
"aprovado".

Todas as mudanças são aditivas. Nada existente é apagado nem renomeado. A migração será um
arquivo novo, com timestamp maior que `20261102200100` (previsto:
`20261103120000_fluxo_etapa1.sql`).

## Resumo

| Item | Tipo | O que muda |
|---|---|---|
| `responsaveis` | tabela nova | quem recebe alertas (gerente da loja ou RH) |
| `conversa_estado` | tabela nova | em que ponto da conversa cada colaborador está |
| `alerta_entregas` | tabela nova | uma linha por alerta e por responsável que recebeu |
| `checkins` | colunas e constraint | um envio por dia de jornada; mensagem de saída |
| `relatos` | colunas | confirmação, confiança, sinais graves, falha da IA |
| `alertas` | colunas | urgência; resolução separada de "contato realizado" |
| `normalizar_loja(text)` | função nova | comparação confiável de lojas |

## a) `responsaveis`

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `uuid` | PK, `default gen_random_uuid()` |
| `nome` | `text` | obrigatório, não vazio |
| `telefone` | `text` | obrigatório, E.164: `^\+[1-9][0-9]{7,14}$` (mesmo check de `colaboradores.telefone`) |
| `papel` | `text` | obrigatório, `GERENTE` ou `RH` |
| `loja` | `text` | obrigatória para `GERENTE`; nula para `RH` |
| `ativo` | `boolean` | obrigatório, padrão `true` |
| `criado_em` | `timestamptz` | obrigatório, padrão `now()` |

Constraints:

- `unique (telefone, papel)`: o mesmo celular pode ser gerente e RH (o PO testa assim), mas
  não pode aparecer duas vezes no mesmo papel.
- `check ((papel = 'GERENTE' and loja is not null and btrim(loja) <> '') or (papel = 'RH' and loja is null))`.
  Um RH com loja seria ambíguo: o RH recebe de todas as lojas.

Índices:

- O `unique (telefone, papel)` já serve à busca por telefone, quando um responsável escreve ou
  toca num botão.
- `responsaveis_gerente_loja_idx` em `(normalizar_loja(loja)) where papel = 'GERENTE' and ativo`:
  é a busca "gerentes ativos da loja deste colaborador".

### Como comparar lojas

Hoje `colaboradores.loja` e `responsaveis.loja` são texto livre. "Centro", " centro" e
"CENTRO  " precisam ser a mesma loja. Proposta: uma função imutável,
`public.normalizar_loja(text)`, que tira os espaços das pontas, junta espaços repetidos e passa
tudo para minúsculas:

```sql
lower(regexp_replace(btrim(loja), '\s+', ' ', 'g'))
```

O backend compara sempre `normalizar_loja(colaboradores.loja) = normalizar_loja(responsaveis.loja)`,
e o índice acima torna essa busca rápida. A função segue o padrão H26: `set search_path = ''`
e sem `execute` para `public`, `anon` e `authenticated`.

Acentos ficam de fora ("São Paulo" e "Sao Paulo" continuam diferentes). Tirá-los exigiria a
extensão `unaccent`, que não é imutável e não serve para índice sem um invólucro próprio. Para
o volume da Etapa 1, o cadastro cuidadoso pelo RH basta.

**A longo prazo:** uma tabela `lojas (id, nome, ...)`, com `colaboradores.loja_id` e
`responsaveis.loja_id` como chaves estrangeiras. Isso elimina a comparação por texto e permite
cadastro de lojas pelo painel. Fica fora desta migração porque mudaria `colaboradores`, e as
mudanças aqui precisam ser aditivas.

## b) `conversa_estado`

Uma linha por colaborador.

| Coluna | Tipo | Regra |
|---|---|---|
| `colaborador_id` | `uuid` | PK e FK para `colaboradores(id)` |
| `estado` | `text` | obrigatório, padrão `LIVRE`; `LIVRE`, `AGUARDANDO_HUMOR` ou `AGUARDANDO_CONFIRMACAO_RELATO` |
| `checkin_id` | `bigint` | opcional, FK para `checkins` |
| `relato_id` | `bigint` | opcional, FK para `relatos` |
| `atualizado_em` | `timestamptz` | obrigatório, padrão `now()`; gatilho `tocar_atualizado_em` (já existe) |

Constraint de coerência (`conversa_estado_coerente`). Proponho a versão estrita, em que cada
estado aceita só a referência que faz sentido para ele:

| Estado | `checkin_id` | `relato_id` |
|---|---|---|
| `LIVRE` | nulo | nulo |
| `AGUARDANDO_HUMOR` | obrigatório | nulo |
| `AGUARDANDO_CONFIRMACAO_RELATO` | nulo | obrigatório |

Assim nunca sobra uma referência velha de um estado anterior. O Java B só precisa limpar o
campo ao trocar de estado.

**Mesmo colaborador:** para impedir que o estado de uma pessoa aponte para o check-in ou o
relato de outra, as chaves estrangeiras são compostas: `(checkin_id, colaborador_id)` referencia
`checkins (id, colaborador_id)` e `(relato_id, colaborador_id)` referencia
`relatos (id, colaborador_id)`. Para isso a migração cria `unique (id, colaborador_id)` em
`checkins` e em `relatos`, o que é aditivo, já que `id` é único. Com a coluna nula, a chave não
é verificada (comportamento padrão `MATCH SIMPLE`).

Índices: `(checkin_id, colaborador_id)` e `(relato_id, colaborador_id)`, nas mesmas colunas
das chaves estrangeiras compostas.

## c) `checkins`

- `unique (colaborador_id, dia_jornada)` (`checkins_um_por_dia`): o agendador insere a linha
  antes de enviar, com `on conflict do nothing`, e só envia se a inserção ocorreu. Assim o
  mesmo dia nunca gera dois envios.
- Coluna nova `mensagem_id bigint`, opcional, FK para `mensagens(id)`: a mensagem de saída do
  check-in. Índice próprio.
- `unique (id, colaborador_id)`, explicado no item b.

O índice atual `checkins_colaborador_id_dia_idx` fica redundante com a nova unicidade, mas não
é removido, porque a migração é aditiva. Pode sair numa limpeza futura.

Se já houver dias duplicados no banco, a constraint falha ao aplicar a migração. Em
produção a tabela foi criada em 02/11/2026 e ainda não recebeu check-ins.

## d) `relatos` (colunas novas)

| Coluna | Tipo | Regra |
|---|---|---|
| `confirmacao` | `text` | obrigatório, padrão `AGUARDANDO`; `AGUARDANDO`, `CONFIRMADO` ou `DESCARTADO` |
| `confianca` | `text` | opcional; `baixa`, `media` ou `alta` (os mesmos valores da saída da IA) |
| `sinais_graves` | `boolean` | obrigatório, padrão `false` |
| `falha_ia` | `boolean` | obrigatório, padrão `false` |

Sem coluna `resumo`: `motivo` já é o resumo neutro mostrado ao colaborador e ao RH.

Constraint proposta: `check (not falha_ia or categoria = 'NAO_CLASSIFICADO')`. Quando a IA
falha, o relato não pode carregar uma categoria inventada (é o que `Classificacao.falha()` da
IA 3 devolve).

Também entra `unique (id, colaborador_id)`, explicado no item b.

## e) `alertas` (colunas novas)

| Coluna | Tipo | Regra |
|---|---|---|
| `urgente` | `boolean` | obrigatório, padrão `false` |
| `resolvido_em` | `timestamptz` | opcional |
| `resolvido_por` | `text` | opcional |

O estado `ATENDIDO` continua existindo e significa contato realizado, não caso resolvido (H20).
A resolução fica nas duas colunas novas.

Constraints:

- `(resolvido_em is null) = (resolvido_por is null)`: as duas andam juntas.
- `resolvido_em >= criado_em`.

`resolvido_por` é texto porque ainda não existe tabela de usuários do painel (login é H32). Na
Etapa 1 ninguém marca resolução; as colunas ficam prontas para o painel.

## f) `alerta_entregas`

Uma linha por alerta e por responsável que recebeu a mensagem. Registra quem, quando e o
resultado (H17).

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `bigint` | PK, identity |
| `alerta_id` | `bigint` | obrigatório, FK para `alertas(id)` |
| `responsavel_id` | `uuid` | obrigatório, FK para `responsaveis(id)` |
| `enviado_em` | `timestamptz` | obrigatório, padrão `now()` |
| `wamid_envio` | `text` | opcional; wamid da mensagem enviada |
| `falha_envio` | `boolean` | obrigatório, padrão `false` |
| `resposta` | `text` | opcional; `CONVERSEI` ou `NAO_CONSEGUI` |
| `respondido_em` | `timestamptz` | opcional |

Constraints:

- `unique (alerta_id, responsavel_id)`: o mesmo responsável não recebe o mesmo alerta duas
  vezes. O índice dessa unicidade também atende à chave estrangeira de `alerta_id`.
- `(resposta is null) = (respondido_em is null)`.
- `not (falha_envio and (wamid_envio is not null or resposta is not null))`: um envio que
  falhou não tem wamid nem resposta.
- `respondido_em >= enviado_em`.

Índices:

- `responsavel_id`, para a chave estrangeira.
- Único parcial em `wamid_envio where wamid_envio is not null`: liga um status de entrega da
  Meta à linha certa e impede o mesmo wamid em duas entregas.

A troca de `NAO_CONSEGUI` para `CONVERSEI` ("ok vale mesmo depois de não") é um `update` comum.
Quem ignora o toque repetido ou "não" depois de "ok" é o Java B.

## g) Fechamento (H26)

Para `responsaveis`, `conversa_estado` e `alerta_entregas`:

- RLS ligado;
- `revoke all` de `anon` e `authenticated`;
- a policy restritiva `negar_anon_authenticated` (`using (false) with check (false)`).

Para a função `normalizar_loja`: `set search_path = ''`, `immutable` e
`revoke execute` de `public`, `anon` e `authenticated`.

O teste `01_negar_por_padrao` já percorre todas as tabelas de `public`, então cobre as novas
automaticamente.

## Testes pgTAP (arquivo novo `04_fluxo_etapa1.test.sql`)

- anon e authenticated sem privilégio nas três tabelas novas, e a policy restritiva presente;
- `GERENTE` sem loja recusado; `RH` com loja recusado; o mesmo telefone como `GERENTE` e `RH`
  aceito; o mesmo telefone duas vezes como `RH` recusado;
- `normalizar_loja` iguala " Centro " e "CENTRO";
- `conversa_estado`: `AGUARDANDO_HUMOR` sem `checkin_id` recusado;
  `AGUARDANDO_CONFIRMACAO_RELATO` sem `relato_id` recusado; `LIVRE` com referência recusado;
  check-in de outro colaborador recusado; `atualizado_em` muda no update;
- check-in do mesmo dia duplicado recusado;
- relato novo nasce com `confirmacao = 'AGUARDANDO'`, `sinais_graves = false`,
  `falha_ia = false` e `confianca` nula; `falha_ia` com categoria diferente de
  `NAO_CLASSIFICADO` recusado;
- alerta novo nasce com `urgente = false`; `resolvido_em` sem `resolvido_por` recusado;
- entrega duplicada (mesmo alerta e responsável) recusada; resposta fora da lista recusada;
  resposta sem `respondido_em` recusada.

Tudo com dados fictícios. A migração roda só no banco local (`supabase db reset` e
`supabase test db`); quem aplica no remoto é o PO.

## Pontos para você decidir

1. **Coerência estrita em `conversa_estado`.** O pedido só exigia a referência do estado
   aguardando. Propus também que `LIVRE` não tenha referência e que cada estado aguardando
   não tenha a referência do outro. Se preferir a versão mínima, eu removo essa parte.
2. **Chaves compostas** que garantem que o estado aponta para o check-in ou relato da mesma
   pessoa (item b).
3. **`falha_ia` só com `NAO_CLASSIFICADO`** (item d).
4. **wamid de entrega único** quando preenchido (item f).
5. **Lojas comparadas sem diferença de caixa e espaços, mas com acento**, e a tabela `lojas`
   para o futuro.
