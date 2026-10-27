import { getDashboardData, getPerfil, getRelatos, getSerieMensal, getBemestar } from "./api.js";
import { h, icon } from "./ui.js";
import { renderEmployees } from "./views/funcionarios.js";
import { renderAlerts } from "./views/alertas.js";
import { renderOverview } from "./views/visao-geral.js";
import { renderRelatos } from "./views/relatos.js";
import { renderWellbeing } from "./views/bem-estar.js";
import { renderRelatorios } from "./views/relatorios.js";

const routes = [
  ["#/visao-geral", "Visão geral", "grid"], ["#/funcionarios", "Funcionários", "people"],
  ["#/alertas", "Alertas", "warning"], ["#/relatos", "Relatos", "inbox"],
  ["#/bem-estar", "Bem-estar", "heart"], ["#/relatorios", "Relatórios", "chart"]
];
const state = { data: null, profile: null, query: "", employeeId: null, alertId: null, reportId: null, wellbeingId: null, detailsOpen: true, pageData: {}, loadingPage: "", pageError: "" };
const root = document.querySelector("#app");

function currentRoute() {
  const route = window.location.hash || "#/visao-geral";
  return routes.some(([path]) => path === route) ? route : "#/visao-geral";
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
    nav, h("div", { className: "profile" }, h("span", { className: "avatar avatar-lime", "aria-hidden": "true" }, "FR"), h("div", {}, h("strong", {}, "Fernanda Rocha"), h("small", {}, state.profile === "GERENTE" ? "Gerente" : "Gestora de RH")), h("span", { className: "more", "aria-hidden": "true" }, "•••")));
  const search = h("label", { className: "search" }, icon("search"), h("input", { type: "search", value: state.query, placeholder: "Buscar funcionário ou alerta...", "aria-label": "Buscar funcionário ou alerta" }), h("kbd", {}, "Ctrl K"));
  search.querySelector("input").addEventListener("input", (event) => { state.query = event.currentTarget.value; render(); const field = root.querySelector(".topbar .search input"); field?.focus(); field?.setSelectionRange(state.query.length, state.query.length); });
  const topbar = h("header", { className: "topbar" }, h("div", { className: "mobile-brand" }, h("span", { className: "brand-mark" }, h("img", { src: "img/mcdonalds-logo.svg", alt: "" })), h("strong", {}, "Kallopolli")), search, h("button", { type: "button", className: "icon-button", "aria-label": "Notificações" }, icon("bell"), h("span", {})));
  let content;
  const waitingForExtraData = ["#/visao-geral", "#/bem-estar", "#/relatorios"].includes(path) || (path === "#/relatos" && state.profile === "RH");
  if (state.pageError) content = errorCard(state.pageError);
  else if (!state.data || state.loadingPage === path || (waitingForExtraData && !state.pageData[path])) content = loadingCard();
  else if (path === "#/visao-geral") {
    const extra = state.pageData[path];
    content = renderOverview({ data: state.data, serie: extra.serie, relatosNovos: state.profile === "RH" ? extra.relatos.filter((item) => item.status === "NOVO").length : null, onNavigate: navigate });
  } else if (path === "#/funcionarios") content = renderEmployees({ data: state.data, query: state.query, selectedId: state.employeeId, onSelect: (id) => { state.employeeId = id; state.detailsOpen = true; render(); }, detailsOpen: state.detailsOpen, onClose: closeDetails });
  else if (path === "#/alertas") content = renderAlerts({ data: state.data, query: state.query, selectedId: state.alertId, onSelect: (id) => { state.alertId = id; state.detailsOpen = true; render(); }, detailsOpen: state.detailsOpen, onClose: closeDetails });
  else if (path === "#/relatos") content = renderRelatos({ profile: state.profile, reports: state.profile === "RH" ? state.pageData[path]?.relatos || [] : [], query: state.query, selectedId: state.reportId, detailsOpen: state.detailsOpen, onSelect: (id) => { state.reportId = id; state.detailsOpen = true; render(); }, onClose: closeDetails, onUpdate: render });
  else if (path === "#/bem-estar") content = renderWellbeing({ data: { bemestar: state.pageData[path].bemestar, colaboradores: state.data.colaboradores }, profile: state.profile, query: state.query, selectedId: state.wellbeingId, detailsOpen: state.detailsOpen, onSelect: (id) => { state.wellbeingId = id; state.detailsOpen = true; render(); }, onClose: closeDetails });
  else if (path === "#/relatorios") content = renderRelatorios({ serie: state.pageData[path].serie, colaboradores: state.data.colaboradores, kpis: state.data.kpis, onUpdate: render });
  else content = placeholder(active?.[1] || "Página", path);
  root.replaceChildren(sidebar, h("main", { className: "main" }, topbar, mobileNav, h("div", { className: "content" }, content)));
}

