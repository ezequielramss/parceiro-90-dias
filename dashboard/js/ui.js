function h(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "className") node.className = value;
    else if (key === "dataset") Object.assign(node.dataset, value);
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === "value" || key === "checked" || key === "disabled") node[key] = value;
    else node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function icon(name, size = 19) {
  return h("svg", { className: "icon", width: size, height: size, "aria-hidden": "true" },
    h("use", { href: `icons.svg#${name}` }));
}

function avatar(initials, tone = "lime") {
  return h("span", { className: `avatar avatar-${tone}`, "aria-hidden": "true" }, initials);
}

function badge(text, className) { return h("span", { className }, text); }

function formatTime(value) {
  const elapsed = Math.max(0, Date.now() - new Date(value).getTime());
  const minutes = Math.floor(elapsed / 60000);
  if (minutes < 60) return `há ${Math.max(1, minutes)} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `há ${hours}h`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "há 1 dia" : `há ${days} dias`;
}

function formatDate(value) {
  if (!value) return "Não registrado";
  return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatDuration(alerta) {
  if (!alerta.respondidoEm) return "Aguardando retorno";
  const mins = Math.max(0, Math.round((new Date(alerta.respondidoEm) - new Date(alerta.criadoEm)) / 60000));
  return mins < 60 ? `${mins} min` : `${Math.floor(mins / 60)}h ${mins % 60}min`;
}

function isConfidential(alerta) {
  return alerta.destino === "RH" && ["ASSEDIO", "CONFLITO_CHEFIA"].includes(alerta.categoria);
}

function labelCategory(value) {
  const labels = { CANSACO: "Cansaço", SOBRECARGA: "Sobrecarga", DESMOTIVACAO: "Desmotivação", VONTADE_DE_SAIR: "Vontade de sair", DUVIDA: "Dúvida", CONFLITO_COLEGA: "Conflito com colega", CONFLITO_CHEFIA: "Conflito com chefia", ASSEDIO: "Assédio", SITUACAO_GRAVE: "Situação grave" };
  return labels[value] || value;
}

export { h, icon, avatar, badge, formatTime, formatDate, formatDuration, isConfidential, labelCategory };
