# Textos do assistente (Etapa 1)

Tudo o que o Parceiro dos 90 dias escreve no WhatsApp durante a Etapa 1, para revisão do PO,
do RH e do jurídico. O código está em
`backend/src/main/java/br/com/kallopolli/parceiro/conversa/TextosConversa.java`. Um teste
automático falha se um texto fixo daqui ficar diferente do código, então **mudanças de texto
são feitas nos dois lugares**.

O termo de aceite v1 não está aqui: ele continua em `consentimento/Textos.java`.

**Regras de tom:**
- português do Brasil, "você", frases curtas, sem gíria;
- linguagem neutra ao falar com o colaborador: "Agradeço", "Boas-vindas", "Fico à disposição",
  nunca "Obrigado", "bem-vindo", "tranquilo" e semelhantes;
- no máximo um emoji por mensagem e nenhum nas mensagens graves;
- nada de diagnóstico, termos clínicos, pontuação ou linguagem de vigilância;
- sobre privacidade, sempre "confidencial, não anônimo"; nunca "100%".

**Limites e opções:**
- texto: até 1000 caracteres (os que têm campos variáveis ficam em até 1024 mesmo com entradas enormes);
- não há botões: o canal (Evolution API ou simulador local) só envia texto. As opções vão escritas no fim da mensagem, e a pessoa responde digitando. Maiúsculas, acentos e pontuação não importam;
- números não são opção: 1 a 5 é a nota do check-in;
- neste canal não há janela de 24 horas nem modelos de mensagem.

Os campos entre chaves (`{loja}`, `{motivo}` etc.) são preenchidos pelo sistema. Valores longos
são cortados com "…" para caber no limite.

## 1. Boas-vindas depois do aceite (H03)

```text
Que bom ter você por aqui! 👋 Eu sou o Parceiro dos 90 dias, o assistente que acompanha seus primeiros 90 dias na empresa pelo WhatsApp.

De vez em quando vou perguntar como você está, com uma nota de 1 a 5. Se quiser, você também pode me contar o que está acontecendo, a qualquer hora.

Participar é voluntário. Este canal é confidencial, não anônimo: o RH e o gerente da sua loja veem a nota de ânimo que você informar.

Para parar a qualquer momento, escreva PARAR.
```

## 2. Check-in (H04)

Dia 1, com o primeiro nome. Sem nome cadastrado, a saudação vira só "Oi!".

```text
Oi, {primeiroNome}! Aqui é o Parceiro dos 90 dias. Hoje começa a sua jornada e eu queria saber: como você está?

Responda com uma nota de 1 a 5 (1 = muito mal, 2 = mal, 3 = mais ou menos, 4 = bem, 5 = muito bem).
```

Dias 2 a 10:

```text
Oi, {primeiroNome}! Como você está hoje?

Responda com uma nota de 1 a 5 (1 = muito mal, 2 = mal, 3 = mais ou menos, 4 = bem, 5 = muito bem).
```

## 3. Resposta à nota

Notas 1 e 2 (convida a contar, sem insistir e sem prometer que alguém será avisado):

```text
Agradeço por você contar. Sinto muito que hoje não esteja sendo um bom dia. Se quiser, me conte com suas palavras o que está acontecendo. Se preferir não falar agora, tudo bem.
```

Nota 3:

```text
Agradeço a sua resposta. Se quiser contar algo sobre o seu dia, é só escrever.
```

Notas 4 e 5:

```text
Que bom saber disso! 🙂 Agradeço a sua resposta.
```

Mensagem vazia ou só com espaços (não é gravada como relato):

```text
Pode me contar com suas palavras o que está acontecendo. Estou por aqui para ouvir.
```

## 4. Confirmação do relato (H16)

O `{destinatario}` depende da categoria: "o gerente da sua loja", "o RH" ou "o gerente da sua
loja e o RH".

```text
Entendi assim: {motivo}

Posso encaminhar para {destinatario}? Este canal é confidencial, não anônimo.

Se eu entendi errado, é só escrever de novo do seu jeito.

Responda SIM para enviar ou NÃO para descartar.
```

Respostas: **SIM** encaminha e **NÃO** descarta. Só valem enquanto a conversa aguarda esta confirmação; fora disso, "sim" e "não" seguem o fluxo normal. **NÃO CONSEGUI** não conta como NÃO. Qualquer outro texto substitui o relato e é classificado de novo.

## 5. Depois da confirmação

Encaminhado:

```text
Pronto, encaminhei. Agradeço a confiança. Se quiser acrescentar algo, é só escrever.
```

Nada foi enviado:

```text
Tudo bem, não enviei nada. Se mudar de ideia ou quiser contar de outro jeito, é só escrever.
```

## 6. Relato urgente

Assunto grave, que vai direto ao RH sem esperar confirmação. Sem emoji, sem orientação de
emergência e sem números de telefone.

```text
Agradeço por você contar. O que você relatou é sério, e por isso o RH já foi avisado. Uma pessoa do RH vai procurar você para conversar. Você não precisa fazer mais nada agora.
```

## 7. IA indisponível (H37)

```text
Recebi seu relato, agradeço por você contar. Não consegui analisar a mensagem agora, então uma pessoa do RH vai olhar com atenção.
```

