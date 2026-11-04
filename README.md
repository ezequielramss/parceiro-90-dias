# Parceiro dos 90 dias — Kallopolli

Dashboard para acompanhamento de pessoas no período inicial de 90 dias. A interface usa HTML semântico, CSS e JavaScript nativo (ES modules), sem framework, bundler, CDN ou chamadas externas.

## Como rodar

O próprio backend serve o dashboard, na mesma origem que a API e sem CORS: suba o backend (`backend/README.md`) e abra `http://localhost:8080`. O build copia `dashboard/` para o classpath, então a pasta continua sendo a única fonte dos arquivos.

- **Dados reais:** a tela de login aparece quando não há sessão. Ela usa o e-mail e a senha de um usuário do Supabase Auth que tenha uma linha em `acessos_dashboard`.
- **Demonstração sem backend:** abra com `?mock=1` (por exemplo `http://localhost:8080/?mock=1`), ou sirva só a pasta com `python -m http.server 8000 --directory dashboard` e abra `http://localhost:8000/?mock=1`. Nesse modo, os dados vêm de `dashboard/mock/` e não há login.

## Estrutura

- `dashboard/index.html`: shell e ponto de entrada.
- `dashboard/css/`: reset e estilos da interface.
- `dashboard/js/`: inicialização, API, utilitários seguros e telas.
- `dashboard/mock/`: dados demonstrativos fictícios.
- `backend/`: API (WhatsApp e painel), servidor do dashboard e do simulador local.
- `evolution/`: Evolution API local (Docker) para a demonstração no celular.
- `simulador/`: página do simulador de chat, servida pelo backend só com o canal simulador.
- `docs/`: contrato da API e decisões de produto e privacidade.

## Convenções

- A navegação usa hash routes para Visão geral, Funcionários, Alertas, Relatos, Bem-estar e Relatórios. A rota padrão é `#/visao-geral`.
- `dashboard/js/api.js` define `USE_MOCK`: é `false` por padrão e só fica `true` com `?mock=1` na URL. Com `false`, o navegador acessa somente `/api/*` da mesma origem.
- No modo demonstração, o perfil é `MOCK_PERFIL` em `dashboard/js/api.js` (`RH` ou `GERENTE`). No modo real, o perfil vem do backend, que o lê de `acessos_dashboard`.
- O token de login fica só em cookie HttpOnly; o JavaScript não o lê e o navegador nunca fala com o Supabase.
- Conteúdo dinâmico é inserido com `textContent` e `createElement` via helper `h()`; não use `innerHTML` para dados.
- Ícones e fontes são servidos localmente. Mantenha os exemplos de pessoas e alertas como fictícios.
- Os mocks deste repositório são exclusivamente fictícios e não representam colaboradores reais.

## Login e acesso

- O RH vê todas as lojas.
- O gerente vê só a própria loja, sem motivo, sem texto de relato e sem categorias restritas.
- Os usuários são criados no Supabase Auth pelo PO. O papel e a loja de cada um ficam em `public.acessos_dashboard`, que o navegador nunca altera.
- Não há cadastro, recuperação de senha nem renovação de sessão pelo painel. Quando o token vence, a tela de login volta.

## Fases entregues

Visão geral, Funcionários, Alertas, Relatos confidenciais, Bem-estar e Relatórios estão ligados ao backend, somente para leitura.

- As mudanças de status e as observações dos relatos ficam só no navegador.
- A geração de análise em Relatórios mostra "Indisponível nesta versão." no modo real. No modo demonstração, ela usa um mock com atraso simulado.
- Presença e faltas aparecem como "—", porque o sistema não recebe dados de ponto.

## WhatsApp

Neste MVP o WhatsApp funciona pela **Evolution API** (`evolution/README.md`), que conecta um número por QR code. Para testar sem nada externo, há um **simulador de chat** local em `http://localhost:8080/simulador` (`WHATSAPP_CANAL=simulador`). Os dois usam o mesmo fluxo; as opções vão escritas nas mensagens (SIM ou NÃO, CONVERSEI ou NÃO CONSEGUI), sem botões e sem janela de 24 horas.

> **Produção deve voltar à API oficial da Meta.** A conexão por QR code é não oficial e o número pode ser banido. A versão com a WhatsApp Cloud API está na tag git `meta-cloud-api`.
