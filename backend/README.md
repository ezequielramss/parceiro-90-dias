# Backend — Parceiro dos 90 dias

API em Java 21 + Spring Boot 3 (Maven). Recebe o webhook da WhatsApp Cloud API, valida a
assinatura, grava as mensagens no Postgres do Supabase e conduz o fluxo da Etapa 1: aceite,
check-in, relato, classificação, confirmação e alerta ao gerente ou ao RH com botões.

O que já existe:

- **Etapa 0 (H01, H38):** verificação do webhook, `WhatsAppClient` e segredos só por ambiente.
- **Etapa 1 (H31, H02):** assinatura `X-Hub-Signature-256`, idempotência por `wamid`,
  processamento assíncrono, aceite e "parar".
- **Etapa 1 (H18, H19, H37):** classificação pelo Gemini e regra de destino.
- **Etapa 1 (H03, H04, H13, H16, H17, H39):** boas-vindas, check-in dos dias 1 a 10, relato com
  confirmação, alertas com "Conversei" e "Não consegui" e auditoria.

Não há endpoint HTTP além do webhook: sem login (H32, Etapa 6) nenhum dado é exposto.

## Estrutura

```
backend/src/main/java/br/com/kallopolli/parceiro/
├── whatsapp/
│   ├── WhatsAppWebhookController.java   GET (verificação) e POST (eventos) em /webhook/whatsapp
│   ├── AssinaturaWebhook.java           HMAC-SHA256 do corpo bruto com o App Secret, tempo constante
│   ├── ProcessadorMensagens.java        @Async: toque de alerta, colaborador, responsável ou desconhecido
│   ├── MensagemRecebida.java            texto, toque em botão ou tipo não suportado (toString sem dados)
│   ├── TelefoneBrasil.java              compara números com e sem o nono dígito
│   ├── WamidsRecentes.java              idempotência das mensagens de responsáveis (em memória)
│   ├── MensageiroWhatsApp.java          envia texto ou botões ao colaborador e grava a saída
│   └── WhatsAppClient.java              Graph API com RestClient (texto e botões), timeout e erros sem token
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
│   ├── ServicoAlertas.java              cria o alerta e envia aos destinatários com botões
│   ├── RespostaAlerta.java              toques "Conversei" e "Não consegui"
│   ├── *Repository.java                 responsáveis, alertas e entregas
│   └── jdbc/JdbcAlertaRepositorios.java
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
pasta `ia/`.

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

## Como uma mensagem é tratada

1. O POST lê o corpo **bruto** e confere `X-Hub-Signature-256` (`sha256=` + HMAC-SHA256 em hex
   com `WHATSAPP_APP_SECRET`). Se a assinatura estiver ausente ou inválida, responde **401** e
   não processa nada.
2. Com a assinatura válida, responde **200 na hora** e entrega cada mensagem ao
   `ProcessadorMensagens`, que roda em outra thread.
3. O processador decide quem escreveu:
   - toque em botão com id `alerta:...` é sempre resposta de responsável (seção "Alertas");
   - número de colaborador ativo segue o fluxo abaixo. A Meta pode entregar celulares
     brasileiros sem o nono dígito: o número é comparado com e sem o 9 depois do DDD, e as
     respostas saem sempre para o E.164 cadastrado;
   - número que só é responsável recebe o texto curto de responsável (isso abre a janela de
     24 horas dele para os próximos alertas);
   - número desconhecido é ignorado: nada é gravado nem respondido.
4. A mensagem do colaborador é gravada com `on conflict (wamid) do nothing`. Se o `wamid` já
   existia, nada novo é gravado e nenhuma resposta é enviada.
5. Aceite (H02), com **"parar" acima de tudo**:
   - "parar" (qualquer caixa, com ou sem acento ou pontuação) retira o aceite, volta a conversa
     para `LIVRE`, marca como `DESCARTADO` o relato que aguardava confirmação e confirma;
   - sem aceite ativo, "aceito" registra o aceite com o termo v1 e envia as boas-vindas (H03);
     qualquer outra mensagem recebe o pedido de aceite.
6. Com aceite ativo, a conversa segue no `OrquestradorConversa`, pelo estado em
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
     - demais: mostra o resumo e pergunta se pode encaminhar, com os botões
       `conf:<relatoId>:sim` e `conf:<relatoId>:nao`. Nada sai antes do "sim".
   - **AGUARDANDO_CONFIRMACAO_RELATO** (H16): "sim" (botão ou texto) cria e envia o alerta;
     "não" marca `DESCARTADO` e confirma que nada foi enviado; qualquer outro texto substitui
     o relato, reclassifica e pede nova confirmação.
   - Áudio, imagem, vídeo ou figurinha recebem "por enquanto só leio texto", sem mudar o estado.
7. Toda mensagem processada gera o log `mensagem processada`, com `wamid` e `resultado`. Uma
   falha gera `falha ao processar mensagem`, só com `wamid` e o nome da exceção.

### Check-in (H04)

Não há horário (decisão do PO). O `AgendadorCheckin` roda a cada minuto, no fuso
America/Sao_Paulo (`Clock` injetado), e envia o check-in do dia a quem está ativo, tem aceite
ativo e ainda não recebeu o do dia: `dia_jornada = (hoje - data_inicio) + 1`, só dias 1 a 10.
A linha em `checkins` é inserida antes do envio (unicidade colaborador + dia), então o mesmo
dia nunca sai duas vezes. Dia perdido não é recuperado. Quem está confirmando um relato recebe
o check-in depois, no mesmo dia. Sem resposta, não há cobrança. Liga e desliga com
`parceiro.checkin.ativo` (padrão `true`; os testes usam `false`).

### Alertas (H17, H19, H39)

- Destinatários: `GERENTE` = gerentes ativos da loja do colaborador (comparação sem caixa e
  espaços, `normalizar_loja`); `RH` = todos os RH ativos; `GERENTE_E_RH` = os dois. Loja sem
  gerente ativo: vai ao RH e fica na auditoria.
- Uma linha em `alerta_entregas` por destinatário, com os botões "Conversei"
  (`alerta:<entregaId>:ok`) e "Não consegui" (`alerta:<entregaId>:nao`). O gerente nunca
  recebe nome, motivo nem texto do relato; o RH recebe o motivo, nunca o nome nem o texto.
- Toque: só vale do número dono da entrega. "Conversei" grava `CONVERSEI` e põe o alerta em
  `ATENDIDO` (contato realizado, **não** resolvido); "Não consegui" grava `NAO_CONSEGUI` e o
  alerta continua `ABERTO`. "Conversei" vale depois de "Não consegui"; o contrário e os toques
  repetidos são ignorados.
- Criação, envio, falha de envio e toque vão para `auditoria` (quem, o quê, quando), sem texto
  de relato.

Mensagens de acompanhamento (check-in) só saem por `EnvioAcompanhamento.enviar(...)`, que
recusa quem não tem aceite ativo.

### Limitações desta etapa

- **Janela de 24 horas.** Sem modelo de mensagem aprovado na Meta, o WhatsApp só entrega texto
  livre a quem escreveu ao assistente nas últimas 24 horas. Por isso o **check-in só chega a
  quem conversou com o assistente nas últimas 24 horas**, e o **responsável precisa ter escrito
  ao assistente nas últimas 24 horas** para receber o alerta. Envio que falha é marcado
  (`CHECKIN_FALHA_ENVIO`, `falha_envio` na entrega) e não é repetido; lembrete e escalonamento
  ficam para a Etapa 3.
- **Idempotência de responsáveis.** As mensagens de gerente e RH não vão para `mensagens`
  (a tabela exige colaborador); os wamids recentes ficam em memória (`WamidsRecentes`). O toque
  em botão é idempotente pela própria regra.
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

Toda configuração sensível vem de variáveis de ambiente. Nenhuma tem valor padrão: sem elas a
aplicação não sobe e diz qual falta. As duas do painel são a exceção: sem elas, só o painel fica
indisponível.

| Variável | O que é |
|---|---|
| `WHATSAPP_TOKEN` | token de acesso da Graph API (de preferência de um usuário do sistema) |
| `WHATSAPP_PHONE_NUMBER_ID` | ID do número de telefone no WhatsApp Manager |
| `WHATSAPP_VERIFY_TOKEN` | texto que você inventa e repete no painel da Meta ao cadastrar o webhook |
| `WHATSAPP_APP_SECRET` | chave secreta do app; valida a assinatura de cada POST |
| `WHATSAPP_GRAPH_VERSION` | versão da Graph API, por exemplo `v23.0` |
| `DATABASE_URL` | `jdbc:postgresql://host:porta/postgres` do Supabase |
| `DATABASE_USER` | usuário do banco (conexão de servidor; anon e authenticated não têm acesso) |
| `DATABASE_PASSWORD` | senha do banco |
| `GEMINI_API_KEY` | chave do Google AI Studio para classificar relatos; vai só no cabeçalho `x-goog-api-key` |
| `GEMINI_MODEL` | modelo do Gemini, por exemplo `gemini-3.1-flash-lite` (ver `ia/README.md`) |
| `SUPABASE_URL` | *(opcional)* URL do projeto, `https://<ref>.supabase.co`; sem ela o painel responde 503 |
| `SUPABASE_PUBLISHABLE_KEY` | *(opcional)* chave publicável (`sb_publishable_...`), usada só pelo backend no login |

