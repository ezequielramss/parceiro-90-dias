// Dados reais do backend por padrão. ?mock=1 na URL usa os JSON fictícios de mock/, para demonstrar sem backend.
const USE_MOCK = new URLSearchParams(window.location.search).get("mock") === "1";
const MOCK_BASE = "mock";
const MOCK_PERFIL = "RH";
const MOCK_USUARIO = { perfil: MOCK_PERFIL, nome: "Fernanda Rocha", loja: null };
const SESSAO_EXPIRADA = "Sua sessão expirou. Entre novamente para continuar.";

function erroComStatus(message, status) {
  const error = new Error(message);
  error.status = status;
  return error;
}

async function mensagemDoServidor(response, padrao) {
  try {
    const body = await response.json();
    if (typeof body?.erro?.mensagem === "string") return body.erro.mensagem;
  } catch { /* corpo sem JSON */ }
  return padrao;
}

async function request(path, mockFile, options = {}) {
  const headers = { Accept: "application/json", ...options.headers };
  const response = await fetch(USE_MOCK ? `${MOCK_BASE}/${mockFile}` : path, { credentials: "same-origin", ...options, headers });
  if (response.status === 401) throw erroComStatus(SESSAO_EXPIRADA, 401);
  if (!response.ok) throw erroComStatus(await mensagemDoServidor(response, `Não foi possível carregar os dados (${response.status}).`), response.status);
  if (response.status === 204) return null;
  return response.json();
}

async function login(email, senha) {
  const response = await fetch("/api/login", {
    method: "POST", credentials: "same-origin",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ email, senha })
  });
  if (!response.ok) throw erroComStatus(await mensagemDoServidor(response, "Não foi possível entrar agora. Tente novamente."), response.status);
  return response.json();
}
async function logout() {
  if (USE_MOCK) return;
  await fetch("/api/logout", { method: "POST", credentials: "same-origin" });
}

function getKpis() { return request("/api/kpis", "kpis.json"); }
function getColaboradores() { return request("/api/colaboradores", "colaboradores.json"); }
/** { perfil: "RH" | "GERENTE", nome, loja }. Sem sessão, lança erro com status 401. */
async function getPerfil() {
  if (USE_MOCK) return MOCK_USUARIO;
  return request("/api/perfil");
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
async function getBemestar(profile = MOCK_PERFIL) {
  if (USE_MOCK && profile === "GERENTE") return request("/api/bem-estar", "bemestar-gerente.json");
  const data = await request("/api/bem-estar", "bemestar.json");
  if (profile !== "GERENTE") return data;
  return {
    distribuicaoCategorias: data.distribuicaoCategorias,
    pessoas: data.pessoas.map(({ categoria, sugestao }) => ({ categoria, sugestao }))
  };
}
function getSerieMensal() { return request("/api/kpis/serie-mensal", "serie-mensal.json"); }
/** Geração de relatório só existe no modo de demonstração (fora do MVP no backend). */
const RELATORIOS_DISPONIVEIS = USE_MOCK;
async function gerarRelatorio() {
  if (!USE_MOCK) throw new Error("Indisponível nesta versão.");
  await new Promise((resolve) => window.setTimeout(resolve, 800));
  return request("/api/relatorios/gerar", "relatorio-exemplo.json");
}
async function getDashboardData() {
  const [kpis, colaboradores, alertas] = await Promise.all([getKpis(), getColaboradores(), getAlertas()]);
  return { kpis, colaboradores, alertas };
}

export { USE_MOCK, RELATORIOS_DISPONIVEIS, login, logout, getKpis, getColaboradores, getLinhaDoTempo, getPerfil, getAlertas, getRelatos, getBemestar, getSerieMensal, gerarRelatorio, getDashboardData };