## 8. Elogio recebido

```text
Que bom ler isso! 😊 Agradeço por compartilhar.
```

## 9. Áudio, imagem, vídeo ou figurinha

```text
Por enquanto eu só consigo ler mensagens de texto. Se quiser, escreva com suas palavras o que queria me mandar.
```

## 10. Alerta ao gerente (H17)

O alerta ao gerente **nunca** leva o nome do colaborador, o motivo nem o texto do relato.
Loja ou turno vazio aparece como "não informado".

```text
Parceiro dos 90 dias: novo alerta de acompanhamento

Loja: {loja}
Turno: {turno}
Assunto: {rótulo da categoria}
Sugestão: {acaoSugerida}

Este alerta é confidencial. Depois do acompanhamento, responda CONVERSEI ou NÃO CONSEGUI.
```

Respostas: **CONVERSEI** ou **NÃO CONSEGUI**, digitadas. Valem para o alerta mais recente ainda aberto daquele número; sem alerta aberto, o texto segue o fluxo normal. NÃO CONSEGUI é conferido antes de NÃO.

## 11. Alertas ao RH

O alerta ao RH leva o motivo e **nunca** leva o nome nem o texto do relato. As respostas são as
mesmas do item 10: **CONVERSEI** ou **NÃO CONSEGUI**.

Normal:

```text
Parceiro dos 90 dias: novo relato para o RH

Loja: {loja}
Turno: {turno}
Assunto: {rótulo da categoria}
Resumo: {motivo}
Sugestão: {acaoSugerida}

Atenção: loja e turno podem permitir identificar a pessoa. Trate com confidencialidade.

Depois do contato, responda CONVERSEI ou NÃO CONSEGUI.
```

Urgente: o mesmo corpo, com outro título.

```text
URGENTE: relato que pede contato ainda hoje

Loja: {loja}
Turno: {turno}
Assunto: {rótulo da categoria}
Resumo: {motivo}
Sugestão: {acaoSugerida}

Atenção: loja e turno podem permitir identificar a pessoa. Trate com confidencialidade.

Depois do contato, responda CONVERSEI ou NÃO CONSEGUI.
```

Sem classificação (a IA falhou):

```text
Parceiro dos 90 dias: relato sem classificação

Loja: {loja}
Turno: {turno}

A análise automática não funcionou para este relato. Ele está registrado no sistema e precisa da leitura de uma pessoa do RH.

Atenção: loja e turno podem permitir identificar a pessoa. Trate com confidencialidade.

Depois do contato, responda CONVERSEI ou NÃO CONSEGUI.
```

## 12. Resposta ao responsável depois de CONVERSEI ou NÃO CONSEGUI

Conversei ("contato realizado" não é "resolvido"):

```text
Registrado: contato realizado. O caso segue em acompanhamento até o RH ou o gestor marcar como resolvido.
```

Não consegui:

```text
Registrado. O alerta continua pendente. Quando conseguir conversar, responda CONVERSEI.
```

## 13. Responsável que escreve fora de um alerta

```text
Oi! Por aqui eu envio os alertas de acompanhamento quando houver algum. Quando chegar um, é só responder CONVERSEI ou NÃO CONSEGUI.
```

## 14. Rótulos das categorias

Usados no campo "Assunto" dos alertas.

| Código | Rótulo |
|---|---|
| CANSACO | Cansaço |
| SOBRECARGA | Sobrecarga de trabalho |
| DESMOTIVACAO | Desmotivação |
| DUVIDA | Dúvida sobre rotina ou benefícios |
| CONFLITO_COLEGA | Conflito com colega |
| VONTADE_DE_SAIR | Vontade de sair da empresa |
| CONFLITO_CHEFIA | Conflito com a chefia |
| ASSEDIO | Assédio |
| HUMILHACAO | Humilhação ou constrangimento |
| SITUACAO_GRAVE | Situação grave |
| ELOGIO | Elogio |
| NAO_CLASSIFICADO | Assunto não classificado |

Um código desconhecido aparece como "Assunto não identificado".

## O que mudou em relação à versão com botões (Java D)

A WhatsApp Cloud API da Meta saiu do MVP (a versão com botões está na tag `meta-cloud-api`). O resto dos textos aprovados não mudou.

| Texto | Antes (botões da Meta) | Agora (texto) |
|---|---|---|
| 4. Confirmação do relato | Botões **Sim, pode enviar** e **Não enviar** | Linha "Responda SIM para enviar ou NÃO para descartar." no fim |
| 10 e 11. Alertas | "...toque em uma das opções abaixo." + botões **Conversei** e **Não consegui** | "...responda CONVERSEI ou NÃO CONSEGUI." |
| 12. Não consegui | "...toque em Conversei." | "...responda CONVERSEI." |
| 13. Responsável fora de alerta | "...é só usar os botões da mensagem." | "...é só responder CONVERSEI ou NÃO CONSEGUI." |

Internamente, as respostas digitadas viram as mesmas ações de antes (`conf:<relatoId>:sim|nao` e `alerta:<entregaId>:ok|nao`), e a auditoria mantém os nomes `TOQUE_CONVERSEI` e `TOQUE_NAO_CONSEGUI`.
