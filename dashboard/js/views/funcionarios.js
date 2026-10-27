import { h, icon, avatar, badge, formatDate } from "../ui.js";

const tones = ["purple", "blue", "orange", "green", "lime"];
const levelClass = (level) => `level-${level.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")}`;
function renderEmployees({ data, query = "", selectedId, onSelect, detailsOpen = true, onClose }) {
  const { colaboradores, alertas } = data;
  const matching = colaboradores.filter((person) => `${person.nome} ${person.funcao} ${person.loja}`.toLocaleLowerCase("pt-BR").includes(query.toLocaleLowerCase("pt-BR")));
  const selected = colaboradores.find((person) => person.id === selectedId) || matching[0] || colaboradores[0];
  const personAlerts = alertas.filter((item) => item.colaboradorId === selected?.id);
  const summary = h("section", { className: "employee-summary", "aria-label": "Resumo dos funcionários" },
    summaryCard("people", "dark", "Em acompanhamento", data.kpis.emJornada, "nos primeiros 90 dias"),
    summaryCard("warning", "red", "Precisam de atenção", data.kpis.precisamAtencao, "com motivo explicável"),
    summaryCard("inbox", "yellow", "Alertas abertos", data.kpis.alertasAbertos, "aguardando acompanhamento"),
    summaryCard("check", "green", "Presença média", `${Math.round(colaboradores.reduce((sum, p) => sum + p.presenca, 0) / colaboradores.length)}%`, "entre os exemplos exibidos"));

  const toolbar = h("div", { className: "employees-toolbar" },
    h("div", {}, h("h2", {}, "Equipe em acompanhamento"), h("p", { className: "results-count" }, `${matching.length} funcionários encontrados`)),
    h("label", { className: "employee-search" }, icon("search", 16), h("input", { type: "search", placeholder: "Buscar funcionário...", "aria-label": "Buscar funcionário", value: query })));
  const filters = h("div", { className: "employee-filters", role: "group", "aria-label": "Filtrar por nível de atenção" });
  const filterButtons = ["Todos", "Atenção", "Estável", "Prioridade"].map((filter) => {
    const button = h("button", { type: "button", className: "selected", "aria-pressed": "true" }, filter);
    button.addEventListener("click", () => {
      for (const row of table.querySelectorAll(".employee-row")) {
        const match = filter === "Todos" || row.dataset.level === filter;
        row.hidden = !match;
      }
      for (const item of filters.querySelectorAll("button")) { item.classList.toggle("selected", item === button); item.setAttribute("aria-pressed", String(item === button)); }
    });
    return button;
  });
  filters.append(...filterButtons, h("span", {}, "Atualizado agora"));
  const table = h("div", { className: "employee-table", role: "table", "aria-label": "Funcionários em acompanhamento" },
    h("div", { className: "employee-table-head", role: "row" }, ...["Funcionário", "Dia da jornada", "Presença", "Nível de atenção"].map((text) => h("span", { role: "columnheader" }, text))));
  for (const person of matching) {
    const row = h("button", { type: "button", className: `employee-row ${person.id === selected?.id ? "selected" : ""}`, role: "row", "aria-label": `Ver detalhes de ${person.nome}`, dataset: { level: person.nivelAtencao } },
      h("span", { className: "employee-person", role: "cell" }, avatar(person.iniciais, tones[colaboradores.indexOf(person) % tones.length]), h("span", {}, h("strong", {}, person.nome), h("small", {}, `${person.funcao} · ${person.loja}`))),
      h("span", { className: "employee-tenure", role: "cell" }, icon("calendar", 14), `Dia ${person.diaJornada} de 90`),
      h("span", { className: "attendance-cell", role: "cell" }, h("strong", {}, `${person.presenca}%`), h("span", { className: "table-progress" }, h("progress", { max: 100, value: person.presenca, "aria-label": `Presença ${person.presenca}%` }))),
      h("span", { role: "cell" }, badge(person.nivelAtencao, `level-badge ${levelClass(person.nivelAtencao)}`)), icon("chevron", 16));
    row.addEventListener("click", () => onSelect(person.id));
    table.append(row);
  }
  if (!matching.length) table.append(h("p", { className: "empty-state" }, "Nenhum funcionário encontrado."));
  toolbar.querySelector("input").addEventListener("input", (event) => {
    const value = event.currentTarget.value;
    const focusPosition = value.length;
    document.dispatchEvent(new CustomEvent("dashboard-search", { detail: { query: value, source: "employees", focusPosition } }));
  });

  const alertSection = h("section", { className: "person-alerts" }, h("h3", {}, `Alertas relacionados (${personAlerts.length})`), ...(personAlerts.length ? personAlerts.map((item) => h("p", {}, `${item.protocolo} · ${item.status === "ABERTO" ? "Aberto" : item.status === "ATENDIDO" ? "Atendido" : "Sem retorno há mais de 24h"}`)) : [h("p", {}, "Nenhum alerta associado.")]));
  const details = selected && detailsOpen ? h("aside", { className: "panel employee-detail", "aria-label": `Detalhes de ${selected.nome}` },
    h("div", { className: "detail-cover" }),
    h("button", { type: "button", className: "close-detail", "aria-label": "Fechar detalhes", onClick: onClose }, icon("close", 17)),
    h("div", { className: "detail-profile" }, avatar(selected.iniciais, tones[colaboradores.indexOf(selected) % tones.length]), badge(selected.nivelAtencao, `level-badge ${levelClass(selected.nivelAtencao)}`), h("h2", {}, selected.nome), h("p", {}, `${selected.funcao} · Unidade ${selected.loja}`)),
    h("div", { className: "journey-block" }, h("div", { className: "journey-copy" }, h("span", {}, "Jornada de 90 dias"), h("strong", {}, `${selected.diaJornada} de 90`)), h("div", { className: "journey-track" }, h("progress", { max: 90, value: selected.diaJornada, "aria-label": `Dia ${selected.diaJornada} de 90` })), h("small", {}, `Próximo check-in: ${new Intl.DateTimeFormat("pt-BR", { day: "numeric", month: "short" }).format(new Date(`${selected.proximoCheckin}T12:00:00`))}`)),
    h("div", { className: "detail-stats" }, detailStat("Presença", `${selected.presenca}%`, `${selected.faltas} faltas no período`), detailStat("Último contato", formatDate(selected.ultimoContato), "via WhatsApp")),
    h("div", { className: `detail-alert ${selected.nivelAtencao === "Estável" ? "confidential" : ""}` }, icon("warning", 18), h("div", {}, h("strong", {}, selected.nivelAtencao === "Estável" ? "Acompanhamento" : "Motivo da atenção"), h("p", {}, selected.motivoAtencao))),
    alertSection) : null;

  const heading = h("section", { className: "page-heading" }, h("div", {}, h("span", { className: "eyebrow" }, "GESTÃO DE PESSOAS"), h("h1", {}, "Funcionários"), h("p", {}, "Acompanhe os primeiros 90 dias e identifique quem precisa de atenção.")));
  const listPanel = h("article", { className: "panel employees-panel" }, toolbar, filters, table);
  const layout = h("section", { className: "employees-layout" }, listPanel, details);
  return h("div", {}, heading, summary, layout);
}

function summaryCard(symbol, tone, label, value, help) {
  return h("article", { className: "summary-card" }, h("div", { className: `summary-icon ${tone}` }, icon(symbol)), h("div", {}, h("span", {}, label), h("strong", {}, value), h("small", {}, help)));
}
function detailStat(label, value, hint) { return h("div", {}, h("span", {}, label), h("strong", {}, value), h("small", {}, hint)); }

export { renderEmployees };
