"""Avalia o prompt de classificação de relatos (H18) contra os casos fictícios.

Usa a API do Gemini (Google AI Studio) no nível gratuito.

Uso:
    python avaliar.py --listar-modelos             # modelos disponíveis para a sua chave
    python avaliar.py dev                          # ajuste do prompt
    python avaliar.py dev --retomar                # continua de onde parou (ex.: após cota diária)
    python avaliar.py teste --rodar-teste          # avaliação final: só o PO, uma vez, no fim

A chave vem da variável de ambiente GEMINI_API_KEY e o modelo, opcionalmente, de GEMINI_MODEL.
Nunca coloque a chave em arquivo. O nível gratuito pode usar o conteúdo enviado para melhorar
os produtos do Google: use-o só com os casos fictícios deste repositório, nunca com relatos reais.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

from google import genai
from google.genai import errors, types

RAIZ = Path(__file__).resolve().parent
PROMPT = (RAIZ / "classificacao" / "prompt.md").read_text(encoding="utf-8")
ESQUEMA = json.loads((RAIZ / "classificacao" / "saida.schema.json").read_text(encoding="utf-8"))

# Escolha explicada em ia/README.md. GEMINI_MODEL no ambiente tem precedência.
MODELO_PADRAO = "gemini-3.1-flash-lite"
RPM_PADRAO = 5
TENTATIVAS = 6
ESPERA_MAXIMA = 120  # segundos

# Destinos (tabela public.categorias).
RESTRITAS = {"CONFLITO_CHEFIA", "ASSEDIO", "HUMILHACAO", "SITUACAO_GRAVE"}
CHEGAM_AO_GERENTE = {
    "CANSACO", "SOBRECARGA", "DESMOTIVACAO", "DUVIDA", "CONFLITO_COLEGA", "VONTADE_DE_SAIR", "ELOGIO",
}
CATEGORIAS = ESQUEMA["properties"]["categoria"]["enum"]


class CotaDiariaEsgotada(Exception):
    """A cota diária do nível gratuito acabou: não adianta repetir hoje."""


class LimiteRpm:
    """Espaça o início das chamadas para não passar de `rpm` por minuto."""

    def __init__(self, rpm: int, relogio=time.monotonic, dormir=time.sleep):
        self.intervalo = 60.0 / rpm
        self.relogio = relogio
        self.dormir = dormir
        self.ultima: float | None = None

    def aguardar(self) -> None:
        agora = self.relogio()
        if self.ultima is not None:
            falta = self.ultima + self.intervalo - agora
            if falta > 0:
                self.dormir(falta)
                agora += falta
        self.ultima = agora


def interpretar_429(detalhes) -> tuple[str, float | None]:
    """Lê o corpo do erro 429 e diz se é limite por minuto ou cota diária, e quanto esperar.

    O Gemini manda um QuotaFailure com o quotaId violado (ex.: "...PerDay..." ou
    "...PerMinute...") e um RetryInfo com retryDelay (ex.: "41s").
    """
    itens = []
    if isinstance(detalhes, dict):
        itens = detalhes.get("error", {}).get("details", []) or []
    cotas = [
        v.get("quotaId", "")
        for d in itens if "QuotaFailure" in d.get("@type", "")
        for v in d.get("violations", [])
    ]
    espera = None
    for d in itens:
        if "RetryInfo" in d.get("@type", ""):
            m = re.fullmatch(r"([\d.]+)s", str(d.get("retryDelay", "")))
            if m:
                espera = float(m.group(1))
    if any("PerDay" in c for c in cotas):
        return "diaria", espera
    return "minuto", espera


def carregar_casos(pasta: str) -> list[dict]:
    caminho = RAIZ / "casos" / pasta / "casos.jsonl"
    with caminho.open(encoding="utf-8") as f:
        return [json.loads(linha) for linha in f if linha.strip()]


def arquivo_parcial(pasta: str, modelo: str) -> Path:
    """Um arquivo por pasta, modelo e versão do prompt+esquema: mudar o prompt começa do zero."""
    versao = hashlib.sha256((PROMPT + json.dumps(ESQUEMA, sort_keys=True)).encode()).hexdigest()[:10]
    return RAIZ / "resultados" / f"{pasta}-{modelo}-{versao}.jsonl"


def carregar_feitos(caminho: Path) -> dict[str, dict]:
    """Casos já classificados com resposta válida (sem erro), por id; o último registro vale."""
    feitos: dict[str, dict] = {}
    if caminho.exists():
        with caminho.open(encoding="utf-8") as f:
            for linha in f:
                if linha.strip():
                    r = json.loads(linha)
                    if "erro" not in r["saida"]:
                        feitos[r["id"]] = r
                    else:
                        feitos.pop(r["id"], None)
    return feitos


def classificar(client, modelo: str, texto: str, limite: LimiteRpm, dormir=time.sleep) -> dict:
    """Uma chamada por relato. Devolve o JSON da classificação ou {"erro": ...}.

    Lança CotaDiariaEsgotada quando a cota do dia acabou.
    """
    config = types.GenerateContentConfig(
        system_instruction=PROMPT,
        response_mime_type="application/json",
        response_json_schema=ESQUEMA,
    )
    for tentativa in range(TENTATIVAS):
        limite.aguardar()
        try:
            resposta = client.models.generate_content(
                model=modelo, contents=f"<relato>\n{texto}\n</relato>", config=config)
            break
        except errors.ClientError as e:
            if e.code != 429:
                raise
            tipo, espera = interpretar_429(e.details)
            if tipo == "diaria":
                raise CotaDiariaEsgotada() from None
            if tentativa == TENTATIVAS - 1:
                return {"erro": "API 429 (limite por minuto, tentativas esgotadas)"}
            dormir(min(ESPERA_MAXIMA, (espera or 10 * 2 ** tentativa) + 1))
        except errors.ServerError as e:
            if tentativa == TENTATIVAS - 1:
                return {"erro": f"API {e.code}"}
            dormir(min(ESPERA_MAXIMA, 10 * 2 ** tentativa))

    if resposta.text is None:
        motivo = resposta.candidates[0].finish_reason if resposta.candidates else "sem candidato"
        return {"erro": f"resposta vazia ({motivo})"}
    try:
        return json.loads(resposta.text)
    except json.JSONDecodeError:
        return {"erro": "JSON inválido"}


def relatorio(resultados: list[dict]) -> dict:
    por_categoria = defaultdict(lambda: {"total": 0, "acertos": 0})
    erros_, graves, restrita_para_nao_classificado, falhas = [], [], [], []
    confusao = Counter()

    for r in resultados:
        esperada = r["esperada"]
        prevista = r["saida"].get("categoria")
        por_categoria[esperada]["total"] += 1
        if "erro" in r["saida"]:
            falhas.append(r)
            continue
        if prevista == esperada:
            por_categoria[esperada]["acertos"] += 1
            continue
        erros_.append(r)
        confusao[(esperada, prevista)] += 1
        if esperada in RESTRITAS and prevista in CHEGAM_AO_GERENTE:
            graves.append(r)
        elif esperada in RESTRITAS and prevista == "NAO_CLASSIFICADO":
            restrita_para_nao_classificado.append(r)

    total = len(resultados)
    acertos = sum(c["acertos"] for c in por_categoria.values())
    return {
        "total": total,
        "acertos": acertos,
        "por_categoria": {c: por_categoria[c] for c in CATEGORIAS if c in por_categoria},
        "erros": erros_,
        "graves": graves,
        "restrita_para_nao_classificado": restrita_para_nao_classificado,
        "falhas": falhas,
        "confusao": confusao,
    }


def imprimir(rel: dict, pasta: str, modelo: str, pendentes: int) -> None:
    print(f"\nAvaliação '{pasta}' — modelo {modelo}")
    if pendentes:
        print(f"PARCIAL: {pendentes} caso(s) ainda sem resposta válida. Rode de novo com --retomar.")
    if rel["total"]:
        print(f"Acertos: {rel['acertos']}/{rel['total']} ({100 * rel['acertos'] / rel['total']:.1f}%)\n")
    print(f"{'Categoria':<18} {'Acertos':>8}")
    for categoria, c in rel["por_categoria"].items():
        print(f"{categoria:<18} {c['acertos']:>3}/{c['total']:<4}")

    print(f"\nErros graves (restrita classificada como categoria que vai ao gerente): {len(rel['graves'])}")
    for r in rel["graves"]:
        print(f"  [{r['id']}] esperada {r['esperada']} -> {r['saida']['categoria']}: {r['texto']}")

    print(f"\nRestrita classificada como NAO_CLASSIFICADO (vai ao RH, não é grave): "
          f"{len(rel['restrita_para_nao_classificado'])}")
    for r in rel["restrita_para_nao_classificado"]:
        print(f"  [{r['id']}] esperada {r['esperada']}: {r['texto']}")

    outros = [r for r in rel["erros"] if r not in rel["graves"] and r not in rel["restrita_para_nao_classificado"]]
    print(f"\nDemais erros: {len(outros)}")
    for r in outros:
        print(f"  [{r['id']}] esperada {r['esperada']} -> {r['saida']['categoria']} "
              f"({r['saida']['confianca']}): {r['texto']}")

    if rel["falhas"]:
        print(f"\nFalhas de execução: {len(rel['falhas'])}")
        for r in rel["falhas"]:
            print(f"  [{r['id']}] {r['saida']['erro']}")


def avaliar(client, pasta: str, modelo: str, rpm: int, retomar: bool) -> int:
    casos = carregar_casos(pasta)
    caminho = arquivo_parcial(pasta, modelo)
    caminho.parent.mkdir(exist_ok=True)
    feitos = carregar_feitos(caminho) if retomar else {}
    if not retomar and caminho.exists():
        caminho.unlink()
    a_fazer = [c for c in casos if c["id"] not in feitos]
    print(f"{len(casos)} casos, {len(feitos)} já classificados, {len(a_fazer)} a fazer "
          f"(até {rpm} por minuto). Parcial em {caminho}", flush=True)

    limite = LimiteRpm(rpm)
    parou_por_cota = False
    with caminho.open("a", encoding="utf-8") as saida:
        for n, caso in enumerate(a_fazer, 1):
            try:
                resultado = classificar(client, modelo, caso["texto"], limite)
            except CotaDiariaEsgotada:
                parou_por_cota = True
                break
            except errors.ClientError as e:
                if e.code in (401, 403):
                    sys.exit("Chave inválida ou sem permissão: confira GEMINI_API_KEY.")
                resultado = {"erro": f"API {e.code}"}
            registro = {**caso, "saida": resultado}
            saida.write(json.dumps(registro, ensure_ascii=False) + "\n")
            saida.flush()
            if "erro" not in resultado:
                feitos[caso["id"]] = registro
            obtido = resultado.get("categoria") or resultado.get("erro")
            marca = "ok" if obtido == caso["esperada"] else "ERRO"
            print(f"[{n}/{len(a_fazer)}] {caso['id']}: {obtido} ({marca})", flush=True)

    if parou_por_cota:
        print("\nCOTA DIÁRIA ESGOTADA para este modelo no nível gratuito. A avaliação parou aqui; "
              "o que já foi classificado está salvo. Amanhã (ou com outro modelo em GEMINI_MODEL), "
              "rode de novo com --retomar.", flush=True)

    respondidos = [feitos[c["id"]] for c in casos if c["id"] in feitos]
    pendentes = len(casos) - len(respondidos)
    imprimir(relatorio(respondidos), pasta, modelo, pendentes)
    return 2 if pendentes else 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pasta", nargs="?", choices=["dev", "teste"])
    parser.add_argument("--rodar-teste", action="store_true",
                        help="obrigatório para a pasta teste, que só o PO roda, uma vez, no fim")
    parser.add_argument("--modelo", default=os.environ.get("GEMINI_MODEL") or MODELO_PADRAO)
    parser.add_argument("--rpm", type=int, default=RPM_PADRAO, help="máximo de requisições por minuto")
    parser.add_argument("--retomar", action="store_true",
                        help="pula os casos que já têm resposta válida em ia/resultados/")
    parser.add_argument("--listar-modelos", action="store_true")
    args = parser.parse_args()

    if not os.environ.get("GEMINI_API_KEY"):
        sys.exit("Defina GEMINI_API_KEY com uma chave do Google AI Studio.")
    client = genai.Client()

    if args.listar_modelos:
        for m in client.models.list():
            if "generateContent" in (m.supported_actions or []):
                print(m.name.removeprefix("models/"))
        return
    if args.pasta is None:
        parser.error("informe a pasta: dev ou teste")
    if args.pasta == "teste" and not args.rodar_teste:
        sys.exit("A pasta teste não serve para ajustar o prompt. Use --rodar-teste só na avaliação final.")
    if args.rpm < 1:
        parser.error("--rpm precisa ser pelo menos 1")

    sys.exit(avaliar(client, args.pasta, args.modelo, args.rpm, args.retomar))


if __name__ == "__main__":
    main()
