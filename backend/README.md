# Backend — Parceiro dos 90 dias

API em Java 21 + Spring Boot 3 (Maven). Recebe as mensagens de WhatsApp pela **Evolution API**,
grava no Postgres do Supabase e conduz o fluxo da Etapa 1: aceite, check-in, relato,
classificação, confirmação e alerta ao gerente ou ao RH. Também serve o dashboard e, só na máquina
local, um simulador de chat.

> **Produção:** a Evolution conecta o número por QR code. Essa conexão é não oficial, e o número
> pode ser banido. A produção deve voltar à WhatsApp Cloud API oficial da Meta: o código dessa
> versão está na tag git `meta-cloud-api`. Neste MVP a Meta saiu porque exige conta de empresa
> (CNPJ) para sair do modo de teste.

O que já existe:

- **Etapa 0 (H01, H38):** segredos só por ambiente.
- **Etapa 1 (H31, H02):** idempotência pelo id da mensagem, processamento assíncrono, aceite e
  "parar".
- **Etapa 1 (H18, H19, H37):** classificação pelo Gemini e regra de destino.
- **Etapa 1 (H03, H04, H13, H16, H17, H39):** boas-vindas, check-in dos dias 1 a 10, relato com
  confirmação, alertas com CONVERSEI e NÃO CONSEGUI e auditoria.
- **Java C:** dashboard com login e retenção de 90 dias.
- **Java D:** canal Evolution API, simulador local e opções por texto, sem botões.

## Estrutura

```
backend/src/main/java/br/com/kallopolli/parceiro/
├── whatsapp/
│   ├── CanalSaida.java                  interface do canal de saída: enviarTexto(número, texto) → id
│   ├── FalhaEnvio.java                  falha de envio, sem chave nem texto na mensagem
│   ├── ProcessadorMensagens.java        @Async: resposta a alerta, colaborador, responsável ou desconhecido
│   ├── MensagemRecebida.java            mensagem de qualquer canal: id, número, tipo e texto (toString sem dados)
│   ├── RespostaPorTexto.java            SIM/NÃO e CONVERSEI/NÃO CONSEGUI digitados
│   ├── TelefoneBrasil.java              compara números com e sem o nono dígito
│   ├── WamidsRecentes.java              idempotência das mensagens de responsáveis (em memória)
│   ├── MensageiroWhatsApp.java          envia ao colaborador pelo canal e grava a saída
│   └── evolution/
│       ├── EvolutionWebhookController.java   POST /webhook/evolution com segredo compartilhado
│       ├── EventoEvolution.java         messages.upsert → MensagemRecebida (só conversa individual)
│       ├── EvolutionCanal.java          POST /message/sendText/{instância} com apikey
│       └── EvolutionProperties.java     EVOLUTION_* (obrigatórias só com o canal evolution)
├── simulador/
│   ├── SimuladorCanal.java              fila em memória por número (últimas 200)
│   ├── SimuladorController.java         página /simulador e API /simulador/api
│   └── FiltroSimuladorLocal.java        só loopback, Host local e sem cabeçalho de proxy
├── consentimento/
│   ├── FluxoConsentimento.java          "parar", "aceito" e pedido de aceite
│   ├── EnvioAcompanhamento.java         única porta para check-ins: só envia com aceite ativo
│   ├── Textos.java                      termo v1 e mensagens do fluxo
│   └── TextoNormalizado.java            sem acento, minúsculas, sem pontuação
├── jornada/
│   ├── *Repository.java                 interfaces (colaboradores, consentimentos, mensagens, auditoria)
│   ├── jdbc/JdbcRepositorios.java       implementação com JdbcClient sobre as tabelas da Etapa 1
│   └── BancoProperties.java / BancoConfig.java   conexão lida do ambiente
├── conversa/
│   ├── OrquestradorConversa.java        nota, relato, confirmação, urgente, falha da IA, elogio
│   ├── AgendadorCheckin.java            @Scheduled a cada minuto: check-in dos dias 1 a 10
│   ├── TextosConversa.java              todos os textos (revisão em docs/textos-conversa.md)
│   ├── *Repository.java                 estado da conversa, check-ins e relatos
│   └── jdbc/JdbcConversaRepositorios.java
├── alerta/
│   ├── ServicoAlertas.java              cria o alerta e envia aos destinatários
│   ├── RespostaAlerta.java              CONVERSEI e NÃO CONSEGUI (ação alerta:<entregaId>:ok|nao)
│   ├── *Repository.java                 responsáveis, alertas e entregas
│   └── jdbc/JdbcAlertaRepositorios.java
├── painel/                              dashboard: login, rotas de leitura e retenção (seção Painel)
└── classificacao/
    ├── ClassificadorRelato.java         interface: classificar(texto) nunca lança exceção
    ├── ClassificadorGemini.java         generateContent com RestClient, novas tentativas e validação
    ├── PromptClassificacao.java         prompt e esquema lidos do classpath (fonte: ia/classificacao)
    ├── RoteadorRelato.java              regra pura de destino, urgência e confirmação (H19)
    ├── CategoriaRepository.java         tabela categorias (jdbc/JdbcCategoriaRepository.java)
    └── GeminiProperties.java            configuração do Gemini lida do ambiente
```

