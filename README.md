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

- A navegação usa hash routes para Visão geral, Funcionários, Alertas, Relatos, Bem-estar e Relatórios. A rota padrão é `#/visao-geral`.
- `dashboard/js/api.js` define `USE_MOCK`. Com `true`, carrega JSON local; com `false`, acessa somente `/api/*`.
- O perfil de demonstração é `MOCK_PERFIL` em `dashboard/js/api.js` (`RH` ou `GERENTE`). O perfil GERENTE não solicita dados de relatos confidenciais.
- Conteúdo dinâmico é inserido com `textContent` e `createElement` via helper `h()`; não use `innerHTML` para dados.
- Ícones e fontes são servidos localmente. Mantenha os exemplos de pessoas e alertas como fictícios.
- Os mocks deste repositório são exclusivamente fictícios e não representam colaboradores reais.

## Fases entregues

Visão geral, Funcionários, Alertas, Relatos confidenciais, Bem-estar e Relatórios. Alterações de status e observações dos relatos são locais; geração de análise usa um mock com atraso simulado. Não existe integração real com backend nesta entrega.
