import { h, icon, avatar, badge, formatTime, formatDate, formatDuration, isConfidential, labelCategory } from "../ui.js";

const statusLabels = { ABERTO: "Aberto", ATENDIDO: "Atendido", SEM_RETORNO_24H: "Sem retorno 24h" };
const severityLabels = { BAIXA: "Baixa", MODERADA: "Atenção", ALTA: "Alta" };
const returnLabels = { CONVERSEI: "Conversei", NAO_CONSEGUI: "Não consegui" };
const tones = ["purple", "blue", "orange", "green", "lime"];

function renderAlerts({ data, query = "", selectedId, onSelect, detailsOpen = true, onClose }) {
  const { alertas, colaboradores, kpis } = data;
  const selected = alertas.find((item) => item.id === selectedId) || alertas[0];
  let statusFilter = "TODOS";
  let destinationFilter = "";
  const list = h("div", { className: "alert-list", role: "list", "aria-label": "Alertas" });
  const filters = h("div", { className: "filter-row" },
    ...[["TODOS", "Todos"], ["ABERTO", "Abertos"], ["ATENDIDO", "Atendidos"], ["SEM_RETORNO_24H", "Sem retorno"]].map(([value, label]) => {
      const button = h("button", { type: "button", className: value === "TODOS" ? "selected" : "", "aria-pressed": String(value === "TODOS") }, label);
      button.addEventListener("click", () => { statusFilter = value; updateList(); for (const b of filters.querySelectorAll("button")) { const active = b === button; b.classList.toggle("selected", active); b.setAttribute("aria-pressed", String(active)); } });
      return button;
    }),
    h("label", { className: "sr-only", for: "destination-filter" }, "Filtrar por destino"),
    h("select", { id: "destination-filter", "aria-label": "Filtrar por destino" }, h("option", { value: "" }, "Todos os destinos"), h("option", { value: "GERENTE" }, "Gerente"), h("option", { value: "RH" }, "RH")));
  filters.querySelector("select").addEventListener("change", (event) => { destinationFilter = event.currentTarget.value; updateList(); });
  const search = h("label", { className: "filter-search" }, icon("search", 15), h("input", { type: "search", placeholder: "Buscar alerta...", "aria-label": "Buscar alertas", value: query }));
  search.querySelector("input").addEventListener("input", (event) => document.dispatchEvent(new CustomEvent("dashboard-search", { detail: { query: event.currentTarget.value, source: "alerts" } })));

  function updateList() {
    const term = query.toLocaleLowerCase("pt-BR");
    const visible = alertas.filter((item) => {
      const person = colaboradores.find((person) => person.id === item.colaboradorId);
      const safeName = isConfidential(item) ? item.protocolo : `${item.protocolo} ${person?.nome || ""} ${person?.loja || ""}`;
      return (statusFilter === "TODOS" || item.status === statusFilter) && (!destinationFilter || item.destino === destinationFilter) && `${safeName} ${item.categoria} ${item.destino}`.toLocaleLowerCase("pt-BR").includes(term);
    });
    list.replaceChildren();
    if (!visible.length) { list.append(h("p", { className: "empty-state" }, "Nenhum alerta encontrado.")); return; }
    for (const item of visible) {
      const person = colaboradores.find((person) => person.id === item.colaboradorId);
      const confidential = isConfidential(item);
      const name = confidential ? item.protocolo : (person?.nome || item.protocolo);
      const tone = tones[Math.max(0, colaboradores.findIndex((p) => p.id === person?.id)) % tones.length];
      const row = h("button", { type: "button", className: `alert-row ${item.id === selected?.id ? "selected" : ""}`, role: "listitem", "aria-label": `Abrir alerta ${item.protocolo}${confidential ? " confidencial" : ` de ${name}`}`, dataset: { id: item.id } },
        avatar(confidential ? "" : (person?.iniciais || "RH"), confidential ? "lime" : tone),
        h("span", { className: "alert-main" }, h("strong", {}, confidential ? item.protocolo : name), h("small", {}, confidential ? "Acesso restrito ao RH" : `${person?.loja || "Unidade"} · ${person?.funcao || "Colaborador"}`), h("span", { className: "alert-tags" }, badge(labelCategory(item.categoria), "severity"), badge(severityLabels[item.severidade] || item.severidade, `severity severity-${item.severidade.toLowerCase()}`), badge(item.destino === "RH" ? "RH" : "Gerente", "severity severity-moderada"), badge(statusLabels[item.status], `status-badge status-${item.status.toLowerCase()}`), badge(returnLabels[item.retorno] || "Aguardando", "return-badge"))),
        h("span", { className: "alert-age" }, icon("clock", 13), formatTime(item.criadoEm), h("small", {}, `Até retorno: ${formatDuration(item)}`)));
      row.addEventListener("click", () => onSelect(item.id));
      list.append(row);
    }
  }
  updateList();
  const heading = h("section", { className: "page-heading alert-heading" }, h("div", {}, h("span", { className: "eyebrow" }, "ACOMPANHAMENTO"), h("h1", {}, "Alertas"), h("p", {}, "Sinais enviados pelo assistente e retornos da liderança.")));
  const kpiCards = h("section", { className: "alert-kpis", "aria-label": "Resumo dos alertas" },
    metric("inbox", "yellow", "Abertos", kpis.alertasAbertos), metric("check", "green", "Atendidos", kpis.atendidos), metric("clock", "red", "Sem retorno há mais de 24h", kpis.semRetorno24h), metric("trend", "", "Tempo médio até o retorno", `${kpis.tempoMedioRetornoHoras}`, "h"));
  const listPanel = h("article", { className: "panel alert-list-panel" }, h("div", { className: "alert-toolbar" }, h("div", {}, h("h2", {}, "Acompanhamento de alertas"), h("p", { className: "alert-count" }, `${alertas.length} alertas no período`)), search), filters, list);
  const detail = selected && detailsOpen ? renderDetail(selected, colaboradores, onClose) : null;
  return h("div", {}, heading, kpiCards, h("section", { className: "alert-workspace" }, listPanel, detail));
}

