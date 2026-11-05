package br.com.kallopolli.parceiro.conversa;

import br.com.kallopolli.parceiro.agentes.Agente;
import br.com.kallopolli.parceiro.agentes.FluxoRelato;
import br.com.kallopolli.parceiro.agentes.RegistroAgentes;
import br.com.kallopolli.parceiro.consentimento.TextoNormalizado;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Conversa com o colaborador que tem aceite ativo: só decide para onde a mensagem vai.
 *
 * <ul>
 *   <li>LIVRE: a escolha do menu (número ou nome) abre o agente. Outro texto passa pela triagem de
 *       sinais graves: o grave vai ao RH na hora; o resto recebe o menu ou, se for longo, o aviso de
 *       que pode ser registrado como relato, sem relato nem alerta;
 *   <li>EM_AGENTE: a mensagem vai ao agente ativo; "menu", "voltar" e "0" voltam ao menu. Sem
 *       mensagem por 30 minutos, vale como LIVRE;
 *   <li>AGUARDANDO_HUMOR: 1 a 5 ou "nota 2" grava o humor; outro texto volta a LIVRE e é tratado
 *       como texto solto;
 *   <li>AGUARDANDO_CONFIRMACAO_RELATO: SIM envia, NÃO descarta, outro texto reclassifica; "menu",
 *       "voltar" e "0" descartam e voltam ao menu;
 *   <li>tipo não suportado recebe o texto próprio, sem mudar o estado.
 * </ul>
 * Os números do menu só valem em LIVRE; 1 a 5 é nota só em AGUARDANDO_HUMOR; SIM e NÃO só na
 * confirmação. A lógica do relato fica em {@link FluxoRelato}, e cada agente é uma classe própria.
 */
@Service
public class OrquestradorConversa {

    private static final Pattern NOTA = Pattern.compile("(?:nota )?([1-5])");
    private static final Set<String> VOLTAR_AO_MENU = Set.of("menu", "voltar", "0");
    /** Só o que não tem conteúdo fica fora da triagem. Na dúvida, o texto é classificado. */
    private static final Set<String> SEM_CONTEUDO = Set.of("", "oi", "ola", "ok", "okay", "bom dia", "boa tarde",
            "boa noite", "tudo bem", "obrigado", "obrigada", "agradeco", "valeu", "sim", "nao", "aceito");

    private final ConversaEstadoRepository estados;
    private final CheckinRepository checkins;
    private final RegistroAgentes agentes;
    private final FluxoRelato fluxoRelato;
    private final MensageiroWhatsApp mensageiro;
    private final Clock relogio;
    private final int limiteTextoLongo;
    private final boolean triagemSinaisGraves;

    public OrquestradorConversa(ConversaEstadoRepository estados, CheckinRepository checkins, RegistroAgentes agentes,
                                FluxoRelato fluxoRelato, MensageiroWhatsApp mensageiro, Clock relogio,
                                MenuProperties menu) {
        this.estados = estados;
        this.checkins = checkins;
        this.agentes = agentes;
        this.fluxoRelato = fluxoRelato;
        this.mensageiro = mensageiro;
        this.relogio = relogio;
        this.limiteTextoLongo = menu.textoLongoCaracteres();
        this.triagemSinaisGraves = menu.triagemSinaisGraves();
    }

    /** Mensagem já gravada (mensagemId) de quem tem aceite ativo. Devolve o resultado para o log. */
    public String tratar(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
        if (mensagem.naoSuportado()) {
            mensageiro.enviar(colaborador, TextosConversa.tipoNaoSuportado());
            return "TIPO_NAO_SUPORTADO";
        }
        if (mensagem.conteudo() == null) {
            return "IGNORADA";
        }
        EstadoConversa estado = estados.estadoVigente(colaborador.id(), relogio.instant());
        return switch (estado.tipo()) {
            case AGUARDANDO_CONFIRMACAO_RELATO -> confirmacao(colaborador, mensagem, estado.relatoId());
            case AGUARDANDO_HUMOR -> humor(colaborador, mensagem, estado.checkinId(), mensagemId);
            case EM_AGENTE -> emAgente(colaborador, mensagem, mensagemId, estado.agente());
            case LIVRE -> livre(colaborador, mensagem, mensagemId);
        };
    }

    /** PARAR (prioridade sobre tudo): volta a LIVRE, sem agente, e descarta o que aguardava confirmação. */
    public void aoParar(Colaborador colaborador) {
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        fluxoRelato.descartarAoParar(colaborador);
    }

