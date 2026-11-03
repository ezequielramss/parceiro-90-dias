"""Testes do ia/avaliar.py. Sem rede e sem chave: o cliente do Gemini é falso.

Rodar, a partir de ia/:  python -m unittest discover -s tests -v
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import avaliar  # noqa: E402
from google.genai import errors  # noqa: E402


def erro_429(quota_id: str | None, espera: str | None = None) -> errors.ClientError:
    detalhes = []
    if quota_id:
        detalhes.append({"@type": "type.googleapis.com/google.rpc.QuotaFailure",
                         "violations": [{"quotaId": quota_id}]})
    if espera:
        detalhes.append({"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": espera})
    return errors.ClientError(429, {"error": {"code": 429, "message": "cota", "status": "RESOURCE_EXHAUSTED",
                                              "details": detalhes}})


def resposta(categoria: str = "CANSACO") -> SimpleNamespace:
    corpo = {"categoria": categoria, "motivo": "m", "acao_sugerida": "a",
             "confianca": "alta", "sinais_graves": False}
    return SimpleNamespace(text=json.dumps(corpo), candidates=[])


class ClienteFalso:
    """Devolve, em ordem, as respostas ou exceções da lista; depois repete a última função."""

    def __init__(self, roteiro):
        self.roteiro = list(roteiro)
        self.chamadas = 0
        self.models = SimpleNamespace(generate_content=self._gerar)

    def _gerar(self, model, contents, config):
        self.chamadas += 1
        item = self.roteiro.pop(0) if len(self.roteiro) > 1 else self.roteiro[0]
        if callable(item):
            return item(contents)
        if isinstance(item, Exception):
            raise item
        return item


SEM_ESPERA = avaliar.LimiteRpm(60_000, dormir=lambda s: None)


class Interpretar429Test(unittest.TestCase):

    def test_cota_diaria(self):
        e = erro_429("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "41s")
        self.assertEqual(avaliar.interpretar_429(e.details), ("diaria", 41.0))

    def test_limite_por_minuto_com_espera(self):
        e = erro_429("GenerateRequestsPerMinutePerProjectPerModel-FreeTier", "12.5s")
        self.assertEqual(avaliar.interpretar_429(e.details), ("minuto", 12.5))

    def test_429_sem_detalhes_e_tratado_como_por_minuto(self):
        self.assertEqual(avaliar.interpretar_429(erro_429(None).details), ("minuto", None))
        self.assertEqual(avaliar.interpretar_429(None), ("minuto", None))


class LimiteRpmTest(unittest.TestCase):

    def test_espaca_as_chamadas(self):
        tempo = [0.0]
        dormidas = []

        def dormir(s):
            dormidas.append(s)
            tempo[0] += s

        limite = avaliar.LimiteRpm(6, relogio=lambda: tempo[0], dormir=dormir)  # uma a cada 10 s
        limite.aguardar()
        tempo[0] = 3.0
        limite.aguardar()
        tempo[0] = 30.0
        limite.aguardar()
        self.assertEqual(dormidas, [7.0])


class ClassificarTest(unittest.TestCase):

    def test_429_por_minuto_espera_o_tempo_pedido_e_tenta_de_novo(self):
        cliente = ClienteFalso([erro_429("PerMinute", "12s"), resposta("DUVIDA")])
        dormidas = []
        saida = avaliar.classificar(cliente, "m", "texto", SEM_ESPERA, dormir=dormidas.append)
        self.assertEqual(saida["categoria"], "DUVIDA")
        self.assertEqual(dormidas, [13.0])
        self.assertEqual(cliente.chamadas, 2)

    def test_cota_diaria_para_sem_repetir(self):
        cliente = ClienteFalso([erro_429("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "41s")])
        with self.assertRaises(avaliar.CotaDiariaEsgotada):
            avaliar.classificar(cliente, "m", "texto", SEM_ESPERA, dormir=lambda s: None)
        self.assertEqual(cliente.chamadas, 1)

    def test_429_por_minuto_esgotando_tentativas_vira_erro(self):
        cliente = ClienteFalso([erro_429("PerMinute", "1s")])
        saida = avaliar.classificar(cliente, "m", "texto", SEM_ESPERA, dormir=lambda s: None)
        self.assertIn("erro", saida)
        self.assertEqual(cliente.chamadas, avaliar.TENTATIVAS)

    def test_json_invalido_vira_erro(self):
        cliente = ClienteFalso([SimpleNamespace(text="nao e json", candidates=[])])
        self.assertEqual(avaliar.classificar(cliente, "m", "t", SEM_ESPERA), {"erro": "JSON inválido"})

    def test_relato_vai_dentro_da_tag(self):
        recebido = []
        cliente = ClienteFalso([lambda contents: recebido.append(contents) or resposta()])
        avaliar.classificar(cliente, "m", "texto ficticio", SEM_ESPERA)
        self.assertEqual(recebido, ["<relato>\ntexto ficticio\n</relato>"])


class RetomarTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.parcial = Path(self.dir.name) / "dev-parcial.jsonl"
        self.casos = avaliar.carregar_casos("dev")
        esperada = {c["texto"]: c["esperada"] for c in self.casos}
        self.acerta_tudo = lambda contents: resposta(esperada[contents.split("\n")[1]])
        patches = [
            mock.patch.object(avaliar, "arquivo_parcial", lambda pasta, modelo: self.parcial),
            mock.patch.object(avaliar, "LimiteRpm", lambda rpm: SEM_ESPERA),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def tearDown(self):
        self.dir.cleanup()

    def test_para_na_cota_diaria_e_retoma_so_o_que_falta(self):
        diaria = erro_429("GenerateRequestsPerDayPerProjectPerModel-FreeTier")
        primeira = ClienteFalso([self.acerta_tudo] * 3 + [diaria, diaria])
        with mock.patch("builtins.print"):
            codigo = avaliar.avaliar(primeira, "dev", "m", 5, retomar=False)
        self.assertEqual(codigo, 2)
        self.assertEqual(len(avaliar.carregar_feitos(self.parcial)), 3)

        segunda = ClienteFalso([self.acerta_tudo])
        with mock.patch("builtins.print"):
            codigo = avaliar.avaliar(segunda, "dev", "m", 5, retomar=True)
        self.assertEqual(codigo, 0)
        self.assertEqual(segunda.chamadas, len(self.casos) - 3)
        self.assertEqual(len(avaliar.carregar_feitos(self.parcial)), len(self.casos))

    def test_sem_retomar_comeca_do_zero(self):
        with mock.patch("builtins.print"):
            avaliar.avaliar(ClienteFalso([self.acerta_tudo]), "dev", "m", 5, retomar=False)
            cliente = ClienteFalso([self.acerta_tudo])
            avaliar.avaliar(cliente, "dev", "m", 5, retomar=False)
        self.assertEqual(cliente.chamadas, len(self.casos))

    def test_resposta_com_erro_nao_conta_como_feita(self):
        linhas = [
            {"id": "a", "esperada": "CANSACO", "texto": "x", "saida": {"erro": "API 429"}},
            {"id": "b", "esperada": "CANSACO", "texto": "y", "saida": {"categoria": "CANSACO"}},
        ]
        self.parcial.write_text("".join(json.dumps(l) + "\n" for l in linhas), encoding="utf-8")
        self.assertEqual(set(avaliar.carregar_feitos(self.parcial)), {"b"})


class RelatorioTest(unittest.TestCase):

    def test_restrita_indo_ao_gerente_e_erro_grave(self):
        r = avaliar.relatorio([
            {"id": "1", "esperada": "ASSEDIO", "texto": "x", "saida": {"categoria": "CONFLITO_COLEGA"}},
            {"id": "2", "esperada": "HUMILHACAO", "texto": "x", "saida": {"categoria": "NAO_CLASSIFICADO"}},
            {"id": "3", "esperada": "CANSACO", "texto": "x", "saida": {"categoria": "CONFLITO_CHEFIA"}},
            {"id": "4", "esperada": "CANSACO", "texto": "x", "saida": {"categoria": "CANSACO"}},
        ])
        self.assertEqual([x["id"] for x in r["graves"]], ["1"])
        self.assertEqual([x["id"] for x in r["restrita_para_nao_classificado"]], ["2"])
        self.assertEqual(r["acertos"], 1)


if __name__ == "__main__":
    unittest.main()
