# Parceiro dos 90 dias — Kallopolli

Dashboard estático para acompanhamento de pessoas no período inicial de 90 dias. A interface usa HTML semântico, CSS e JavaScript nativo (ES modules), sem framework, bundler, CDN ou chamadas externas.

## Como rodar

Na pasta `parceiro-90-dias/`, execute `python -m http.server 8000 --directory dashboard` e abra `http://localhost:8000`. Também é possível servir `dashboard/` com qualquer servidor estático.

## Estrutura

- `dashboard/index.html`: shell e ponto de entrada.
- `dashboard/css/`: reset e estilos da interface.
- `dashboard/js/`: inicialização, API, utilitários seguros e telas.
- `dashboard/mock/`: dados demonstrativos fictícios.
- `docs/`: contrato proposto para integração e decisões de produto/privacidade.

## Convenções

- A navegação usa hash routes; telas futuras exibem um placeholder.
- `dashboard/js/api.js` define `USE_MOCK`. Com `true`, carrega JSON local; com `false`, acessa somente `/api/*`.
- Conteúdo dinâmico é inserido com `textContent` e `createElement` via helper `h()`; não use `innerHTML` para dados.
- Ícones e fontes são servidos localmente. Mantenha os exemplos de pessoas e alertas como fictícios.
- Os mocks deste repositório são exclusivamente fictícios e não representam colaboradores reais.

## Fase 1

Entregues as telas Funcionários e Alertas. Visão geral, Relatos, Bem-estar e Relatórios estão reservados para a Fase 2.