function metric(symbol, tone, label, value, suffix = "") {
  return h("article", { className: "metric-card" }, h("div", { className: "metric-head" }, h("span", {}, label), h("span", { className: `metric-icon ${tone}` }, icon(symbol, 17))), h("div", { className: "metric-value" }, value, suffix ? h("span", {}, suffix) : null));
}
function renderDetail(item, colaboradores, onClose) {
  const confidential = isConfidential(item);
  const person = colaboradores.find((entry) => entry.id === item.colaboradorId);
  const returned = item.status === "ATENDIDO";
  const reminder = Boolean(item.lembreteEnviadoEm);
  const timeline = [
    ["Sinal identificado", formatDate(item.criadoEm), true],
    ["Colaborador avisado", "Mensagem enviada pelo WhatsApp", item.colaboradorAvisado],
    [`Alerta enviado a ${item.destinatarioNome}`, formatDate(item.criadoEm), true],
    ["Lembrete 24h", reminder ? formatDate(item.lembreteEnviadoEm) : "Ainda não enviado", reminder],
    [item.retorno === "CONVERSEI" ? "Retorno: Conversei" : item.retorno === "NAO_CONSEGUI" ? "Retorno: Não consegui" : "Aguardando retorno", item.respondidoEm ? `${formatDate(item.respondidoEm)} · ${formatDuration(item)}` : "Sem retorno registrado", returned]
  ];
  return h("aside", { className: "panel alert-detail", "aria-label": `Detalhes do alerta ${item.protocolo}` },
    h("button", { type: "button", className: "close-detail", "aria-label": "Fechar detalhes", onClick: onClose }, icon("close", 17)),
    h("span", { className: "eyebrow" }, confidential ? "ALERTA CONFIDENCIAL" : "DETALHE DO ALERTA"),
    h("h2", {}, item.protocolo), h("p", { className: "detail-subtitle" }, `${labelCategory(item.categoria)} · ${severityLabels[item.severidade] || item.severidade} · ${item.destino === "RH" ? "RH" : "Gerente"}`),
    confidential ? h("div", { className: "detail-alert confidential" }, icon("lock", 17), h("div", {}, h("strong", {}, "Informação confidencial"), h("p", {}, "A identidade da pessoa não é exibida neste painel."))) : h("p", { className: "detail-subtitle" }, `${person?.nome || "Colaborador"} · ${person?.loja || "Unidade"}`),
    h("div", { className: "recommendation" }, icon("sparkles", 18), h("span", {}, item.sugestao)),
    h("section", { className: "timeline" }, h("h3", {}, "Linha do tempo"), ...timeline.map(([title, subtitle, done]) => h("div", { className: `timeline-item ${done ? "done" : ""}` }, h("span", { className: "timeline-dot", "aria-hidden": "true" }), h("div", {}, h("strong", {}, title), h("small", {}, subtitle))))),
    h("p", { className: "readonly-note" }, "O retorno é registrado pelo WhatsApp. Este painel é somente leitura."));
}

export { renderAlerts };