O prompt e o esquema da classificação não são copiados para o Java: o Maven leva
`ia/classificacao/prompt.md` e `ia/classificacao/saida.schema.json` para o classpath em
`classificacao/` no build. Um teste falha se algum deles faltar, estiver vazio ou divergir da
pasta `ia/`. Do mesmo jeito, `dashboard/` vai para `static/` e `simulador/` vai para
`simulador/`.

### Classificação e roteamento

- `ClassificadorGemini` chama `POST /v1beta/models/{GEMINI_MODEL}:generateContent` com o prompt
  em `systemInstruction`, o relato entre `<relato>` e `</relato>` (tags removidas do texto e
  tamanho limitado a 2000 caracteres), temperatura 0 e resposta JSON no esquema
  (`responseJsonSchema`). Resposta inválida, categoria fora da lista ou campo faltando viram
  `Classificacao.falha()` (`NAO_CLASSIFICADO`, `falhaIa`).
- Em 429, 5xx, timeout ou erro de rede, tenta de novo até 2 vezes, respeitando o `retryDelay` da
  API com teto de 5 s. No nível gratuito o 429 é esperado: o relato vai ao RH sem classificação.
- `RoteadorRelato.decidir` usa a tabela `categorias`: falha da IA vai ao RH sem confirmação;
  categoria urgente ou `sinais_graves` vai ao RH na hora; elogio (`AGREGADO`) não gera alerta;
  o resto pede confirmação do colaborador. A confiança não muda o destino, e categoria restrita
  nunca vai ao gerente, mesmo com a tabela errada.

As tabelas vêm de `supabase/migrations/20261102200100_jornada_colaboradores.sql` e
`20261103120000_fluxo_etapa1.sql`.

## Canal do WhatsApp

A propriedade `parceiro.whatsapp.canal` (variável `WHATSAPP_CANAL`) escolhe o canal:

- `evolution` (padrão): Evolution API. Exige `EVOLUTION_API_URL`, `EVOLUTION_API_KEY`,
  `EVOLUTION_INSTANCE` e `EVOLUTION_WEBHOOK_SECRET`; sem elas, a aplicação não sobe e diz quais
  faltam. Passo a passo para subir a Evolution e parear o número: `evolution/README.md`.
- `simulador`: chat no navegador em `http://localhost:8080/simulador`, sem nada externo. Não
  precisa de nenhuma variável da Evolution.

Orquestrador, alertas e agendador dependem só da interface `CanalSaida`. Cada canal converte o que
chega em `MensagemRecebida` e entrega ao mesmo `ProcessadorMensagens`: o fluxo não é duplicado.

Nesse canal **não há janela de 24 horas nem modelos de mensagem**. O check-in e os alertas chegam
a quem não escreveu antes. Também **não há botões**: as opções vão escritas no fim da mensagem
(`docs/textos-conversa.md`).

