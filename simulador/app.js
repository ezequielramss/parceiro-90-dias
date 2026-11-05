// Simulador local: fala com /simulador/api na mesma origem. Nenhuma chamada externa e nada de innerHTML.
const INTERVALO_MS = 1500;
const contato = document.querySelector("#contato");
const telefoneDigitado = document.querySelector("#telefone");
const aviso = document.querySelector("#aviso");
const lista = document.querySelector("#mensagens");
const atalhos = document.querySelector("#atalhos");
const form = document.querySelector("#form");
const campo = document.querySelector("#texto");

const conversas = new Map(); // telefone -> { ultima, itens: [{ de, texto, opcoes }] }
let telefone = "";
let buscando = false;

function el(tag, classe, texto) {
  const node = document.createElement(tag);
  if (classe) node.className = classe;
  if (texto !== undefined) node.textContent = texto;
  return node;
}

function conversa(numero) {
  if (!conversas.has(numero)) conversas.set(numero, { ultima: 0, itens: [] });
  return conversas.get(numero);
}

function desenhar() {
  const atual = telefone ? conversa(telefone) : { itens: [] };
  lista.replaceChildren(...atual.itens.map((item) => {
    const balao = el("li", `balao ${item.de}`);
    balao.append(el("span", "quem-fala", item.de === "robo" ? "Parceiro" : "Você"), el("p", "", item.texto));
    return balao;
  }));
  const ultimaDoRobo = [...atual.itens].reverse().find((item) => item.de === "robo");
  // Sem opções na última mensagem do robô, o atalho é voltar ao menu.
  const opcoes = ultimaDoRobo?.opcoes?.length ? ultimaDoRobo.opcoes : (ultimaDoRobo ? ["menu"] : []);
  atalhos.replaceChildren(...opcoes.map((opcao) => {
    const botao = el("button", "atalho", opcao);
    botao.type = "button";
    botao.addEventListener("click", () => enviar(opcao));
    return botao;
  }));
  lista.lastElementChild?.scrollIntoView({ block: "end" });
}

function avisar(texto) { aviso.textContent = texto; }

async function carregarContatos() {
  try {
    const resposta = await fetch("/simulador/api/contatos", { headers: { Accept: "application/json" } });
    if (!resposta.ok) throw new Error(String(resposta.status));
    const contatos = await resposta.json();
    contato.replaceChildren(el("option", "", "Escolha um telefone cadastrado"), ...contatos.map((c) => {
      const opcao = el("option", "", `${c.nome} · ${c.papeis.join(", ")} · +${c.telefone}`);
      opcao.value = c.telefone;
      return opcao;
    }));
    contato.firstElementChild.value = "";
    if (!contatos.length) avisar("Nenhum telefone cadastrado. Digite um telefone para testar como número desconhecido.");
  } catch {
    avisar("Não foi possível carregar os telefones cadastrados.");
  }
}

function usar(numero) {
  const digitos = numero.replace(/\D/g, "");
  if (!/^\d{8,15}$/.test(digitos)) { avisar("Use DDI + DDD + número, só dígitos (ex.: 5511999990000)."); return; }
  telefone = digitos;
  avisar(`Escrevendo como +${digitos}.`);
  desenhar();
  campo.focus();
}

async function enviar(texto) {
  if (!telefone) { avisar("Escolha quem está escrevendo antes de enviar."); return; }
  if (!texto.trim()) return;
  conversa(telefone).itens.push({ de: "pessoa", texto, opcoes: [] });
  desenhar();
  try {
    const resposta = await fetch("/simulador/api/mensagens", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ telefone, texto })
    });
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      avisar(corpo?.erro?.mensagem || `Não foi possível enviar (${resposta.status}).`);
    }
  } catch {
    avisar("Não foi possível falar com o backend.");
  }
}

async function buscarNovas() {
  if (!telefone || buscando) return;
  buscando = true;
  const numero = telefone;
  const atual = conversa(numero);
  try {
    const resposta = await fetch(`/simulador/api/mensagens?telefone=${encodeURIComponent(numero)}&depois=${atual.ultima}`,
      { headers: { Accept: "application/json" } });
    if (resposta.ok) {
      const corpo = await resposta.json();
      for (const m of corpo.mensagens) atual.itens.push({ de: "robo", texto: m.texto, opcoes: m.opcoes });
      atual.ultima = corpo.ultima;
      if (corpo.mensagens.length && numero === telefone) desenhar();
    }
  } catch { /* tenta de novo no próximo intervalo */ }
  finally { buscando = false; }
}

contato.addEventListener("change", () => { if (contato.value) usar(contato.value); });
document.querySelector("#usar").addEventListener("click", () => usar(telefoneDigitado.value));
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const texto = campo.value;
  campo.value = "";
  enviar(texto);
});

carregarContatos();
window.setInterval(buscarNovas, INTERVALO_MS);
