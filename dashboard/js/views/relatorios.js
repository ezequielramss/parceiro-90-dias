import { gerarRelatorio } from "../api.js";
import { h, icon } from "../ui.js";

let escopo = "GERAL";
let publico = "GESTOR";
let colaboradorId = "";
let resultado = null;
let carregando = false;
let mensagem = "";

function renderRelatorios({ serie, colaboradores, kpis, onUpdate }) {
  if (!colaboradorId && colaboradores.length) colaboradorId = colaboradores[0].id;
  const heading = h("section", { className: "page-heading reports-heading" }, h("div", {}, h("span", { className: "eyebrow" }, "ANÁLISE DE PESSOAS"), h("h1", {}, "Relatórios"), h("p", {}, "Acompanhe a evolução da equipe e gere uma análise para revisão humana.")));
  const metrics = h("section", { className: "reports-metrics", "aria-label": "Resumo de indicadores" },
    metric("people", "Em jornada", kpis.emJornada), metric("inbox", "Alertas abertos", kpis.alertasAbertos), metric("check", "Atendidos", kpis.atendidos), metric("trend", "Resposta em 10 dias", `${kpis.taxaResposta10d}%`));
  const charts = h("section", { className: "reports-charts" },
    h("article", { className: "panel report-chart-panel" }, h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Status mensal da equipe"), h("p", {}, "Contagem de pessoas por nível de acompanhamento")), icon("chart", 19)), legend(), statusChart(serie.itens)),
    h("article", { className: "panel report-chart-panel" }, h("header", { className: "panel-header" }, h("div", {}, h("h2", {}, "Presença e atenção por mês"), h("p", {}, "Evolução dos indicadores agregados")), icon("trend", 19)), presenceLegend(), comparisonChart(serie.itens)));
  const config = reportConfig(colaboradores, onUpdate);
  const preview = reportPreview(colaboradores, onUpdate);
  return h("div", {}, heading, metrics, charts, h("section", { className: "panel ai-report-section" },
    h("header", { className: "ai-report-intro" }, h("span", { className: "ai-mark" }, icon("sparkles", 22)), h("div", {}, h("span", { className: "eyebrow" }, "ASSISTENTE DE RELATÓRIOS"), h("h2", {}, "Gerar análise"), h("p", {}, "Escolha o escopo e o público para preparar um resumo revisável."))),
    h("div", { className: "ai-report-body" }, config, preview)));
}

function metric(symbol, title, value) { return h("article", { className: "report-metric" }, h("span", { className: "report-metric-icon" }, icon(symbol, 17)), h("div", {}, h("small", {}, title), h("strong", {}, value))); }
function legend() { return h("div", { className: "report-legend" }, legendItem("stable", "Estável"), legendItem("attention", "Atenção"), legendItem("priority", "Prioridade")); }
function presenceLegend() { return h("div", { className: "report-legend" }, legendItem("presence", "Presença média"), legendItem("attention-line", "Pessoas em atenção")); }
function legendItem(tone, label) { return h("span", {}, h("i", { className: `legend-dot ${tone}` }), label); }

function statusChart(items) {
  const barWidth = 25, gap = 24, top = 12, fullHeight = 120, baseY = 140;
  const max = Math.max(1, ...items.map((item) => item.estavel + item.atencao + item.prioridade));
  const shapes = items.map((item, index) => {
    const x = 18 + index * (barWidth + gap);
    const stable = Math.round(item.estavel / max * fullHeight), attention = Math.round(item.atencao / max * fullHeight), priority = Math.round(item.prioridade / max * fullHeight);
    const totalHeight = stable + attention + priority;
    const bottom = baseY;
    return [
      h("rect", { x, y: bottom - stable, width: barWidth, height: stable, class: "stack-stable" }),
      h("rect", { x, y: bottom - stable - attention, width: barWidth, height: attention, class: "stack-attention" }),
      h("rect", { x, y: bottom - totalHeight, width: barWidth, height: priority, class: "stack-priority" }),
      h("text", { x: x + barWidth / 2, y: 158, "text-anchor": "middle" }, item.mes)
    ];
  }).flat();
  return h("div", { className: "report-svg-wrap" }, h("svg", { viewBox: "0 0 330 166", role: "img", "aria-label": "Barras empilhadas com status mensal de pessoas" }, h("line", { x1: 8, y1: 140, x2: 322, y2: 140, class: "report-axis" }), ...shapes));
}

function comparisonChart(items) {
  const width = 330, left = 16, right = 315, top = 20, bottom = 140;
  const presence = items.map((item) => item.presenca);
  const attention = items.map((item) => item.atencao + item.prioridade);
  const presenceMin = Math.min(...presence) - 2, presenceMax = Math.max(...presence) + 2;
  const attentionMin = Math.min(...attention) - 2, attentionMax = Math.max(...attention) + 2;
  const point = (value, min, max, index) => `${left + index * ((right - left) / Math.max(1, items.length - 1))},${bottom - ((value - min) / Math.max(1, max - min)) * (bottom - top)}`;
  const presencePoints = presence.map((value, index) => point(value, presenceMin, presenceMax, index)).join(" ");
  const attentionPoints = attention.map((value, index) => point(value, attentionMin, attentionMax, index)).join(" ");
  const labels = items.map((item, index) => h("text", { x: left + index * ((right - left) / Math.max(1, items.length - 1)), y: 160, "text-anchor": "middle" }, item.mes));
  const dots = presence.map((value, index) => h("circle", { cx: left + index * ((right - left) / Math.max(1, items.length - 1)), cy: bottom - ((value - presenceMin) / Math.max(1, presenceMax - presenceMin)) * (bottom - top), r: 3, class: "presence-dot" }));
  return h("div", { className: "report-svg-wrap" }, h("svg", { viewBox: `0 0 ${width} 170`, role: "img", "aria-label": "Linhas de presença média e pessoas em atenção por mês" }, h("line", { x1: 8, y1: 140, x2: 322, y2: 140, class: "report-axis" }), h("polyline", { points: presencePoints, class: "presence-line" }), h("polyline", { points: attentionPoints, class: "attention-line-chart" }), ...dots, ...labels), h("small", { className: "chart-note" }, "As séries usam escalas visuais próprias para facilitar a comparação de tendência."));
}

function reportConfig(colaboradores, onUpdate) {
  const scopeSelect = h("select", { "aria-label": "Escopo da análise" }, h("option", { value: "GERAL" }, "Geral"), h("option", { value: "FUNCIONARIO" }, "Funcionário"));
  scopeSelect.value = escopo;
  scopeSelect.addEventListener("change", () => { escopo = scopeSelect.value; resultado = null; mensagem = ""; onUpdate(); });
  const audienceSelect = h("select", { "aria-label": "Público do relatório" }, h("option", { value: "GESTOR" }, "Gestor"), h("option", { value: "RH" }, "RH"), h("option", { value: "DIRECAO" }, "Direção"));
  audienceSelect.value = publico;
  audienceSelect.addEventListener("change", () => { publico = audienceSelect.value; resultado = null; mensagem = ""; onUpdate(); });
  const fields = [h("label", {}, h("span", {}, "Escopo"), scopeSelect), h("label", {}, h("span", {}, "Público"), audienceSelect)];
  if (escopo === "FUNCIONARIO") {
    const employeeSelect = h("select", { "aria-label": "Colaborador" }, ...colaboradores.map((person) => h("option", { value: person.id }, person.nome)));
    employeeSelect.value = colaboradorId;
    employeeSelect.addEventListener("change", () => { colaboradorId = employeeSelect.value; resultado = null; mensagem = ""; onUpdate(); });
    fields.push(h("label", {}, h("span", {}, "Colaborador"), employeeSelect));
  }
  const generate = h("button", { type: "button", className: "button button-primary generate-report", disabled: carregando }, carregando ? icon("clock", 16) : icon("sparkles", 16), carregando ? "Gerando análise..." : "Gerar análise");
  generate.addEventListener("click", async () => {
    carregando = true; mensagem = ""; resultado = null; onUpdate();
    try { resultado = await gerarRelatorio({ escopo, publico, colaboradorId: escopo === "FUNCIONARIO" ? colaboradorId : null }); }
    catch (error) { mensagem = error.message; }
    finally { carregando = false; onUpdate(); }
  });
  return h("div", { className: "ai-config" }, ...fields, generate, mensagem ? h("p", { className: "report-error", role: "alert" }, mensagem) : null);
}

function reportPreview(colaboradores, onUpdate) {
  if (!resultado) return h("div", { className: "ai-preview", "aria-live": "polite" }, h("div", { className: "empty-preview" }, icon("sparkles", 28), h("strong", {}, carregando ? "Preparando análise" : "A análise aparecerá aqui"), h("p", {}, carregando ? "Os dados estão sendo organizados." : "Configure o escopo e o público e gere uma análise para revisar.")));
  const person = colaboradores.find((item) => item.id === colaboradorId);
  const hideName = publico === "DIRECAO";
  const title = escopo === "GERAL" ? "Panorama da equipe" : hideName ? "Análise individual" : `Análise de ${person?.nome || "colaborador"}`;
  const copiedText = ["Gerado por IA — revise antes de compartilhar.", title, "", "Resumo executivo", resultado.resumoExecutivo, "", "Pontos de atenção", ...resultado.pontosAtencao.map((item) => `• ${item}`), "", "Sugestões", ...resultado.sugestoes.map((item) => `• ${item}`)].join("\n");
  const copyButton = h("button", { type: "button", className: "button button-secondary copy-report" }, icon("file", 15), "Copiar texto");
  copyButton.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(copiedText); mensagem = "Texto copiado."; }
    catch { mensagem = "Não foi possível copiar. Verifique a permissão da área de transferência."; }
    onUpdate();
  });
  return h("article", { className: "ai-preview generated-report", "aria-live": "polite" }, h("header", { className: "preview-head" }, h("div", {}, h("span", {}, "GERADO POR IA"), h("h2", {}, title), h("small", {}, `Público: ${publico === "DIRECAO" ? "Direção" : publico === "RH" ? "RH" : "Gestor"}`)), copyButton),
    h("p", { className: "ai-disclaimer" }, "Gerado por IA — revise antes de compartilhar."),
    h("section", { className: "preview-content" }, h("h3", {}, "Resumo executivo"), h("p", {}, resultado.resumoExecutivo), h("h3", {}, "Pontos de atenção"), h("ul", {}, ...resultado.pontosAtencao.map((item) => h("li", {}, item))), h("h3", {}, "Sugestões"), h("ul", {}, ...resultado.sugestoes.map((item) => h("li", {}, item)))),
    mensagem ? h("p", { className: "copy-feedback", role: "status" }, mensagem) : null);
}

export { renderRelatorios };