### Evolution API

- **Entrada:** `POST /webhook/evolution` recebe o evento `messages.upsert`.
  - A Evolution manda, em cada chamada, os cabeçalhos configurados no webhook da instância. Um
    deles é `X-Webhook-Secret`, comparado em tempo constante com `EVOLUTION_WEBHOOK_SECRET`.
    Sem ele ou com valor errado, a resposta é **401** e nada é processado.
  - Com o segredo certo, responde **200 na hora** e processa em outra thread.
- **O que é aceito:** só conversa individual (`<número>@s.whatsapp.net`).
  - É ignorado (com o motivo no log, sem dados): mensagem enviada pelo próprio número pareado
    (`fromMe`), grupos (`@g.us`), status e transmissões (`@broadcast`), canais (`@newsletter`),
    `@lid` sem número alternativo e eventos que não são de mensagem.
  - O texto vem de `message.conversation` ou `message.extendedTextMessage.text`.
  - Áudio, imagem, vídeo, figurinha e documento recebem "por enquanto só leio texto".
- **Número:** o número sai do identificador (`5511999990000@s.whatsapp.net`) e é comparado com e
  sem o nono dígito. A resposta sai sempre para o número cadastrado.
- **Repetição:** o id da mensagem (`key.id`) deduplica. Colaborador: `on conflict (wamid)` em
  `mensagens`; responsável: `WamidsRecentes`.
- **Saída:** `POST {EVOLUTION_API_URL}/message/sendText/{EVOLUTION_INSTANCE}` com
  `{"number","text"}` e a chave no cabeçalho `apikey`. O id da mensagem criada vem em `key.id`.
  - A chave nunca vai para log, resposta nem tela.
  - Falha de envio vira `FalhaEnvio`, e a entrega fica com `falha_envio` (`ALERTA_FALHA_ENVIO` na
    auditoria, sem texto).
- O formato do evento e o corpo do `sendText` foram conferidos no código da versão fixada
  (v2.3.7): a documentação não mostra o evento e ainda traz `textMessage.text`, que a validação
  da API não aceita.

### Simulador local

- Só existe com `WHATSAPP_CANAL=simulador`. Fora disso, `/simulador` dá 404.
- A página escolhe quem fala (colaborador, gerente ou RH, a partir dos telefones cadastrados) ou
  usa um telefone digitado. As mensagens do robô aparecem em balões, e as opções aparecem como
  atalhos que enviam o mesmo texto que a pessoa digitaria.
- O envio entra pelo mesmo caminho do webhook (`MensagemRecebida` → `ProcessadorMensagens`). A
  saída do robô fica numa fila em memória por telefone, com as últimas 200 mensagens, e se perde
  ao reiniciar. A página busca as novas a cada 1,5 s.
- **Só local:** o simulador não tem login. Por isso só atende conexão de loopback com `Host`
  `localhost` ou `127.0.0.1`, e recusa (403) qualquer requisição com `Forwarded`,
  `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Real-IP` ou `CF-Connecting-IP`.
  Um túnel entra pelo loopback, mas traz esses cabeçalhos. Nunca exponha o simulador na internet.
- A mesma política de segurança do dashboard (sem script inline). O texto das conversas não vai
  para log nem auditoria, e no banco só fica o que o fluxo normal já grava.

## Como uma mensagem é tratada

1. O canal (webhook da Evolution ou simulador) converte a mensagem em `MensagemRecebida` e a
   entrega ao `ProcessadorMensagens`, que roda em outra thread.
2. O processador decide quem escreveu. O mesmo número pode ser colaborador, gerente e RH.
   - **CONVERSEI** ou **NÃO CONSEGUI** de um número de responsável com alerta aberto: é a
     resposta ao alerta mais recente ainda aberto daquele número (seção "Alertas"). Sem alerta
     aberto, o texto segue o fluxo normal.
   - Número de colaborador ativo segue o fluxo abaixo. O número é comparado com e sem o 9
     depois do DDD, e as respostas saem sempre para o E.164 cadastrado.
   - Número que só é responsável recebe o texto curto de responsável.
   - Número desconhecido é ignorado: nada é gravado nem respondido.
