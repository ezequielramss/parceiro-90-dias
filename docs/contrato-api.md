# Contrato da API

Todos os endpoints usam JSON UTF-8 e ficam na mesma origem do dashboard, que o backend serve. Datas e horas são ISO 8601 com fuso de São Paulo. Os exemplos são fictícios.

Só leitura: todas as rotas são `GET`, exceto login e logout.

## Erros

Formato: `{ "erro": { "codigo": "...", "mensagem": "..." } }`.

| Status | Código | Quando |
|---|---|---|
| 400 | `FILTRO_INVALIDO` | `status` ou `destino` fora dos valores aceitos |
| 401 | `SESSAO_INVALIDA` | sem cookie, ou com token inválido, vencido, de outro emissor ou de outra audience. O dashboard volta para a tela de login |
| 401 | `LOGIN_RECUSADO` | e-mail ou senha incorretos; a mensagem é sempre "E-mail ou senha incorretos." |
| 403 | `SEM_ACESSO` | usuário válido no Supabase Auth, mas sem linha em `acessos_dashboard` |
| 403 | `SO_RH` | gerente chamando `/api/relatos` |
| 404 | `NAO_ENCONTRADO` | colaborador que não existe ou é de outra loja (a resposta é a mesma nos dois casos) |
| 503 | `PAINEL_NAO_CONFIGURADO` | o servidor está sem `SUPABASE_URL` ou `SUPABASE_PUBLISHABLE_KEY` |
| 503 | `AUTH_INDISPONIVEL` | o Supabase Auth não respondeu |

Toda resposta de `/api/**` leva `Cache-Control: no-store`.

## `POST /api/login`

Corpo: `{"email":"pessoa@exemplo.invalid","senha":"..."}`. O backend chama o Supabase Auth; o navegador nunca fala com o Supabase nem recebe a chave.

Em caso de sucesso, a resposta é `200`:

- O token vai só no cabeçalho `Set-Cookie: parceiro_sessao=...; Path=/; Max-Age=<validade do token>; Secure; HttpOnly; SameSite=Strict`.
- O corpo traz apenas o perfil (igual ao de `GET /api/perfil`).

Erros: `401 LOGIN_RECUSADO`, `403 SEM_ACESSO` (sem cookie) e `503`.

## `POST /api/logout`

`204`, com `Set-Cookie` que apaga o cookie (`Max-Age=0`).

Não existem rotas de cadastro, de recuperação de senha nem de renovação do token. Quando o token vence, a resposta é `401` e o usuário entra de novo.

## Quem vê o quê

O papel e a loja vêm sempre de `public.acessos_dashboard`. O que o navegador envia (cabeçalho, parâmetro ou corpo) é ignorado, assim como qualquer claim do token além de `sub`.

**RH:** vê todas as lojas, inclusive o motivo e a ação sugerida.

**GERENTE:** vê somente a própria loja.

- Alertas: só os de categorias com destino `GERENTE` ou `GERENTE_E_RH`, nunca os urgentes.
- Nunca recebe motivo, texto do relato nem as categorias restritas (`CONFLITO_CHEFIA`, `NAO_CLASSIFICADO`, `ASSEDIO`, `HUMILHACAO`, `SITUACAO_GRAVE`).
- Como no alerta do WhatsApp (H17), o alerta não é ligado à pessoa: `colaboradorId` vem `null` e a linha do tempo mostra só os check-ins.
- KPIs e bem-estar são só da loja dele.
- `/api/relatos` responde 403.

Colaboradores só aparecem enquanto estão ativos e com aceite ativo.

## `GET /api/kpis`

`alertasAbertos`, `atendidos` e `semRetorno24h` são contados por `status` (veja alertas). `taxaResposta10d` é a porcentagem dos check-ins dos dias 1 a 10 que tiveram nota. `tempoMedioRetornoHoras` é a média entre o alerta e o primeiro retorno. O `insight` é um texto neutro sobre essa taxa.

```json
{"emJornada":38,"precisamAtencao":7,"alertasAbertos":3,"atendidos":6,"semRetorno24h":3,"taxaResposta10d":81,"tempoMedioRetornoHoras":6.4,"insight":{"titulo":"Check-ins no início da jornada","resumo":"A equipe registrou pedidos de orientação nos primeiros 30 dias."}}
```

## `GET /api/colaboradores`

