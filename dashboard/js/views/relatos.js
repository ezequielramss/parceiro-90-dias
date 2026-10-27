import { h, icon, badge, formatTime, formatDate } from "../ui.js";

const statusText = { NOVO: "Novo", EM_ANALISE: "Em análise", CONCLUIDO: "Concluído" };
const severityText = { ALTA: "Alta", MODERADA: "Atenção", BAIXA: "Baixa" };
let selectedProtocol = null;
let statusFilter = "Todos";
const localChanges = new Map();

function renderRelatos({ profile, reports = [], query = "", selectedId, detailsOpen = true, onSelect, onClose, onUpdate }) {
  if (profile !== "RH") return restrictedPage();
  const selected = reports.find((report) => report.protocolo === (selectedId || selectedProtocol)) || reports[0];
  if (!selectedProtocol && selected) selectedProtocol = selected.protocolo;
  const term = query.toLocaleLowerCase("pt-BR");
  const visible = reports.filter((report) => {
    const status = getLocalStatus(report);
    return (statusFilter === "Todos" || statusText[status] === statusFilter) && `${report.protocolo} ${report.titulo} ${categoryLabel(report.categoria)}`.toLocaleLowerCase("pt-BR").includes(term);
  });
  const heading = h("section", { className: "page-heading" }, h("div", {}, h("span", { className: "eyebrow" }, "CANAL CONFIDENCIAL · ACESSO EXCLUSIVO DO RH"), h("h1", {}, "Relatos confidenciais"), h("p", {}, "Receba e acompanhe manifestações com confidencialidade.")), h("div", { className: "report-security" }, icon("lock", 16), h("span", {}, "Acesso restrito ao RH")));
  const list = h("article", { className: "panel reports-queue" },
    h("div", { className: "report-queue-head" }, h("div", {}, h("h2", {}, "Relatos recebidos"), h("p", {}, `${visible.length} registros exibidos`)), h("label", { className: "employee-search report-search" }, icon("search", 16), h("input", { type: "search", value: query, placeholder: "Buscar protocolo ou título", "aria-label": "Buscar relatos por protocolo, título ou categoria" }))),
    buildFilters(), h("div", { className: "report-queue-list", role: "list", "aria-label": "Lista de relatos" }, ...visible.map((report) => reportRow(report, selected, onSelect))));
  list.querySelector("input").addEventListener("input", (event) => document.dispatchEvent(new CustomEvent("dashboard-search", { detail: { query: event.currentTarget.value, source: "relatos" } })));
  const detail = selected && detailsOpen ? reportDetail(selected, onUpdate, onClose) : null;
  return h("div", {}, heading, h("section", { className: "reports-workspace" }, list, detail));
}

function buildFilters() {
  const filters = h("div", { className: "report-filters", role: "group", "aria-label": "Filtrar relatos por status" });
  for (const label of ["Todos", "Novo", "Em análise", "Concluído"]) {
    const button = h("button", { type: "button", className: statusFilter === label ? "selected" : "", "aria-pressed": String(statusFilter === label) }, label);
    button.addEventListener("click", () => {
      statusFilter = label;
      for (const item of filters.querySelectorAll("button")) { const active = item === button; item.classList.toggle("selected", active); item.setAttribute("aria-pressed", String(active)); }
      document.dispatchEvent(new Event("dashboard-refresh-view"));
    });
    filters.append(button);
  }
  return filters;
}

function reportRow(report, selected, onSelect) {
  const status = getLocalStatus(report);
  const row = h("button", { type: "button", className: `report-queue-row ${selected?.protocolo === report.protocolo ? "selected" : ""}`, role: "listitem", "aria-label": `Abrir relato ${report.protocolo}: ${report.titulo}` },
    h("span", { className: `queue-priority severity-${report.severidade.toLowerCase()}` }),
    h("span", { className: "queue-report-copy" }, h("span", { className: "queue-meta" }, h("b", {}, report.protocolo), h("small", {}, formatTime(report.criadoEm))), h("strong", {}, report.titulo), h("span", { className: "queue-tags" }, badge(severityText[report.severidade] || report.severidade, `severity severity-${report.severidade.toLowerCase()}`), badge(categoryLabel(report.categoria), "report-category"), badge(statusText[status], `status-badge status-${status.toLowerCase()}`))));
  row.addEventListener("click", () => { selectedProtocol = report.protocolo; onSelect(report.protocolo); });
  return row;
}