3. A mensagem do colaborador é gravada com `on conflict (wamid) do nothing`. Se o id já existia,
   nada novo é gravado e nenhuma resposta é enviada.
4. Aceite (H02), com **"parar" acima de tudo**:
   - "parar" (qualquer caixa, com ou sem acento ou pontuação) retira o aceite, volta a conversa
     para `LIVRE`, marca como `DESCARTADO` o relato que aguardava confirmação e confirma;
   - sem aceite ativo, "aceito" registra o aceite com o termo v1 e envia as boas-vindas (H03);
     qualquer outra mensagem recebe o pedido de aceite.
5. Com aceite ativo, a conversa segue no `OrquestradorConversa`, pelo estado em
   `conversa_estado`:
   - **AGUARDANDO_HUMOR** (depois do check-in): "2" ou "nota 2" grava o humor, responde e volta
     a `LIVRE`. Outro texto vira relato (a pessoa escolheu falar).
   - **LIVRE**: texto novo é um relato (H13). Texto vazio ou só com espaços não é gravado: o
     assistente convida a escrever. O relato é gravado com `confirmacao = AGUARDANDO`,
     classificado e roteado pelo `RoteadorRelato`:
     - elogio: agradece e pronto, sem alerta;
     - urgente (inclui `sinais_graves`): `CONFIRMADO` na hora, alerta ao RH e aviso de relato
       urgente ao colaborador, sem esperar confirmação;
     - falha da IA: `CONFIRMADO`, alerta ao RH sem classificação e aviso ao colaborador (H37);
     - demais: mostra o resumo e pergunta se pode encaminhar, terminando com "Responda SIM para
       enviar ou NÃO para descartar." Nada sai antes do SIM.
   - **AGUARDANDO_CONFIRMACAO_RELATO** (H16): SIM vira a ação `conf:<relatoId>:sim`, que cria e
     envia o alerta; NÃO vira `conf:<relatoId>:nao`, marca `DESCARTADO` e confirma que nada foi
     enviado. Maiúsculas, acentos e pontuação não importam, e NÃO CONSEGUI não é NÃO. Qualquer
     outro texto substitui o relato, reclassifica e pede nova confirmação. Fora desse estado,
     "sim" e "não" seguem o fluxo normal.
   - Áudio, imagem, vídeo ou figurinha recebem "por enquanto só leio texto", sem mudar o estado.
6. Toda mensagem processada gera o log `mensagem processada`, com o id da mensagem (`wamid`) e o
   `resultado`. Uma falha gera `falha ao processar mensagem`, só com o id e o nome da exceção.

### Check-in (H04)

Não há horário (decisão do PO). O `AgendadorCheckin` roda a cada minuto, no fuso
America/Sao_Paulo (`Clock` injetado), e envia o check-in do dia a quem está ativo, tem aceite
ativo e ainda não recebeu o do dia: `dia_jornada = (hoje - data_inicio) + 1`, só dias 1 a 10.
A linha em `checkins` é inserida antes do envio (unicidade colaborador + dia), então o mesmo
dia nunca sai duas vezes. Dia perdido não é recuperado. Quem está confirmando um relato recebe
o check-in depois, no mesmo dia. Sem resposta, não há cobrança. A nota é de 1 a 5; por isso
nenhuma outra opção usa números. Liga e desliga com `parceiro.checkin.ativo` (padrão `true`;
os testes usam `false`).

### Alertas (H17, H19, H39)

- Destinatários: `GERENTE` = gerentes ativos da loja do colaborador (comparação sem caixa e
  espaços, `normalizar_loja`); `RH` = todos os RH ativos; `GERENTE_E_RH` = os dois. Loja sem
  gerente ativo: vai ao RH e fica na auditoria.