    private String livre(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
        Optional<Agente> escolhido = agentes.escolher(mensagem.conteudo());
        if (escolhido.isPresent()) {
            Agente agente = escolhido.get();
            estados.definir(colaborador.id(), EstadoConversa.emAgente(agente.codigo()));
            mensageiro.enviar(colaborador, agente.abertura());
            return "AGENTE_ESCOLHIDO_" + agente.codigo();
        }
        if (agentes.numeroInvalido(mensagem.conteudo())) {
            mensageiro.enviar(colaborador, agentes.menuCurto());
            return "OPCAO_INVALIDA";
        }
        return responderTextoSolto(colaborador, mensagem, mensagemId);
    }

    private String emAgente(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId, String codigo) {
        Optional<Agente> agente = agentes.porCodigo(codigo);
        if (agente.isEmpty() || querVoltarAoMenu(mensagem)) {
            // Agente desligado por configuração enquanto a pessoa estava nele também volta ao menu.
            estados.definir(colaborador.id(), EstadoConversa.LIVRE);
            return enviarMenu(colaborador);
        }
        Agente.Desfecho desfecho = agente.get().tratar(colaborador, mensagem, mensagemId);
        switch (desfecho.proximo()) {
            // Regrava para renovar atualizado_em: os 30 minutos contam da última mensagem.
            case CONTINUA -> estados.definir(colaborador.id(), EstadoConversa.emAgente(codigo));
            case LIVRE -> estados.definir(colaborador.id(), EstadoConversa.LIVRE);
            case ESTADO_DEFINIDO -> { }
        }
        return desfecho.resultado();
    }

    private String humor(Colaborador colaborador, MensagemRecebida mensagem, long checkinId, long mensagemId) {
        Matcher nota = NOTA.matcher(TextoNormalizado.normalizar(mensagem.conteudo()));
        if (nota.matches()) {
            int valor = Integer.parseInt(nota.group(1));
            checkins.registrarHumor(checkinId, valor);
            estados.definir(colaborador.id(), EstadoConversa.LIVRE);
            mensageiro.enviar(colaborador, TextosConversa.respostaNota(valor));
            return "HUMOR_REGISTRADO";
        }
        // Sem nota: o check-in fica sem resposta. Nada vira relato sem a pessoa escolher no menu.
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        return responderTextoSolto(colaborador, mensagem, mensagemId);
    }

    private String confirmacao(Colaborador colaborador, MensagemRecebida mensagem, long relatoId) {
        if (querVoltarAoMenu(mensagem)) {
            fluxoRelato.descartar(colaborador, relatoId);
            enviarMenu(colaborador);
            return "DESCARTADO_MENU";
        }
        return fluxoRelato.confirmacao(colaborador, mensagem.conteudo(), relatoId);
    }

    /**
     * Texto que não é escolha nem nota. O grave vai ao RH pela triagem. Do resto, o longo pode ser algo
     * a contar, e a pessoa é avisada; o aviso e o menu não registram nada.
     */
    private String responderTextoSolto(Colaborador colaborador, MensagemRecebida mensagem, long mensagemId) {
        if (triagemSinaisGraves && temConteudo(mensagem)) {
            Optional<String> grave = fluxoRelato.triarSinaisGraves(colaborador, mensagem.conteudo(), mensagemId);
            if (grave.isPresent()) {
                return grave.get();
            }
        }
        Optional<String> aviso = textoLongo(mensagem) ? agentes.avisoTextoLongo() : Optional.empty();
        if (aviso.isPresent()) {
            mensageiro.enviar(colaborador, aviso.get());
            return "TEXTO_LONGO";
        }
        return enviarMenu(colaborador);
    }

    private boolean temConteudo(MensagemRecebida mensagem) {
        String texto = TextoNormalizado.normalizar(mensagem.conteudo());
        return !SEM_CONTEUDO.contains(texto) && !VOLTAR_AO_MENU.contains(texto) && !texto.matches("\\d+")
                && agentes.escolher(texto).isEmpty();
    }

    private boolean textoLongo(MensagemRecebida mensagem) {
        String texto = mensagem.conteudo().strip();
        return texto.codePointCount(0, texto.length()) > limiteTextoLongo
                && !querVoltarAoMenu(mensagem)
                && agentes.escolher(texto).isEmpty();
    }

    private String enviarMenu(Colaborador colaborador) {
        mensageiro.enviar(colaborador, agentes.menu());
        return "MENU";
    }

    private static boolean querVoltarAoMenu(MensagemRecebida mensagem) {
        return VOLTAR_AO_MENU.contains(TextoNormalizado.normalizar(mensagem.conteudo()));
    }
}