function reportDetail(report, onUpdate, onClose) {
  const local = localChanges.get(report.protocolo) || { status: report.status, observation: "", extraHistory: [] };
  const history = [...report.historico, ...local.extraHistory].sort((a, b) => new Date(a.data) - new Date(b.data));
  const statusSelect = h("select", { "aria-label": "Status local do caso" }, ...Object.entries(statusText).map(([value, text]) => h("option", { value }, text)));
  statusSelect.value = local.status;
  const observation = h("textarea", { rows: 3, placeholder: "Registre uma observação interna do RH", "aria-label": "Observação interna do RH", value: local.observation });
  const detail = h("article", { className: "panel report-reader", "aria-label": `Detalhe do relato ${report.protocolo}` },
    h("button", { type: "button", className: "close-detail", "aria-label": "Fechar detalhe do relato", onClick: onClose }, icon("close", 17)),
    h("header", { className: "reader-head" }, h("div", {}, h("span", { className: "eyebrow" }, `PROTOCOLO ${report.protocolo}`), h("h2", {}, report.titulo), h("p", {}, `Recebido ${formatDate(report.criadoEm)}`)), badge(severityText[report.severidade] || report.severidade, `severity severity-${report.severidade.toLowerCase()}`)),
    h("div", { className: "reader-context" }, contextCell("Categoria", categoryLabel(report.categoria)), contextCell("Loja", report.loja), contextCell("Turno", report.turno)),
    h("div", { className: "confidential-caution" }, icon("lock", 15), h("p", {}, "Ao agir, considere que loja e turno podem permitir identificar quem relatou.")),
    h("div", { className: "report-content" }, h("span", {}, "CONTEÚDO DO RELATO"), h("p", {}, report.conteudo)),
    h("section", { className: "case-timeline" }, h("h3", {}, "Histórico do caso"), ...(history.length ? history.map((event) => timelineEntry(event)) : [h("p", {}, "Ainda não há atualizações." )])),
    h("section", { className: "case-management" }, h("div", { className: "management-head" }, h("h2", {}, "Gestão do caso"), h("p", {}, "Alterações nesta tela são locais e não são persistidas.")), h("label", { className: "management-field" }, h("span", {}, "Status"), statusSelect), h("label", { className: "management-field" }, h("span", {}, "Observação interna do RH"), observation), h("button", { type: "button", className: "button button-secondary save-local" }, "Salvar atualização local")));
  detail.querySelector(".save-local").addEventListener("click", () => {
    const previousStatus = local.status;
    local.status = statusSelect.value;
    local.observation = observation.value.trim();
    if (local.status !== previousStatus || local.observation) local.extraHistory.push({ titulo: local.status !== previousStatus ? `Status atualizado para ${statusText[local.status]}` : "Observação interna registrada", data: new Date().toISOString(), descricao: local.observation || "Atualização local do caso." });
    localChanges.set(report.protocolo, local);
    onUpdate();
  });
  return detail;
}

function contextCell(label, value) { return h("div", {}, h("span", {}, label), h("strong", {}, value)); }
function timelineEntry(event) { return h("div", { className: "timeline-item done" }, h("span", { className: "timeline-dot", "aria-hidden": "true" }), h("div", {}, h("strong", {}, event.titulo), h("small", {}, `${formatDate(event.data)} · ${event.descricao}`))); }
function getLocalStatus(report) { return localChanges.get(report.protocolo)?.status || report.status; }
function categoryLabel(category) { return ({ ASSEDIO: "Assédio", HUMILHACAO: "Humilhação", CONFLITO_CHEFIA: "Conflito com chefia", CONDUTA_INADEQUADA: "Conduta inadequada", CONDICOES_TRABALHO: "Condições de trabalho" })[category] || category; }
function restrictedPage() { return h("section", { className: "placeholder" }, h("article", { className: "panel access-restricted" }, icon("lock", 28), h("h1", {}, "Acesso restrito"), h("p", {}, "Relatos confidenciais estão disponíveis somente para o perfil RH."))); }

export { renderRelatos };