Retorna uma lista com `id`, `nome`, `iniciais`, `funcao`, `loja`, `turno`, `diaJornada`, `presenca`, `faltas`, `ultimoContato`, `nivelAtencao`, `motivoAtencao` e `proximoCheckin`. Só aparecem pessoas nos 90 dias.

- `presenca` e `faltas` são `null`, porque não há dados de ponto.
- `funcao` mostra o turno, porque o cargo não é cadastrado.
- `ultimoContato` é a última mensagem recebida.
- O `nivelAtencao` vem das notas dos check-ins dos últimos 7 dias, sempre com o motivo: duas ou mais notas 1 ou 2 dão `Prioridade`, uma dá `Atenção` e nenhuma dá `Estável`.
- `proximoCheckin` é `null` depois do dia 10.

```json
[{"id":"c01","nome":"Pessoa Exemplo","iniciais":"PE","funcao":"Atendente","loja":"Centro","diaJornada":18,"presenca":96,"faltas":1,"ultimoContato":"2026-10-27T10:20:00-03:00","nivelAtencao":"Atenção","motivoAtencao":"1 falta antes dos 45 dias","proximoCheckin":"2026-10-30"}]
```

## `GET /api/colaboradores/{id}/linha-do-tempo`

Os eventos vêm do mais recente para o mais antigo:

- `CHECKIN`: o check-in, com a nota ou "Sem resposta".
- `ALERTA`: só para o RH, com o motivo.
- `RETORNO`: só para o RH. Conversei ou Não consegui.

O texto do relato nunca aparece. Um `id` que não existe, que é de outra loja ou que não é UUID dá `404`.

```json
{"colaboradorId":"c01","eventos":[{"tipo":"CHECKIN","titulo":"Check-in de acompanhamento","criadoEm":"2026-10-27T10:20:00-03:00","resumo":"Acompanhamento realizado."}]}
```

## `GET /api/alertas?status=&destino=`

`status` aceita `ABERTO`, `ATENDIDO` ou `SEM_RETORNO_24H`; `destino` aceita `GERENTE` ou `RH`. Os dois filtros incluem os alertas `GERENTE_E_RH`.

- Um alerta `ABERTO` há mais de 24 horas aparece como `SEM_RETORNO_24H`.
- `retorno` é `CONVERSEI` se algum responsável conversou. Se não, é `NAO_CONSEGUI` se algum respondeu assim. Sem resposta, é `null`.
- `severidade`: `ALTA` para urgente, `BAIXA` para dúvida e `MODERADA` para o resto.
- `lembreteEnviadoEm` é `null` (o lembrete ainda não existe).
- `motivo` só vem para o RH. Para categorias confidenciais (`ASSEDIO`, `CONFLITO_CHEFIA`), `colaboradorId` deve ser `null`; a resposta não deve incluir nome ou identificador pessoal.

```json
[{"id":"a01","colaboradorId":null,"protocolo":"PD90-2026-0140","categoria":"CONFLITO_CHEFIA","severidade":"ALTA","destino":"RH","destinatarioNome":"Equipe de RH","criadoEm":"2026-10-26T08:10:00-03:00","status":"SEM_RETORNO_24H","retorno":null,"respondidoEm":null,"lembreteEnviadoEm":"2026-10-27T08:10:00-03:00","sugestao":"Vale uma conversa ainda hoje","colaboradorAvisado":true}]
```

Categorias (tabela `categorias`, 12 códigos):

| Destino | Categorias |
|---|---|
| Gerente da loja | `CANSACO`, `SOBRECARGA`, `DESMOTIVACAO`, `DUVIDA`, `CONFLITO_COLEGA` |
| Gerente e RH | `VONTADE_DE_SAIR` |
| Só RH | `CONFLITO_CHEFIA`, `NAO_CLASSIFICADO`; urgentes: `ASSEDIO`, `HUMILHACAO`, `SITUACAO_GRAVE` |
| Sem alerta (só indicadores agregados) | `ELOGIO` |

Sinais graves em qualquer categoria levam o relato ao RH como urgente. Categorias só do RH nunca aparecem para o gerente.

## `GET /api/perfil`

```json
{"perfil":"GERENTE","nome":"Gerente Ficticio","loja":"loja centro"}
```

`loja` é `null` para o RH.

O cliente consulta o perfil antes de carregar relatos. Somente `RH` pode chamar `GET /api/relatos`.

## `GET /api/relatos` (somente perfil RH)