- Uma linha em `alerta_entregas` por destinatário. A mensagem termina com "responda CONVERSEI
  ou NÃO CONSEGUI." O gerente nunca recebe nome, motivo nem texto do relato; o RH recebe o
  motivo, nunca o nome nem o texto.
- Resposta digitada: vale para a entrega mais recente ainda aberta daquele número (enviada sem
  falha e sem CONVERSEI) e vira a ação `alerta:<entregaId>:ok|nao`, a mesma dos antigos botões.
  CONVERSEI grava `CONVERSEI` e põe o alerta em `ATENDIDO` (contato realizado, **não**
  resolvido); NÃO CONSEGUI grava `NAO_CONSEGUI`, e o alerta continua `ABERTO`. CONVERSEI vale
  depois de NÃO CONSEGUI; o contrário e as repetições são ignorados. NÃO CONSEGUI é conferido
  antes de NÃO.
- Criação, envio, falha de envio e resposta vão para `auditoria` (quem, o quê, quando), sem texto
  de relato. As ações mantêm os nomes de antes (`TOQUE_CONVERSEI`, `TOQUE_NAO_CONSEGUI`).

Mensagens de acompanhamento (check-in) só saem por `EnvioAcompanhamento.enviar(...)`, que
recusa quem não tem aceite ativo.

### Limitações desta etapa

- **Idempotência de responsáveis.** As mensagens de gerente e RH não vão para `mensagens`
  (a tabela exige colaborador); os ids recentes ficam em memória (`WamidsRecentes`).
- **Evolution não oficial.** O número pareado pode ser banido. Use um chip só do projeto e
  volte à API oficial (tag `meta-cloud-api`) para produção.
- Fora desta etapa: áudio, lembrete de 24 horas e subida ao RH, retorno ao colaborador após o
  atendimento, check-ins depois do dia 10 e regra do gerente citado no relato.

## Painel (dashboard)

O backend serve `dashboard/` na raiz. O build copia a pasta para o classpath (`static/`), então
ela continua sendo a fonte única. Os arquivos estáticos são públicos e não têm dados; tudo em
`/api/**` exige login. O código fica em `painel/`.

- **Login:** `POST /api/login` com `{"email","senha"}`. O backend chama o Supabase Auth
  (`/auth/v1/token?grant_type=password`) com a chave publicável; o navegador nunca vê a chave.
  - O token vai só num cookie `parceiro_sessao` (HttpOnly, SameSite=Strict, Path=/, com a
    validade do token). O cookie é Secure, exceto no perfil `local`.
  - Erro de login dá sempre "E-mail ou senha incorretos.".
  - `POST /api/logout` apaga o cookie.
  - Não há cadastro, recuperação de senha nem renovação.
- **A cada chamada:** o `FiltroPainel` confere a assinatura pelo JWKS do projeto
  (`SUPABASE_URL/auth/v1/.well-known/jwks.json`, ES256 ou RS256, em cache por 10 minutos). Ele
  também confere o emissor, a validade e a audience `authenticated`. Depois busca o usuário em
  `acessos_dashboard`.
  - Sem cookie, ou com token inválido ou vencido: 401.
  - Usuário sem linha em `acessos_dashboard`: 403.
  - Papel e loja vêm só dessa tabela.
- **Rotas de leitura:** ver `docs/contrato-api.md`. O filtro de papel e loja fica no WHERE de
  `JdbcPainelRepositorios`.
  - O gerente vê só a própria loja e só os alertas que receberia pelo WhatsApp.
  - O gerente nunca recebe motivo, texto, categorias restritas nem a ligação entre alerta e
    pessoa (H17).
  - `/api/relatos` é só do RH.
