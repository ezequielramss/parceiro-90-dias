import { h, icon, avatar, badge, formatTime, isConfidential, labelCategory } from "../ui.js";

const tones = ["purple", "blue", "orange", "green", "lime"];
const priority = { ALTA: 0, MODERADA: 1, BAIXA: 2 };

function renderOverview({ data, serie, relatosNovos = null, onNavigate }) {
  const { kpis, alertas, colaboradores } = data;
  const greeting = greetingText();
  const heading = h("section", { className: "page-heading overview-heading" },
    h("div", {}, h("span", { className: "eyebrow" }, todayLabel()), h("h1", {}, `${greeting}, Fernanda.`), h("p", {}, "Acompanhe a jornada e os sinais que precisam de atenção.")));

  const metrics = h("section", { className: "overview-metrics", "aria-label": "Indicadores da equipe" },
    metric("people", "dark", "Em jornada", kpis.emJornada, "nos primeiros 90 dias"),
    metric("inbox", "yellow", "Alertas abertos", kpis.alertasAbertos, `${kpis.semRetorno24h} sem retorno há mais de 24h`),
    metric("check", "green", "Atendidos", kpis.atendidos, "retorno registrado pela liderança"),
    metric("trend", "blue", "Taxa de resposta nos 10 primeiros dias", `${kpis.taxaResposta10d}%`, "Meta: 50%", kpis.taxaResposta10d));

  const activeAlerts = alertas.filter((item) => item.status !== "ATENDIDO")
    .sort((a, b) => (priority[a.severidade] ?? 3) - (priority[b.severidade] ?? 3) || new Date(a.criadoEm) - new Date(b.criadoEm)).slice(0, 4);
  const urgentPanel = h("article", { className: "panel overview-priority" },
    panelHeading("Alertas prioritários", "Sinais abertos que pedem acompanhamento", "#/alertas", "Ver todos os alertas", onNavigate),
    h("div", { className: "overview-alert-list" }, ...(activeAlerts.length ? activeAlerts.map((item) => alertRow(item, colaboradores)) : [h("p", { className: "empty-state" }, "Nenhum alerta aberto.")])));

  const attentionPeople = colaboradores.filter((person) => person.nivelAtencao !== "Estável").slice(0, 3);
  const attentionPanel = h("article", { className: "panel overview-attention" },
    h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Precisam de atenção"), h("p", {}, "Quantidade de pessoas em atenção por mês")), icon("warning", 20)),
    buildAttentionChart(serie.itens),
    h("div", { className: "attention-people" }, ...attentionPeople.map((person) => h("div", { className: "attention-person" }, avatar(person.iniciais, tones[colaboradores.indexOf(person) % tones.length]), h("div", {}, h("strong", {}, person.nome), h("small", {}, `${person.funcao} · ${person.loja}`)), h("span", {}, person.motivoAtencao)))));

  const lowerCards = h("section", { className: "overview-lower" },
    h("article", { className: "insight-card" }, h("span", { className: "insight-icon" }, icon("sparkles", 19)), h("div", {}, h("span", { className: "eyebrow" }, "INSIGHT DA SEMANA"), h("h2", {}, kpis.insight.titulo), h("p", {}, kpis.insight.resumo))),
    h("article", { className: "confidential-channel" }, h("span", { className: "anonymous-icon" }, icon("lock", 20)), h("div", {}, h("span", {}, "CANAL CONFIDENCIAL"), h("h2", {}, "Relatos recebidos"), h("p", {}, relatosNovos === null ? "Acesso exclusivo ao RH" : `${relatosNovos} novos relatos aguardam triagem`)), relatosNovos === null ? null : h("a", { className: "button button-primary", href: "#/relatos", onClick: (event) => { event.preventDefault(); onNavigate("#/relatos"); } }, "Ver relatos", icon("arrow", 15))));

  return h("div", {}, heading, metrics, h("section", { className: "overview-grid" }, urgentPanel, attentionPanel), lowerCards);
}

function metric(symbol, tone, title, value, description, progress) {
  return h("article", { className: `overview-metric ${tone ? `metric-${tone}` : ""}` },
    h("div", { className: "metric-head" }, h("span", {}, title), h("span", { className: `metric-icon ${tone}` }, icon(symbol, 18))),
    h("strong", { className: "metric-value" }, value),
    progress === undefined ? h("small", {}, description) : h("div", {}, h("progress", { className: "metric-progress", max: 100, value: Math.min(progress, 100), "aria-label": `Meta de resposta: ${progress}%` }), h("small", {}, description)));
}

function panelHeading(title, description, href, linkText, onNavigate) {
  return h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, title), h("p", {}, description)), h("a", { className: "text-link", href, onClick: (event) => { event.preventDefault(); onNavigate(href); } }, linkText, icon("arrow", 15)));
}

function alertRow(item, colaboradores) {
  const person = colaboradores.find((entry) => entry.id === item.colaboradorId);
  const confidential = isConfidential(item);
  return h("div", { className: "overview-alert-row" },
    avatar(confidential ? "" : person?.iniciais || "RH", confidential ? "lime" : tones[Math.max(0, colaboradores.indexOf(person)) % tones.length]),
    h("div", {}, h("strong", {}, confidential ? item.protocolo : person?.nome || item.protocolo), h("small", {}, `${labelCategory(item.categoria)} · ${item.destino === "RH" ? "RH" : "Gerente"}`)),
    badge(item.severidade === "ALTA" ? "Alta" : item.severidade === "MODERADA" ? "Atenção" : "Baixa", `severity severity-${item.severidade.toLowerCase()}`),
    h("small", { className: "overview-age" }, formatTime(item.criadoEm)));
}

function buildAttentionChart(items) {
  const counts = items.map((item) => ({ month: item.mes, count: item.atencao + item.prioridade }));
  const max = Math.max(1, ...counts.map((item) => item.count));
  const barWidth = 27;
  const gap = 27;
  const bars = counts.map((item, index) => {
    const height = Math.round((item.count / max) * 95);
    const x = 18 + index * (barWidth + gap);
    const y = 120 - height;
    return [h("rect", { x, y, width: barWidth, height, rx: 4, class: index === counts.length - 1 ? "chart-bar current" : "chart-bar", "aria-label": `${item.month}: ${item.count} pessoas` }), h("text", { x: x + barWidth / 2, y: 142, "text-anchor": "middle" }, item.month)];
  }).flat();
  return h("div", { className: "attention-chart" }, h("svg", { viewBox: "0 0 342 152", role: "img", "aria-label": "Pessoas em atenção nos últimos seis meses" }, h("line", { x1: 8, y1: 120, x2: 334, y2: 120, class: "chart-axis-line" }), ...bars));
}

function greetingText() {
  const hour = new Date().getHours();
  return hour < 12 ? "Bom dia" : hour < 18 ? "Boa tarde" : "Boa noite";
}
function todayLabel() { return new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "numeric", month: "long" }).format(new Date()).toLocaleUpperCase("pt-BR"); }

export { renderOverview };
