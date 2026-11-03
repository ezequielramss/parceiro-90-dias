# IA — classificação de relatos (H18)

Prompt, esquema de saída e avaliação do classificador de relatos curtos enviados pelo WhatsApp.
A IA classifica na taxonomia aprovada e sugere uma ação. Ela não faz diagnóstico, não julga a
pessoa e não dá percentual de risco.

```
ia/
├── classificacao/
│   ├── prompt.md               prompt de sistema (fonte única; o backend Java vai ler daqui)
│   └── saida.schema.json       esquema JSON da resposta
├── casos/
│   ├── dev/casos.jsonl         72 casos fictícios para ajustar o prompt
│   └── teste/casos.jsonl       72 casos fictícios da avaliação final (só o PO roda, uma vez)
├── avaliar.py                  roda os casos no Gemini e imprime o relatório
├── tests/test_avaliar.py       testes do script (sem rede e sem chave)
└── requirements.txt
```

Os casos são inventados: 6 por categoria em cada pasta, com relatos curtos, com gíria, com erro
de português, vagos, ambíguos e fora do tema. Nenhum texto se repete entre `dev`, `teste` e o
prompt.

## Saída

| Campo | Conteúdo |
|---|---|
| `categoria` | um dos 12 códigos da tabela `categorias` |
| `motivo` | uma frase neutra com o que levou à categoria (é o resumo mostrado ao colaborador e ao RH) |
| `acao_sugerida` | uma frase para quem recebe: gerente, RH ou os dois |
| `confianca` | `baixa`, `media` ou `alta` |
| `sinais_graves` | `true` quando há indício de risco à integridade, violência, ameaça, assédio sexual, crime ou menção a se machucar |

## Como rodar

Precisa de Python 3.12 e de uma chave do Google AI Studio (https://aistudio.google.com/apikey).
No PowerShell, a partir de `ia/`:

```powershell
python -m pip install -r requirements.txt
$env:GEMINI_API_KEY = 'sua-chave'          # só nesta janela; nunca em arquivo
python avaliar.py --listar-modelos
python avaliar.py dev
python avaliar.py dev --retomar            # continua de onde parou
```

| Opção | Para quê |
|---|---|
| `--rpm N` | máximo de requisições por minuto (padrão 5). As chamadas são feitas uma por vez. |
| `--retomar` | pula os casos que já têm resposta válida em `resultados/` |
| `--modelo X` ou `GEMINI_MODEL` | troca o modelo (a opção na linha de comando vence a variável) |
| `--rodar-teste` | obrigatório para a pasta `teste`; só o PO usa, uma vez, no fim |

Cada caso classificado é gravado na hora em
`resultados/<pasta>-<modelo>-<versão do prompt>.jsonl` (fora do Git). Mudar o prompt ou o
esquema muda a versão e começa um arquivo novo, para não misturar respostas de prompts
diferentes.

### Limites do nível gratuito

- **Limite por minuto** (erro 429 com cota `...PerMinute...`): o script espera o tempo que a
  API pedir (`retryDelay`) e tenta de novo, até 6 vezes.
- **Cota diária** (erro 429 com cota `...PerDay...`): o script para na hora, avisa e mostra o
  relatório parcial. No dia seguinte, ou com outro modelo, rode com `--retomar`.
- **Alta demanda** (erro 503): espera crescente e nova tentativa.

## Modelo

`MODELO_PADRAO = "gemini-3.1-flash-lite"`, escolhido porque:

- é uma versão estável (sem `-preview`), então o nome não some de uma hora para outra;
- está no nível gratuito e é da família Flash-Lite, que tem a maior cota por minuto e por dia;
- a tarefa é uma classificação curta com saída em JSON, que não precisa de um modelo maior.

Descartados: `gemini-2.5-flash` (responde 404, "no longer available to new users") e
`gemini-3-flash-preview` (é preview e esgotou a cota gratuita nos testes de 02/11/2026).
Se o Flash-Lite errar demais, troque com `GEMINI_MODEL` sem mexer no código.

> Escolha provisória: em 03/11/2026 a chave de teste passou a ser recusada (401) e a lista de
> modelos não pôde ser conferida de novo. Confirme com `--listar-modelos` antes da avaliação.

## Relatório

O script imprime os acertos por categoria e separa três tipos de erro:

1. **Erro grave:** categoria restrita (`CONFLITO_CHEFIA`, `ASSEDIO`, `HUMILHACAO`,
   `SITUACAO_GRAVE`) classificada como uma categoria que vai ao gerente. O prompt só é
   aprovado com zero erros graves.
2. Categoria restrita classificada como `NAO_CLASSIFICADO`: vai ao RH, então não é grave.
3. Demais erros.

## Testes

```powershell
python -m unittest discover -s tests -v
```

Cobrem: leitura do erro 429 (por minuto ou diária), espaçamento do `--rpm`, nova tentativa
com a espera pedida, parada na cota diária, retomada sem repetir casos, JSON inválido e a regra
de erro grave. Não usam rede nem chave.

## Privacidade

O nível gratuito do Gemini pode usar o conteúdo enviado para melhorar os produtos do Google.
Use-o só com os casos fictícios desta pasta. Relatos reais de colaboradores exigem outro
contrato e a validação do jurídico.