- **Cabeçalhos:** `Content-Security-Policy` (só a própria origem, sem script inline),
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`,
  e `Cache-Control: no-store` em `/api/**`.
- **Sem `SUPABASE_URL` ou `SUPABASE_PUBLISHABLE_KEY`:** o painel responde 503 e o WhatsApp
  continua funcionando.

### Retenção de 90 dias

O termo de participação diz que os relatos ficam guardados por até 90 dias. Todo dia às 3h (São
Paulo), `RetencaoRelatos` apaga o texto e o motivo dos relatos criados há mais de 90 dias. O
prazo conta da data do relato. A mesma rotina apaga o texto das mensagens do mesmo período: a
mensagem do relato e a confirmação "Entendi assim: ..." repetem o relato e o motivo.

Categoria, destino, datas e contagens ficam para os indicadores. A auditoria e o log registram
só as quantidades. Para desligar, use `parceiro.retencao.ativo=false`.

## Segredos

Toda configuração sensível vem de variáveis de ambiente, sem valor padrão no código.

- Sem as do banco e do Gemini, a aplicação não sobe e diz qual falta.
- As da Evolution só são obrigatórias com o canal `evolution`.
- As duas do painel são opcionais: sem elas, só o painel fica indisponível.

| Variável | O que é |
|---|---|
| `WHATSAPP_CANAL` | *(opcional)* `evolution` (padrão) ou `simulador`; propriedade `parceiro.whatsapp.canal` |
| `EVOLUTION_API_URL` | URL da Evolution API, por exemplo `http://localhost:8081` (`evolution/README.md`) |
| `EVOLUTION_API_KEY` | chave enviada no cabeçalho `apikey` (de preferência a chave da instância) |
| `EVOLUTION_INSTANCE` | nome da instância pareada, por exemplo `parceiro` |
| `EVOLUTION_WEBHOOK_SECRET` | segredo compartilhado que a Evolution envia em `X-Webhook-Secret` |
| `DATABASE_URL` | `jdbc:postgresql://host:porta/postgres` do Supabase |
| `DATABASE_USER` | usuário do banco (conexão de servidor; anon e authenticated não têm acesso) |
| `DATABASE_PASSWORD` | senha do banco |
| `GEMINI_API_KEY` | chave do Google AI Studio para classificar relatos; vai só no cabeçalho `x-goog-api-key` |
| `GEMINI_MODEL` | modelo do Gemini, por exemplo `gemini-3.1-flash-lite` (ver `ia/README.md`) |
| `SUPABASE_URL` | *(opcional)* URL do projeto, `https://<ref>.supabase.co`; sem ela o painel responde 503 |
| `SUPABASE_PUBLISHABLE_KEY` | *(opcional)* chave publicável (`sb_publishable_...`), usada só pelo backend no login |

As variáveis da Meta (`WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_VERIFY_TOKEN`,
`WHATSAPP_APP_SECRET`, `WHATSAPP_GRAPH_VERSION`) não existem mais: a aplicação sobe sem elas.

Para desenvolvimento, copie `.env.example` para `.env` **dentro de `backend/`** e preencha.
O `.env` está no `.gitignore` e é lido automaticamente. Use `CHAVE=valor`, sem aspas.

Os logs são JSON (ECS) e trazem só tipo, id da mensagem e resultado. Nunca trazem chave, segredo,
texto, telefone ou nome. Só `/actuator/health` é exposto.

## Rodar local

Precisa do JDK 21 e do Supabase local com as migrações (veja a raiz do repositório). No
PowerShell, a partir de `backend/`:

```powershell
Copy-Item .env.example .env    # depois preencha o .env
.\mvnw.cmd spring-boot:run
```

Para usar o banco local, preencha o `.env` com `DATABASE_URL=jdbc:postgresql://127.0.0.1:54322/postgres`
e o usuário e a senha que o `supabase status` mostra.

Para testar a conversa sem nada externo, use o simulador: `WHATSAPP_CANAL=simulador` no `.env`,
suba o backend e abra `http://localhost:8080/simulador`. Cadastre antes, no banco local, os
telefones fictícios de colaborador, gerente e RH.

Para o painel em `http://localhost:8080`, sem HTTPS, ative o perfil local, que desliga o
cookie Secure:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Para a demonstração no celular, suba a Evolution e pareie o chip do projeto seguindo
`evolution/README.md`. A Evolution chama o backend em `http://host.docker.internal:8080`, sem
túnel.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes usam segredos falsos, repositórios em memória e uma Evolution API simulada em
`127.0.0.1` (`EvolutionFalsa`). Nada sai da máquina. Eles cobrem:

- **webhook da Evolution**:
  - evento de texto vira `MensagemRecebida`, e o texto estendido também é lido;
  - segredo errado ou ausente dá 401 e nada é processado;
  - mensagem própria, de grupo, de status, de transmissão, de canal e `@lid` sem número são
    ignoradas;
  - `@lid` com número alternativo é aceito, e o nono dígito é tratado;
  - id repetido não duplica;
- **envio**: leva a `apikey` no cabeçalho e `{"number","text"}` no corpo; recusa e timeout viram
  `FalhaEnvio` sem chave, número nem texto;
- **respostas por texto**: SIM e NÃO só na confirmação; CONVERSEI e NÃO CONSEGUI no alerta mais
  recente aberto; NÃO CONSEGUI não vira NÃO; nota de 1 a 5 continua sendo nota; o mesmo telefone
  como colaborador, gerente e RH; texto sem relação segue o fluxo normal;
- **fluxo completo**: check-in, nota, relato, confirmação, urgente, restrito, falha da IA,
  elogio, loja sem gerente, falha de envio e as regras de CONVERSEI e NÃO CONSEGUI;
- **aceite**: pedido de aceite, "aceito", "parar" em 9 variações (caixa, acento, pontuação,
  espaços, quebra de linha), frases que só contêm "parar", novo aceite depois de parar,
  número não cadastrado e acompanhamento bloqueado sem aceite;
- **simulador**: 404 fora do canal simulador; recusa `Forwarded`, `X-Forwarded-For`,
  `CF-Connecting-IP`, `X-Real-IP`, conexão que não é de loopback e `Host` estranho; fluxo
  completo pela API (aceite, check-in, nota, relato, SIM, alertas ao gerente e ao RH,
  CONVERSEI), com o gerente sem motivo e o RH com motivo;
- **logs**: chave da Evolution, segredo do webhook, senha do banco, texto e telefone ausentes,
  inclusive em falha de processamento, erro da Evolution que repete a chave, segredo inválido e
  timeout;
- **subida**: a aplicação não sobe sem as variáveis da Evolution (no canal evolution), do banco
  ou do Gemini, e sobe sem nenhuma variável da Meta, nos dois canais;
- **painel**: um Supabase Auth falso (`SupabaseAuthFalso`) com um par de chaves gerado no
  teste e JWKS próprio. Os testes cobrem:
  - login certo e errado (mesma mensagem), sem acesso (403) e Supabase fora do ar (503);
  - token com assinatura errada, vencido, de outro emissor, de outra audience ou sem
    assinatura (401);
  - duas lojas: o gerente da loja A não vê nada da B em nenhuma rota, nem motivo nem
    categorias restritas; `/api/relatos` dá 403 e um id da loja B dá 404;
  - papel ou loja enviados pelo navegador são ignorados;
  - o cookie é HttpOnly e SameSite=Strict, não há rota de escrita e os cabeçalhos de segurança
    estão presentes;
  - os campos batem com `dashboard/mock/`;
  - sem `SUPABASE_URL`, o painel responde 503 e o WhatsApp continua;
  - os logs não têm e-mail, senha, token, cookie nem chave;
- **retenção**: relato com 89 dias mantém o texto; com 91 dias perde texto e motivo e continua
  contando; rodar duas vezes não muda nada.

Os repositórios JDBC têm teste próprio contra o Postgres. Ele roda só com as variáveis de teste
definidas, e cada caso é desfeito no fim:

```powershell
$env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54322/postgres"
$env:TEST_DATABASE_USER = "<usuário do supabase status>"
$env:TEST_DATABASE_PASSWORD = "<senha do supabase status>"
.\mvnw.cmd test -Dtest="Jdbc*Test"
```
