# Backend — Parceiro dos 90 dias

API em Java 21 + Spring Boot 3 (Maven). Nesta etapa (Etapa 0) ela só recebe o webhook da
WhatsApp Cloud API e responde com um eco (`Recebi: <texto>`) para provar o fluxo de ponta a ponta.

Ainda **não** existem validação de assinatura (`X-Hub-Signature-256`), idempotência nem banco:
isso entra na Etapa 1.

## Estrutura

```
backend/
├── .env.example              nomes das variáveis, sem valores
├── pom.xml / mvnw            build (Maven Wrapper, não precisa instalar Maven)
└── src/main/java/br/com/kallopolli/parceiro/
    ├── ParceiroApplication.java
    └── whatsapp/
        ├── WhatsAppProperties.java          configuração lida do ambiente (falha se faltar)
        ├── WhatsAppWebhookController.java   GET (verificação) e POST (eventos) em /webhook/whatsapp
        ├── EcoService.java                  responde "Recebi: <texto>" fora da thread do webhook
        ├── WhatsAppClient.java              envio pela Graph API com RestClient e timeout
        └── WhatsAppClientException.java     erro sem token nem conteúdo
```

## Segredos

Toda configuração sensível vem de variáveis de ambiente. Nenhuma tem valor padrão: sem elas
a aplicação não sobe e diz qual falta.

| Variável | O que é |
|---|---|
| `WHATSAPP_TOKEN` | token de acesso da Graph API (de preferência de um usuário do sistema, que não expira) |
| `WHATSAPP_PHONE_NUMBER_ID` | ID do número de telefone no WhatsApp Manager (não é o número em si) |
| `WHATSAPP_VERIFY_TOKEN` | texto que você inventa e repete no painel da Meta ao cadastrar o webhook |
| `WHATSAPP_APP_SECRET` | chave secreta do app (será usada na Etapa 1 para validar a assinatura) |
| `WHATSAPP_GRAPH_VERSION` | versão da Graph API, por exemplo `v23.0` |

Para desenvolvimento, copie `.env.example` para `.env` **dentro de `backend/`** e preencha.
O `.env` está no `.gitignore` e é lido automaticamente ao rodar a partir de `backend/`.
Use `CHAVE=valor`, sem aspas. Variáveis de ambiente de verdade têm precedência sobre o `.env`.

Os logs são JSON (formato ECS) e trazem só o tipo e o `wamid` das mensagens. Nunca trazem
token, texto, telefone ou nome. Só `/actuator/health` é exposto.

## Rodar local

Precisa do JDK 21 (`JAVA_HOME` apontando para ele). No PowerShell, a partir de `backend/`:

```powershell
Copy-Item .env.example .env    # depois preencha o .env
.\mvnw.cmd spring-boot:run
```

A API sobe em `http://localhost:8080`. Conferir:

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe "http://localhost:8080/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=<seu verify token>&hub.challenge=123"
```

O primeiro deve responder `{"status":"UP"}` e o segundo, `123`.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes usam valores falsos e uma Graph API simulada em `127.0.0.1`. Nada sai da máquina e
nenhum segredo real é necessário. Eles cobrem:

- GET com verify token certo, errado, modo errado e sem parâmetros;
- POST com payloads desconhecidos, inválidos, vazios e de status (sempre 200);
- eco de ponta a ponta (URL, header `Authorization` e corpo enviados à Graph API);
- token, texto e telefone ausentes dos logs, inclusive quando a Meta devolve erro repetindo o token;
- timeout da Graph API vira exceção sem token;
- aplicação não sobe sem as variáveis do WhatsApp.

## Expor a porta com um túnel

A Meta só chama URLs públicas com HTTPS. Em desenvolvimento, use um túnel até a porta 8080.

**cloudflared** (não pede conta para túnel rápido):

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:8080
```

**ngrok** (pede conta gratuita e `ngrok config add-authtoken`):

```powershell
winget install --id Ngrok.Ngrok
ngrok http 8080
```

Os dois mostram uma URL `https://...`. A URL do webhook é essa URL + `/webhook/whatsapp`.
No túnel rápido do cloudflared e no ngrok gratuito a URL muda a cada execução, então é
preciso recadastrar no painel da Meta sempre que reiniciar o túnel.

## Cadastrar o webhook no painel da Meta

1. Com a API e o túnel rodando, abra o app em developers.facebook.com → **WhatsApp** → **Configuração**.
2. Em **Webhook**, clique em **Editar**:
   - **URL de callback**: `https://<url-do-tunel>/webhook/whatsapp`
   - **Token de verificação**: o mesmo valor de `WHATSAPP_VERIFY_TOKEN`
3. Clique em **Verificar e salvar**. A Meta faz o GET. A API responde o `hub.challenge` e loga
   `webhook verificado pela Meta`.
4. Em **Campos do webhook**, assine o campo **messages**.
5. Enquanto o app estiver em modo de desenvolvimento, só números cadastrados como destinatários
   de teste (em **Configuração da API**) recebem respostas. Mande uma mensagem de um desses
   números para o número do app: o log mostra `mensagem recebida` e `eco enviado`, e o celular
   recebe `Recebi: <texto>`.
