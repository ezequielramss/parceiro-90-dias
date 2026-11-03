# Backend — Parceiro dos 90 dias

API em Java 21 + Spring Boot 3 (Maven). Recebe o webhook da WhatsApp Cloud API, valida a
assinatura, grava as mensagens no Postgres do Supabase e conduz o aceite (H02).

O que já existe:

- **Etapa 0 (H01, H38):** verificação do webhook, `WhatsAppClient` e segredos só por ambiente.
- **Etapa 1 (H31, H02):** assinatura `X-Hub-Signature-256`, idempotência por `wamid`,
  processamento assíncrono, aceite e "parar".

Ainda **não** existem classificação de relatos nem agendador de check-ins.

## Estrutura

```
backend/src/main/java/br/com/kallopolli/parceiro/
├── whatsapp/
│   ├── WhatsAppWebhookController.java   GET (verificação) e POST (eventos) em /webhook/whatsapp
│   ├── AssinaturaWebhook.java           HMAC-SHA256 do corpo bruto com o App Secret, tempo constante
│   ├── ProcessadorMensagens.java        @Async: colaborador → grava (wamid único) → fluxo de aceite
│   ├── MensagemRecebida.java            wamid, remetente, tipo e texto (toString sem número e texto)
│   ├── MensageiroWhatsApp.java          envia texto e grava a mensagem de saída
│   └── WhatsAppClient.java              Graph API com RestClient, timeout e erros sem token
├── consentimento/
│   ├── FluxoConsentimento.java          "parar", "aceito" e pedido de aceite
│   ├── EnvioAcompanhamento.java         única porta para check-ins: só envia com aceite ativo
│   ├── Textos.java                      termo v1 e mensagens do fluxo
│   └── TextoNormalizado.java            sem acento, minúsculas, sem pontuação
├── jornada/
│   ├── *Repository.java                 interfaces (colaboradores, consentimentos, mensagens, auditoria)
│   ├── jdbc/JdbcRepositorios.java       implementação com JdbcClient sobre as tabelas da Etapa 1
│   └── BancoProperties.java / BancoConfig.java   conexão lida do ambiente
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

As tabelas vêm de `supabase/migrations/20261102200100_jornada_colaboradores.sql`.

## Como uma mensagem é tratada

1. O POST lê o corpo **bruto** e confere `X-Hub-Signature-256` (`sha256=` + HMAC-SHA256 em hex
   com `WHATSAPP_APP_SECRET`). Se a assinatura estiver ausente ou inválida, responde **401** e
   não processa nada.
2. Com a assinatura válida, responde **200 na hora** e entrega cada mensagem ao
   `ProcessadorMensagens`, que roda em outra thread.
3. O processador busca o colaborador ativo pelo telefone (`+` + número da Meta). Se o número
   não estiver cadastrado pelo RH, a mensagem é ignorada: não é gravada nem respondida.
4. Grava a mensagem com `on conflict (wamid) do nothing`. Se o `wamid` já existia, a
   mensagem é ignorada: nada novo é gravado e nenhuma resposta é enviada.
5. Aplica o fluxo de aceite:
   - **"parar"**: aceita qualquer caixa, com ou sem acento ou pontuação. Registra a retirada,
     grava na auditoria e confirma por texto.
   - **Sem aceite ativo e "aceito"**: registra o aceite com o texto do termo v1, grava na
     auditoria e confirma.
   - **Sem aceite ativo e qualquer outra mensagem**: envia o pedido de aceite com o termo.
   - **Com aceite ativo**: a mensagem fica gravada para a classificação, que é a próxima história.
6. Toda mensagem processada gera o log `mensagem processada`, com `wamid` e `resultado`. Uma
   falha gera `falha ao processar mensagem`, só com `wamid` e o nome da exceção.

Mensagens de acompanhamento só saem por `EnvioAcompanhamento.enviar(...)`, que recusa quem
não tem aceite ativo. O agendador da próxima etapa deve usar essa classe.

## Segredos

Toda configuração sensível vem de variáveis de ambiente. Nenhuma tem valor padrão: sem elas a
aplicação não sobe e diz qual falta.

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

Os repositórios JDBC têm teste próprio contra o Postgres. Ele roda só com as variáveis de teste
definidas, e cada caso é desfeito no fim:

```powershell
$env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:54322/postgres"
$env:TEST_DATABASE_USER = "<usuário do supabase status>"
$env:TEST_DATABASE_PASSWORD = "<senha do supabase status>"
.\mvnw.cmd test -Dtest=JdbcRepositoriosTest
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