function loadingCard() { return h("section", { className: "placeholder", "aria-live": "polite" }, h("article", { className: "panel" }, h("p", {}, "Carregando dados..."))); }
function errorCard(message) { return h("section", { className: "placeholder" }, h("article", { className: "panel" }, icon("warning", 28), h("h1", {}, "Não foi possível carregar os dados"), h("p", {}, message))); }
function placeholder(title) { return h("section", { className: "placeholder" }, h("article", { className: "panel" }, icon("sparkles", 32), h("h1", {}, `${title} em construção`), h("p", {}, "Esta área será disponibilizada em uma próxima etapa."))); }
function closeDetails() { state.detailsOpen = false; render(); }
function navigate(path) { window.location.hash = path; }

async function loadPageData(path) {
  if (!state.data || !state.profile) return;
  if (state.pageData[path]) return;
  if (state.loadingPage === path) return;
  state.loadingPage = path;
  state.pageError = "";
  render();
  try {
    if (path === "#/visao-geral") {
      const [serie, relatos] = await Promise.all([getSerieMensal(), state.profile === "RH" ? getRelatos() : Promise.resolve([])]);
      state.pageData[path] = { serie, relatos };
    } else if (path === "#/relatos" && state.profile === "RH") state.pageData[path] = { relatos: await getRelatos() };
    else if (path === "#/bem-estar") state.pageData[path] = { bemestar: await getBemestar(state.profile) };
    else if (path === "#/relatorios") state.pageData[path] = { serie: await getSerieMensal() };
    else state.pageData[path] = {};
  } catch (error) { state.pageError = error.message; }
  finally { state.loadingPage = ""; if (currentRoute() === path) render(); }
}

document.addEventListener("dashboard-search", (event) => {
  state.query = event.detail.query;
  render();
  const selector = event.detail.source === "employees" ? ".employee-search input" : event.detail.source === "alerts" ? ".filter-search input" : event.detail.source === "relatos" ? ".report-search input" : event.detail.source === "wellbeing" ? ".wellbeing-search input" : ".topbar .search input";
  const field = root.querySelector(selector);
  field?.focus();
  if (event.detail.focusPosition !== undefined) field?.setSelectionRange(event.detail.focusPosition, event.detail.focusPosition);
});
document.addEventListener("dashboard-refresh-view", render);
window.addEventListener("hashchange", () => { state.query = ""; state.detailsOpen = true; state.pageError = ""; render(); loadPageData(currentRoute()); });
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && state.detailsOpen && ["#/alertas", "#/funcionarios", "#/relatos", "#/bem-estar"].includes(currentRoute())) closeDetails();
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); root.querySelector(".topbar .search input")?.focus(); }
});

render();
getPerfil().then(async (profile) => {
  state.profile = profile;
  state.data = await getDashboardData();
  state.employeeId = state.data.colaboradores[0]?.id;
  state.alertId = state.data.alertas[0]?.id;
  render();
  await loadPageData(currentRoute());
}).catch((error) => { state.pageError = error.message; state.loadingPage = ""; render(); });
