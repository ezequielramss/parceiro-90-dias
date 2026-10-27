const USE_MOCK = true;
const MOCK_BASE = "mock";

async function request(path, mockFile) {
  const response = await fetch(USE_MOCK ? `${MOCK_BASE}/${mockFile}` : path, { headers: { Accept: "application/json" } });
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
async function getDashboardData() {
  const [kpis, colaboradores, alertas] = await Promise.all([getKpis(), getColaboradores(), getAlertas()]);
  return { kpis, colaboradores, alertas };
}

export { USE_MOCK, getKpis, getColaboradores, getLinhaDoTempo, getAlertas, getDashboardData };
