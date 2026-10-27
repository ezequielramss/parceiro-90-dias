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

## `GET /api/relatos` (somente perfil RH)

A lista não contém loja, turno ou identidade; esses dados podem ser entregues no detalhe conforme política de acesso.

```json
[{"id":"r01","protocolo":"REL-2026-001","categoria":"Humilhação","severidade":"ALTA","status":"NOVO","criadoEm":"2026-10-27T09:20:00-03:00"}]
```

## `GET /api/bem-estar`

Fornece categoria e resumo neutro descritivo, nunca a mensagem literal do colaborador.

```json
[{"categoria":"Sobrecarga","resumo":"Relatos indicam necessidade de apoio durante horários de pico.","periodo":"2026-10"}]
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
