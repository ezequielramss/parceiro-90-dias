import { getDashboardData } from "./api.js";
import { h, icon } from "./ui.js";
import { renderEmployees } from "./views/funcionarios.js";
import { renderAlerts } from "./views/alertas.js";

const routes = [
  ["#/visao-geral", "Visão geral", "grid"], ["#/funcionarios", "Funcionários", "people"],
  ["#/alertas", "Alertas", "warning"], ["#/relatos", "Relatos", "inbox"],
  ["#/bem-estar", "Bem-estar", "heart"], ["#/relatorios", "Relatórios", "chart"]
];
const state = { data: null, query: "", employeeId: null, alertId: null, detailsOpen: true };
const root = document.querySelector("#app");

function currentRoute() {
  const route = window.location.hash || "#/funcionarios";
  return routes.some(([path]) => path === route) ? route : "#/funcionarios";
}

function navLink([path, label, symbol], active, count) {
  return h("a", { className: `nav-item ${active === path ? "active" : ""}`, href: path, "aria-current": active === path ? "page" : null }, icon(symbol), h("span", {}, label), label === "Alertas" && count ? h("em", { "aria-label": `${count} alertas sem retorno` }, count) : null);
}

function render() {
  const path = currentRoute();
  const active = routes.find(([route]) => route === path);
  const nav = h("nav", { className: "nav-list", "aria-label": "Navegação principal" }, h("span", { className: "nav-label" }, "MENU"), ...routes.map((route) => navLink(route, path, state.data?.kpis.semRetorno24h)));
  const mobileNav = h("nav", { className: "mobile-nav", "aria-label": "Navegação principal" }, ...routes.map((route) => navLink(route, path, state.data?.kpis.semRetorno24h)));
  const sidebar = h("aside", { className: "sidebar" },
    h("div", { className: "brand" }, h("span", { className: "brand-mark" }, h("img", { src: "img/mcdonalds-logo.svg", alt: "" })), h("div", {}, h("strong", {}, "Kallopolli"), h("small", {}, "Pessoas & Cultura"))),
    nav, h("div", { className: "profile" }, h("span", { className: "avatar avatar-lime", "aria-hidden": "true" }, "FR"), h("div", {}, h("strong", {}, "Fernanda Rocha"), h("small", {}, "Gestora de RH")), h("span", { className: "more", "aria-hidden": "true" }, "•••")));
  const search = h("label", { className: "search" }, icon("search"), h("input", { type: "search", value: state.query, placeholder: "Buscar funcionário ou alerta...", "aria-label": "Buscar funcionário ou alerta" }), h("kbd", {}, "Ctrl K"));
  search.querySelector("input").addEventListener("input", (event) => { state.query = event.currentTarget.value; render(); const field = root.querySelector(".topbar .search input"); field?.focus(); field?.setSelectionRange(state.query.length, state.query.length); });
  const topbar = h("header", { className: "topbar" }, h("div", { className: "mobile-brand" }, h("span", { className: "brand-mark" }, h("img", { src: "img/mcdonalds-logo.svg", alt: "" })), h("strong", {}, "Kallopolli")), search, h("button", { type: "button", className: "icon-button", "aria-label": "Notificações" }, icon("bell"), h("span", {})));
  let content;
  if (!state.data) content = h("section", { className: "placeholder" }, h("article", { className: "panel" }, h("p", {}, "Carregando dados...")));
  else if (path === "#/funcionarios") content = renderEmployees({ data: state.data, query: state.query, selectedId: state.employeeId, onSelect: (id) => { state.employeeId = id; state.detailsOpen = true; render(); }, detailsOpen: state.detailsOpen, onClose: () => { state.detailsOpen = false; render(); } });
  else if (path === "#/alertas") content = renderAlerts({ data: state.data, query: state.query, selectedId: state.alertId, onSelect: (id) => { state.alertId = id; state.detailsOpen = true; render(); }, detailsOpen: state.detailsOpen, onClose: () => { state.detailsOpen = false; render(); } });
  else content = placeholder(active?.[1] || "Página", path);
  root.replaceChildren(sidebar, h("main", { className: "main" }, topbar, mobileNav, h("div", { className: "content" }, content)));
}

function placeholder(title, path) {
  const message = path === "#/relatos" ? "A área de relatos confidenciais estará disponível em uma próxima fase." : path === "#/bem-estar" ? "Indicadores agregados e resumos neutros estarão disponíveis em uma próxima fase." : path === "#/relatorios" ? "Relatórios estarão disponíveis em uma próxima fase." : "A visão geral estará disponível em uma próxima fase.";
  return h("section", { className: "placeholder" }, h("article", { className: "panel" }, icon("sparkles", 32), h("h1", {}, `${title} em construção`), h("p", {}, message)));
}

document.addEventListener("dashboard-search", (event) => {
  state.query = event.detail.query;
  render();
});
window.addEventListener("hashchange", () => { state.query = ""; state.detailsOpen = true; render(); });
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && state.detailsOpen && (currentRoute() === "#/alertas" || currentRoute() === "#/funcionarios")) {
    state.detailsOpen = false;
    render();
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); root.querySelector(".topbar .search input")?.focus(); }
});

render();
getDashboardData().then((data) => { state.data = data; state.employeeId = data.colaboradores[0]?.id; state.alertId = data.alertas[0]?.id; render(); }).catch((error) => {
  root.replaceChildren(h("main", { className: "error-page" }, h("h1", {}, "Não foi possível carregar o painel"), h("p", {}, error.message)));
});
