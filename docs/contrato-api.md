# Contrato da API (proposta)

Todos os endpoints usam JSON UTF-8. Erros devem retornar `{ "erro": { "codigo": "...", "mensagem": "..." } }`; `401` encerra a sessão e solicita novo acesso. Datas e horas são ISO 8601 com fuso. Os exemplos são fictícios.

## `GET /api/kpis`

```json
{"emJornada":38,"precisamAtencao":7,"alertasAbertos":3,"atendidos":6,"semRetorno24h":3,"taxaResposta10d":81,"tempoMedioRetornoHoras":6.4}
```

## `GET /api/colaboradores`

Retorna uma lista com `id`, `nome`, `iniciais`, `funcao`, `loja`, `diaJornada`, `presenca`, `faltas`, `ultimoContato`, `nivelAtencao`, `motivoAtencao` e `proximoCheckin`.

```json
[{"id":"c01","nome":"Pessoa Exemplo","iniciais":"PE","funcao":"Atendente","loja":"Centro","diaJornada":18,"presenca":96,"faltas":1,"ultimoContato":"2026-10-27T10:20:00-03:00","nivelAtencao":"Atenção","motivoAtencao":"1 falta antes dos 45 dias","proximoCheckin":"2026-10-30"}]
```

## `GET /api/colaboradores/{id}/linha-do-tempo`

```json
{"colaboradorId":"c01","eventos":[{"tipo":"CHECKIN","titulo":"Check-in de acompanhamento","criadoEm":"2026-10-27T10:20:00-03:00","resumo":"Acompanhamento realizado."}]}
```

## `GET /api/alertas?status=&destino=`

`status` aceita `ABERTO`, `ATENDIDO` ou `SEM_RETORNO_24H`; `destino` aceita `GERENTE` ou `RH`. Para categorias confidenciais (`ASSEDIO`, `CONFLITO_CHEFIA`), `colaboradorId` deve ser `null`; a resposta não deve incluir nome ou identificador pessoal.

```json
[{"id":"a01","colaboradorId":null,"protocolo":"PD90-2026-0140","categoria":"CONFLITO_CHEFIA","severidade":"ALTA","destino":"RH","destinatarioNome":"Equipe de RH","criadoEm":"2026-10-26T08:10:00-03:00","status":"SEM_RETORNO_24H","retorno":null,"respondidoEm":null,"lembreteEnviadoEm":"2026-10-27T08:10:00-03:00","sugestao":"Vale uma conversa ainda hoje","colaboradorAvisado":true}]
```

Categorias: `CANSACO`, `SOBRECARGA`, `DESMOTIVACAO`, `VONTADE_DE_SAIR`, `DUVIDA`, `CONFLITO_COLEGA`, `CONFLITO_CHEFIA`, `ASSEDIO`, `SITUACAO_GRAVE`. Destinos de `CONFLITO_CHEFIA` e `ASSEDIO` são RH; `SITUACAO_GRAVE` também vai a RH. As demais categorias são encaminhadas ao gerente.

## `GET /api/perfil`

```json
{"perfil":"RH"}
```

O cliente consulta o perfil antes de carregar relatos. Somente `RH` pode chamar `GET /api/relatos`.

## `GET /api/relatos` (somente perfil RH)

Aceita filtro opcional `status`. A lista contém protocolo, título, categoria, severidade, status e data; não retorna loja, turno, telefone nem dados de identificação.

```json
[{"protocolo":"AN-1048","titulo":"Tratamento inadequado durante o turno","categoria":"HUMILHACAO","severidade":"ALTA","status":"NOVO","criadoEm":"2026-10-27T09:24:00-03:00","loja":"Centro","turno":"Noite","conteudo":"Orientações foram feitas diante da equipe em tom ofensivo.","historico":[{"titulo":"Relato recebido","data":"2026-10-27T09:24:00-03:00","descricao":"Registro recebido pelo canal confidencial."}]}]
```

Loja, turno e conteúdo detalhado são retornados para exibição somente no detalhe e sob autorização RH.

## `PATCH /api/relatos/{protocolo}` (futuro)

Na Fase 2, mudança de status e observação interna existem somente na memória do navegador. A integração futura aceitará:

```json
{"status":"EM_ANALISE","observacaoInterna":"Análise iniciada pelo RH."}
```

Status possíveis: `NOVO`, `EM_ANALISE`, `CONCLUIDO`.

## `GET /api/bem-estar`

Para o perfil RH, fornece distribuição e acompanhamentos com categoria e resumo neutro descritivo, nunca a mensagem literal. O backend limita o perfil gerente a categorias e sugestões, sem identificadores, histórico, resumo ou contato individual.

```json
{"distribuicaoCategorias":[{"categoria":"Estável","quantidade":16},{"categoria":"Sobrecarga","quantidade":6}],"pessoas":[{"colaboradorId":"c03","categoria":"Sobrecarga","ultimoContato":"2026-10-26T17:45:00-03:00","tendencia":"mais frequente nos últimos 7 dias","sugestao":"Vale conversar sobre a distribuição das tarefas","historico":[{"tipo":"CHECKIN","categoria":"Sobrecarga","resumoNeutro":"A pessoa pediu apoio para organizar as tarefas nos horários de maior movimento.","data":"2026-10-26T17:45:00-03:00","tag":"Acompanhamento"}]}]}
```

## `GET /api/kpis/serie-mensal`

```json
{"itens":[{"mes":"2026-08","emJornada":32,"precisamAtencao":6},{"mes":"2026-09","emJornada":35,"precisamAtencao":7}]}
```

## `POST /api/relatorios/gerar` (futuro: IA)

```json
{"periodo":"90d","escopo":"loja","loja":"Centro","observacoes":"Pontos a considerar"}
```

Resposta futura: `202 Accepted` com identificador de processamento. Revisão humana é necessária antes de compartilhar o relatório.
