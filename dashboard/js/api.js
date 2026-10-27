const USE_MOCK = true;
const MOCK_BASE = "mock";
const MOCK_PERFIL = "RH";

async function request(path, mockFile, options = {}) {
  const headers = { Accept: "application/json", ...options.headers };
  const response = await fetch(USE_MOCK ? `${MOCK_BASE}/${mockFile}` : path, { ...options, headers });
  if (response.status === 401) {
    const error = new Error("Sua sessão expirou. Entre novamente para continuar.");
    error.status = 401;
    throw error;
  }
  if (!response.ok) throw new Error(`Não foi possível carregar os dados (${response.status}).`);
  return response.json();
}

function getKpis() { return request("/api/kpis", "kpis.json"); }
function getColaboradores() { return request("/api/colaboradores", "colaboradores.json"); }
async function getPerfil() {
  if (USE_MOCK) return MOCK_PERFIL;
  const result = await request("/api/perfil");
  return typeof result === "string" ? result : result.perfil;
}
async function getLinhaDoTempo(id) {
  if (!USE_MOCK) return request(`/api/colaboradores/${encodeURIComponent(id)}/linha-do-tempo`);
  const colaboradores = await getColaboradores();
  const person = colaboradores.find((item) => item.id === id);
  return { colaboradorId: id, eventos: person ? [{ tipo: "CONTATO", titulo: "Último contato", criadoEm: person.ultimoContato, resumo: "Contato de acompanhamento pelo WhatsApp." }] : [] };
}
function getAlertas(params = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.destino) query.set("destino", params.destino);
  return request(`/api/alertas${query.size ? `?${query}` : ""}`, "alertas.json");
}
function getRelatos(params = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  return request(`/api/relatos${query.size ? `?${query}` : ""}`, "relatos.json");
}
function getBemestar() { return request("/api/bem-estar", "bemestar.json"); }
function getSerieMensal() { return request("/api/kpis/serie-mensal", "serie-mensal.json"); }
async function gerarRelatorio({ escopo, publico, colaboradorId }) {
  if (!USE_MOCK) return request("/api/relatorios/gerar", undefined, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ escopo, publico, colaboradorId })
  });
  await new Promise((resolve) => window.setTimeout(resolve, 800));
  return request("/api/relatorios/gerar", "relatorio-exemplo.json");
}
async function getDashboardData() {
  const [kpis, colaboradores, alertas] = await Promise.all([getKpis(), getColaboradores(), getAlertas()]);
  return { kpis, colaboradores, alertas };
}

export { USE_MOCK, getKpis, getColaboradores, getLinhaDoTempo, getPerfil, getAlertas, getRelatos, getBemestar, getSerieMensal, gerarRelatorio, getDashboardData };