Aceita filtro opcional `status`. Lista os relatos encaminhados ao RH. Não lista os que o colaborador decidiu não enviar nem os que foram só ao gerente.

- A lista nunca traz telefone nem nome.
- Depois de 90 dias, `conteudo` vira o aviso "Texto apagado após 90 dias, como prevê o termo de participação.", porque a retenção apaga o texto.

```json
[{"protocolo":"AN-1048","titulo":"Tratamento inadequado durante o turno","categoria":"HUMILHACAO","severidade":"ALTA","status":"NOVO","criadoEm":"2026-10-27T09:24:00-03:00","loja":"Centro","turno":"Noite","conteudo":"Orientações foram feitas diante da equipe em tom ofensivo.","historico":[{"titulo":"Relato recebido","data":"2026-10-27T09:24:00-03:00","descricao":"Registro recebido pelo canal confidencial."}]}]
```

Loja, turno e conteúdo detalhado são retornados para exibição somente no detalhe e sob autorização RH.

## `PATCH /api/relatos/{protocolo}` (fora do MVP: não existe)

Na Fase 2, mudança de status e observação interna existem somente na memória do navegador. A integração futura aceitará:

```json
{"status":"EM_ANALISE","observacaoInterna":"Análise iniciada pelo RH."}
```

Status possíveis: `NOVO`, `EM_ANALISE`, `CONCLUIDO`.

## `GET /api/bem-estar`

Para o perfil RH, fornece distribuição e acompanhamentos com categoria e resumo neutro descritivo, nunca a mensagem literal. O backend limita o perfil gerente a categorias e sugestões, sem identificadores, histórico, resumo ou contato individual. A resposta do gerente é `{"distribuicaoCategorias":[...],"pessoas":[{"categoria":"Cansaço","sugestao":"..."}]}`.

- RH: a distribuição conta os relatos encaminhados e os elogios. `pessoas` não inclui categorias restritas.
- Gerente: a distribuição conta os relatos da loja que viraram alerta para ele, mais os elogios.
- Relatos que já perderam o texto pela retenção continuam contando.

```json
{"distribuicaoCategorias":[{"categoria":"Estável","quantidade":16},{"categoria":"Sobrecarga","quantidade":6}],"pessoas":[{"colaboradorId":"c03","categoria":"Sobrecarga","ultimoContato":"2026-10-26T17:45:00-03:00","tendencia":"mais frequente nos últimos 7 dias","sugestao":"Vale conversar sobre a distribuição das tarefas","historico":[{"tipo":"CHECKIN","categoria":"Sobrecarga","resumoNeutro":"A pessoa pediu apoio para organizar as tarefas nos horários de maior movimento.","data":"2026-10-26T17:45:00-03:00","tag":"Acompanhamento"}]}]}
```

## `GET /api/kpis/serie-mensal`

Últimos 6 meses: pessoas por nível de atenção, pelas notas de cada mês. `presenca` é `null`, porque não há dados de ponto.

```json
{"itens":[{"mes":"Mai","estavel":23,"atencao":10,"prioridade":5,"presenca":92},{"mes":"Jun","estavel":25,"atencao":9,"prioridade":5,"presenca":93},{"mes":"Jul","estavel":27,"atencao":8,"prioridade":4,"presenca":94},{"mes":"Ago","estavel":28,"atencao":7,"prioridade":4,"presenca":94},{"mes":"Set","estavel":29,"atencao":6,"prioridade":4,"presenca":95},{"mes":"Out","estavel":31,"atencao":5,"prioridade":2,"presenca":95}]}
```

## `POST /api/relatorios/gerar` (fora do MVP: não existe; o dashboard mostra "Indisponível nesta versão.")

```json
{"escopo":"FUNCIONARIO","publico":"DIRECAO","colaboradorId":"c03"}
```

A resposta contém `resumoExecutivo`, `pontosAtencao[]` e `sugestoes[]`.

```json
{"resumoExecutivo":"A equipe mantém indicadores de presença consistentes.","pontosAtencao":["Há sinais de cansaço em parte da equipe."],"sugestoes":["Realizar conversas individuais de acompanhamento."]}
```

No protótipo, `USE_MOCK` retorna `relatorio-exemplo.json` após atraso simulado de aproximadamente 800 ms. A integração futura deve omitir nomes quando `publico` for `DIRECAO`, aplicar autorização no servidor e sinalizar que a análise precisa de revisão humana.
