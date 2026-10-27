import { h, icon, avatar, badge, formatDate } from "../ui.js";

const suggestions = {
  "Estável": "Manter o acompanhamento regular.",
  "Cansaço": "Vale uma conversa acolhedora ainda hoje.",
  "Sobrecarga": "Vale conversar sobre a distribuição das tarefas.",
  "Desmotivação": "Vale uma conversa aberta sobre a experiência na função.",
  "Dúvidas": "Oferecer orientação sobre os procedimentos da função."
};
let categoryFilter = "Todos";

function renderWellbeing({ data, profile, query = "", selectedId, detailsOpen = true, onSelect, onClose }) {
  const { bemestar, colaboradores } = data;
  const heading = h("section", { className: "page-heading wellbeing-heading" }, h("div", {}, h("span", { className: "eyebrow" }, "ACOMPANHAMENTO DA EQUIPE"), h("h1", {}, "Bem-estar"), h("p", {}, "Categorias e acompanhamentos com resumos descritivos.")));
  const distribution = h("article", { className: "panel wellbeing-distribution" }, h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Visão da equipe"), h("p", {}, "Distribuição de situações por categoria")), icon("chart", 19)), categoryChart(bemestar.distribuicaoCategorias));
  if (profile !== "RH") {
    const groups = [...new Map(bemestar.pessoas.map((person) => [person.categoria, person.sugestao])).entries()];
    const managerActions = h("article", { className: "panel wellbeing-manager-actions" }, h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Sugestões de acompanhamento"), h("p", {}, "Categorias e orientações gerais para a liderança."))), ...groups.map(([category, suggestion]) => actionCard(category, suggestion)));
    return h("div", {}, heading, h("section", { className: "wellbeing-overview-grid" }, distribution, managerActions), privacyFooter());
  }

  const visible = bemestar.pessoas.map((entry) => ({ ...entry, person: colaboradores.find((item) => item.id === entry.colaboradorId) })).filter((entry) => {
    const person = entry.person;
    return person && (categoryFilter === "Todos" || entry.categoria === categoryFilter) && `${person.nome} ${person.funcao} ${person.loja} ${entry.categoria}`.toLocaleLowerCase("pt-BR").includes(query.toLocaleLowerCase("pt-BR"));
  });
  const selected = visible.find((entry) => entry.colaboradorId === selectedId) || visible[0];
  const filterBar = h("div", { className: "wellbeing-filters", role: "group", "aria-label": "Filtrar por categoria" });
  for (const category of ["Todos", ...bemestar.distribuicaoCategorias.map((item) => item.categoria)]) {
    const button = h("button", { type: "button", className: categoryFilter === category ? "selected" : "", "aria-pressed": String(categoryFilter === category) }, category);
    button.addEventListener("click", () => { categoryFilter = category; document.dispatchEvent(new Event("dashboard-refresh-view")); });
    filterBar.append(button);
  }
  const search = h("label", { className: "employee-search wellbeing-search" }, icon("search", 16), h("input", { type: "search", value: query, placeholder: "Buscar pessoa ou categoria", "aria-label": "Buscar acompanhamento por pessoa ou categoria" }));
  search.querySelector("input").addEventListener("input", (event) => document.dispatchEvent(new CustomEvent("dashboard-search", { detail: { query: event.currentTarget.value, source: "wellbeing" } })));
  const listPanel = h("article", { className: "panel wellbeing-list-panel" }, h("div", { className: "wellbeing-list-head" }, h("div", {}, h("h2", {}, "Acompanhamento individual"), h("p", {}, `${visible.length} pessoas`)), search), filterBar, h("div", { className: "wellbeing-people", role: "list", "aria-label": "Pessoas em acompanhamento" }, ...(visible.length ? visible.map((entry) => personRow(entry, selected, onSelect)) : [h("p", { className: "empty-state" }, "Nenhum acompanhamento encontrado.")])));
  const detail = selected && detailsOpen ? personDetail(selected, onClose) : null;
  return h("div", {}, heading, h("section", { className: "wellbeing-overview-grid" }, distribution, actionsPanel(visible)), h("section", { className: "wellbeing-workspace" }, listPanel, detail), privacyFooter());
}

function categoryChart(categories) {
  const max = Math.max(1, ...categories.map((item) => item.quantidade));
  const shapes = categories.map((item, index) => {
    const y = 13 + index * 32;
    const width = Math.round((item.quantidade / max) * 175);
    return [h("text", { x: 2, y: y + 14 }, item.categoria), h("rect", { x: 116, y, width, height: 20, rx: 5, class: `wellbeing-bar bar-${index}` }), h("text", { x: 302, y: y + 14, "text-anchor": "end" }, String(item.quantidade))];
  }).flat();
  return h("div", { className: "category-chart" }, h("svg", { viewBox: "0 0 310 180", role: "img", "aria-label": "Distribuição de situações por categoria" }, ...shapes));
}

function personRow(entry, selected, onSelect) {
  const person = entry.person;
  const button = h("button", { type: "button", className: `wellbeing-person ${selected?.colaboradorId === entry.colaboradorId ? "selected" : ""}`, role: "listitem", "aria-label": `Ver acompanhamento de ${person.nome}` },
    avatar(person.iniciais, "blue"), h("span", { className: "wellbeing-person-copy" }, h("strong", {}, person.nome), h("small", {}, `${person.funcao} · ${person.loja}`)),
    h("span", { className: "wellbeing-person-meta" }, h("small", {}, `Último contato ${formatDate(entry.ultimoContato)}`), badge(entry.categoria, "wellbeing-category")),
    h("span", { className: "wellbeing-trend" }, entry.tendencia), icon("chevron", 16));
  button.addEventListener("click", () => onSelect(entry.colaboradorId));
  return button;
}

function personDetail(entry, onClose) {
  const person = entry.person;
  const history = [...entry.historico].sort((a, b) => new Date(b.data) - new Date(a.data));
  return h("article", { className: "panel wellbeing-detail", "aria-label": `Histórico de acompanhamento de ${person.nome}` },
    h("button", { type: "button", className: "close-detail", "aria-label": "Fechar histórico", onClick: onClose }, icon("close", 17)),
    h("header", { className: "wellbeing-detail-head" }, avatar(person.iniciais, "blue"), h("div", {}, h("span", { className: "eyebrow" }, "ACOMPANHAMENTO"), h("h2", {}, person.nome), h("p", {}, `${person.funcao} · ${person.loja}`))),
    h("div", { className: "wellbeing-current" }, h("div", {}, h("span", {}, "Situação"), badge(entry.categoria, "wellbeing-category")), h("div", {}, h("span", {}, "Tendência"), h("strong", {}, entry.tendencia))),
    h("section", { className: "wellbeing-history" }, h("h3", {}, "Histórico de acompanhamento"), ...history.map((event) => h("article", { className: "wellbeing-history-event" }, h("span", { className: "history-event-icon" }, icon(event.tipo === "WHATSAPP" ? "chat" : "heart", 15)), h("div", {}, h("span", { className: "history-event-meta" }, h("strong", {}, event.categoria), h("small", {}, formatDate(event.data))), h("p", {}, event.resumoNeutro), badge(event.tag, "report-category"))))),
    h("aside", { className: "wellbeing-recommendation" }, icon("sparkles", 17), h("div", {}, h("strong", {}, "Ação recomendada"), h("p", {}, entry.sugestao))));
}

function actionsPanel(visible) {
  const categories = [...new Map(visible.map((entry) => [entry.categoria, entry.sugestao])).entries()].slice(0, 4);
  return h("aside", { className: "panel wellbeing-actions" }, h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Ações recomendadas"), h("p", {}, "Sugestões para conversas de acompanhamento."))), ...(categories.length ? categories.map(([category, suggestion]) => actionCard(category, suggestion)) : [h("p", { className: "empty-state" }, "Nenhuma sugestão para este filtro.")]));
}
function actionCard(category, suggestion) { return h("div", { className: "wellbeing-action-card" }, h("span", { className: "action-icon" }, icon("heart", 16)), h("div", {}, h("strong", {}, category), h("p", {}, suggestion))); }
function privacyFooter() { return h("p", { className: "wellbeing-privacy" }, icon("lock", 14), "O colaborador sabe que o RH vê um resumo das conversas e é avisado quando um alerta é gerado."); }

export { renderWellbeing };