Para desenvolvimento, copie `.env.example` para `.env` **dentro de `backend/`** e preencha.
O `.env` está no `.gitignore` e é lido automaticamente. Use `CHAVE=valor`, sem aspas.

Os logs são JSON (ECS) e trazem só tipo, `wamid` e resultado. Nunca trazem token, segredo,
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

Para o painel em `http://localhost:8080`, sem HTTPS, ative o perfil local, que desliga o
cookie Secure:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

## Testes

```powershell
.\mvnw.cmd test
```

Os testes do webhook usam segredos falsos, repositórios em memória e uma Graph API simulada em
`127.0.0.1`. Nada sai da máquina. Eles cobrem:

- **assinatura**: válida, ausente, com outro segredo, corpo alterado depois de assinado,
  JSON reformatado e cabeçalho malformado (os inválidos dão 401 e nada é processado);
- **idempotência**: o mesmo `wamid` duas vezes gera um registro e uma resposta;
- **aceite**: pedido de aceite, "aceito", "parar" em 9 variações (caixa, acento, pontuação,
  espaços, quebra de linha), frases que só contêm "parar", novo aceite depois de parar,
  número não cadastrado e acompanhamento bloqueado sem aceite;
- **logs**: token, App Secret, verify token, senha do banco, texto e telefone ausentes,
  inclusive em falha de processamento, erro da Meta, assinatura inválida e timeout;
