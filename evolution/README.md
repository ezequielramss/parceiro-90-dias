# Evolution API local (demonstração no celular)

A Evolution API conecta um número de WhatsApp por QR code e entrega as mensagens ao backend do
Parceiro. Ela serve **só para demonstração local**.

> **Produção:** a conexão por QR code é não oficial e o número pode ser banido. Para produção, o
> projeto deve voltar à API oficial da Meta. O código dessa versão está na tag git `meta-cloud-api`.

Versão fixada: `evoapicloud/evolution-api:v2.3.7`, a última estável. A 2.4.0 passou a exigir
ativação de licença no servidor da Evolution Foundation, e a 2.3.7 não exige.

Pareie um chip só do projeto. O número pareado é o "robô": mensagens enviadas por ele mesmo são
ignoradas. Para testar, escreva **de outro celular** cadastrado como colaborador, gerente ou RH.

## 1. Preencher os segredos

Dentro de `evolution/`, no PowerShell:

```powershell
Copy-Item .env.example .env
```

Abra `evolution/.env` e preencha `AUTHENTICATION_API_KEY` e `POSTGRES_PASSWORD` com valores longos
e aleatórios. Para gerar um valor:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

O `evolution/.env` está no `.gitignore` e não vai para o Git.

## 2. Subir a Evolution

```powershell
docker compose up -d
```

Sobem três contêineres: a Evolution, um Postgres e um Redis só dela. As portas ficam presas a
127.0.0.1: a Evolution responde em `http://localhost:8081`, porque o backend já usa a 8080.

A Evolution só guarda a instância no Postgres dela. A gravação de mensagens, contatos, conversas,
etiquetas e histórico está desligada (`DATABASE_SAVE_DATA_*` em `docker-compose.yml`), porque o
termo promete guarda de até 90 dias.

## 3. Criar a instância

No mesmo PowerShell, informe a chave global (ela não fica gravada em arquivo):

```powershell
$chave = Read-Host "AUTHENTICATION_API_KEY"
$base = "http://localhost:8081"
$criada = Invoke-RestMethod -Method Post -Uri "$base/instance/create" -Headers @{ apikey = $chave } -ContentType "application/json" -Body '{"instanceName":"parceiro","integration":"WHATSAPP-BAILEYS","qrcode":true}'
```

## 4. Ler o QR code com o chip do projeto

```powershell
$qr = Invoke-RestMethod -Uri "$base/instance/connect/parceiro" -Headers @{ apikey = $chave }
Set-Content -Path qrcode.html -Value "<img src='$($qr.base64)'>"
Start-Process qrcode.html
```

No celular com o chip do projeto: **WhatsApp → Aparelhos conectados → Conectar aparelho**, e leia o
QR. Ele expira em poucos segundos; se expirar, rode o bloco de novo. Depois apague o `qrcode.html`.

Confira se conectou. O estado deve ser `open`:

```powershell
Invoke-RestMethod -Uri "$base/instance/connectionState/parceiro" -Headers @{ apikey = $chave }
```

## 5. Configurar o webhook

O webhook leva o segredo compartilhado no cabeçalho `X-Webhook-Secret`. Use o mesmo valor que vai
em `EVOLUTION_WEBHOOK_SECRET` no `backend/.env`, e assine só o evento de mensagem:

```powershell
$segredo = Read-Host "EVOLUTION_WEBHOOK_SECRET"
$corpo = @{ webhook = @{ enabled = $true; url = "http://host.docker.internal:8080/webhook/evolution"; byEvents = $false; base64 = $false; events = @("MESSAGES_UPSERT"); headers = @{ "X-Webhook-Secret" = $segredo } } } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$base/webhook/set/parceiro" -Headers @{ apikey = $chave } -ContentType "application/json" -Body $corpo
Invoke-RestMethod -Uri "$base/webhook/find/parceiro" -Headers @{ apikey = $chave }
```

O `find` deve mostrar `enabled: true`, a URL acima e só `MESSAGES_UPSERT`.

Na Evolution 2.3.7, o webhook global (configurado por variável de ambiente) não envia cabeçalhos.
Por isso o webhook é configurado por instância.

## 6. Ligar o backend

Em `backend/.env`:

```properties
WHATSAPP_CANAL=evolution
EVOLUTION_API_URL=http://localhost:8081
EVOLUTION_API_KEY=<a chave da Evolution>
EVOLUTION_INSTANCE=parceiro
EVOLUTION_WEBHOOK_SECRET=<o mesmo segredo do passo 5>
```

Em `EVOLUTION_API_KEY`, prefira a chave da instância, o campo `hash` da resposta do passo 3
(`$criada.hash`), que só vale para essa instância. Se o envio for recusado com 401, use a
`AUTHENTICATION_API_KEY`.

Depois suba o backend (`backend/README.md`) e escreva "oi" para o chip do projeto, de um celular
cadastrado.

## Parar e apagar

```powershell
docker compose down      # para os contêineres e mantém o número conectado
docker compose down -v   # apaga também a instância e o Postgres da Evolution (pede novo QR)
```