- **subida**: a aplicação não sobe sem as variáveis do WhatsApp ou do banco.
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

## Expor a porta com um túnel

A Meta só chama URLs públicas com HTTPS. Em desenvolvimento, use um túnel até a porta 8080:

```powershell
cloudflared tunnel --url http://localhost:8080    # ou: ngrok http 8080
```

A URL do webhook é a URL `https://...` mostrada + `/webhook/whatsapp`. No túnel rápido, ela muda
a cada execução.

## Cadastrar o webhook no painel da Meta

1. Com a API e o túnel rodando, abra o app em developers.facebook.com → **WhatsApp** → **Configuração**.
2. Em **Webhook**, clique em **Editar**:
   - **URL de callback**: `https://<url-do-tunel>/webhook/whatsapp`
   - **Token de verificação**: o mesmo valor de `WHATSAPP_VERIFY_TOKEN`
3. Clique em **Verificar e salvar** e depois assine o campo **messages**.
4. O `WHATSAPP_APP_SECRET` precisa ser o **App Secret** do mesmo app (Configurações do app →
   Básico). Se não for, todo POST da Meta vai dar 401.
5. Cadastre o colaborador de teste na tabela `colaboradores`, com o telefone em E.164. Enquanto
   o app estiver em modo de desenvolvimento, esse número também precisa estar na lista de
   destinatários de teste da Meta.
