package br.com.kallopolli.parceiro.conversa;

import br.com.kallopolli.parceiro.alerta.ServicoAlertas;
import br.com.kallopolli.parceiro.classificacao.CategoriaRepository;
import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.classificacao.ClassificadorRelato;
import br.com.kallopolli.parceiro.classificacao.DecisaoEncaminhamento;
import br.com.kallopolli.parceiro.classificacao.RoteadorRelato;
import br.com.kallopolli.parceiro.consentimento.TextoNormalizado;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.Botao;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Conversa com o colaborador que tem aceite ativo (H04, H13, H16, H37).
 *
 * <ul>
 *   <li>AGUARDANDO_HUMOR: "2" ou "nota 2" grava o humor e responde; outro texto vira relato;
 *   <li>LIVRE: texto vira relato, classificado e roteado; o comum espera confirmação, o urgente
 *       (inclui sinais graves) e a falha da IA vão ao RH na hora, o elogio só é agradecido;
 *   <li>AGUARDANDO_CONFIRMACAO_RELATO: "sim" envia, "não" descarta, outro texto reclassifica;
 *   <li>tipo não suportado recebe o texto próprio, sem mudar o estado.
 * </ul>
 * Nada sai para gerente ou RH antes do "sim", exceto urgente e falha da IA.
 */
@Service
public class OrquestradorConversa {

    private static final Pattern NOTA = Pattern.compile("(?:nota )?([1-5])");
    private static final Pattern BOTAO_CONFIRMACAO = Pattern.compile("conf:(\\d+):(sim|nao)");

    private final ConversaEstadoRepository estados;
    private final CheckinRepository checkins;
    private final RelatoRepository relatos;
    private final ClassificadorRelato classificador;
    private final CategoriaRepository categorias;
    private final RoteadorRelato roteador;
    private final ServicoAlertas alertas;
    private final AuditoriaRepository auditoria;
    private final MensageiroWhatsApp mensageiro;

    public OrquestradorConversa(ConversaEstadoRepository estados, CheckinRepository checkins, RelatoRepository relatos,
                                ClassificadorRelato classificador, CategoriaRepository categorias,
                                RoteadorRelato roteador, ServicoAlertas alertas, AuditoriaRepository auditoria,
                                MensageiroWhatsApp mensageiro) {
        this.estados = estados;
        this.checkins = checkins;
        this.relatos = relatos;
        this.classificador = classificador;
        this.categorias = categorias;
        this.roteador = roteador;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.mensageiro = mensageiro;
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
        EstadoConversa estado = estados.estadoDe(colaborador.id());
        return switch (estado.tipo()) {
            case AGUARDANDO_CONFIRMACAO_RELATO -> confirmacao(colaborador, mensagem, estado.relatoId());
            case AGUARDANDO_HUMOR -> humor(colaborador, mensagem, estado.checkinId(), mensagemId);
            case LIVRE -> mensagem.tocouBotao()
                    ? "BOTAO_SEM_EFEITO"
                    : novoRelato(colaborador, mensagem.conteudo(), mensagemId);
        };
    }

    /** PARAR (prioridade sobre tudo): volta a LIVRE e descarta o que aguardava confirmação. */
    public void aoParar(Colaborador colaborador) {
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        if (relatos.descartarAguardando(colaborador.id()) > 0) {
            auditoria.registrar(quem(colaborador), "RELATO_DESCARTADO", "colaborador/" + colaborador.id());
        }
    }

    private String humor(Colaborador colaborador, MensagemRecebida mensagem, long checkinId, long mensagemId) {
        Matcher nota = NOTA.matcher(TextoNormalizado.normalizar(mensagem.conteudo()));
        if (!mensagem.tocouBotao() && nota.matches()) {
            int valor = Integer.parseInt(nota.group(1));
            checkins.registrarHumor(checkinId, valor);
            estados.definir(colaborador.id(), EstadoConversa.LIVRE);
            mensageiro.enviar(colaborador, TextosConversa.respostaNota(valor));
            return "HUMOR_REGISTRADO";
        }
        // Sem nota: a pessoa escolheu falar. O check-in fica sem resposta.
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        return mensagem.tocouBotao() ? "BOTAO_SEM_EFEITO" : novoRelato(colaborador, mensagem.conteudo(), mensagemId);
    }

    private String novoRelato(Colaborador colaborador, String texto, long mensagemId) {
        if (texto.isBlank()) {
            mensageiro.enviar(colaborador, TextosConversa.conviteRelato());
            return "RELATO_VAZIO";
        }
        long relatoId = relatos.criar(colaborador.id(), mensagemId, texto);
        return classificarEDecidir(colaborador, relatoId, texto);
    }

    private String confirmacao(Colaborador colaborador, MensagemRecebida mensagem, long relatoId) {
        String resposta;
        if (mensagem.tocouBotao()) {
            Matcher botao = BOTAO_CONFIRMACAO.matcher(mensagem.botaoId());
            if (!botao.matches() || Long.parseLong(botao.group(1)) != relatoId) {
                return "BOTAO_SEM_EFEITO";
            }
            resposta = botao.group(2);
        } else {
            resposta = TextoNormalizado.normalizar(mensagem.conteudo());
        }

        if ("sim".equals(resposta)) {
            var relato = relatos.buscarRelato(relatoId).orElseThrow();
            Classificacao classificacao = relato.classificacao();
            DecisaoEncaminhamento decisao = decidir(classificacao);
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.relatoEncaminhado());
            return "ENCAMINHADO";
        }
        if ("nao".equals(resposta)) {
            relatos.definirConfirmacao(relatoId, RelatoRepository.DESCARTADO);
            estados.definir(colaborador.id(), EstadoConversa.LIVRE);
            auditoria.registrar(quem(colaborador), "RELATO_DESCARTADO", "relato/" + relatoId);
            mensageiro.enviar(colaborador, TextosConversa.nadaEnviado());
            return "DESCARTADO";
        }
        String texto = mensagem.conteudo();
        if (texto.isBlank()) {
            mensageiro.enviar(colaborador, TextosConversa.conviteRelato());
            return "RELATO_VAZIO";
        }
        // Escreveu de novo: o texto substitui o anterior e a classificação é refeita (H16).
        relatos.atualizarTexto(relatoId, texto);
        return "RECLASSIFICADO_" + classificarEDecidir(colaborador, relatoId, texto);
    }

    private String classificarEDecidir(Colaborador colaborador, long relatoId, String texto) {
        Classificacao classificacao = classificador.classificar(texto);
        relatos.gravarClassificacao(relatoId, classificacao);
        DecisaoEncaminhamento decisao = decidir(classificacao);

        if (decisao.semAlerta()) {
            confirmar(colaborador, relatoId);
            mensageiro.enviar(colaborador, TextosConversa.elogioRecebido());
            return "ELOGIO";
        }
        if (classificacao.falhaIa()) {
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.iaIndisponivel());
            return "FALHA_IA";
        }
        if (decisao.urgente()) {
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.relatoUrgente());
            return "URGENTE";
        }
        estados.definir(colaborador.id(), EstadoConversa.aguardandoConfirmacao(relatoId));
        mensageiro.enviarBotoes(colaborador,
                TextosConversa.confirmacaoRelato(classificacao.motivo(), decisao.destino()),
                List.of(new Botao("conf:" + relatoId + ":sim", TextosConversa.BOTAO_ENVIAR),
                        new Botao("conf:" + relatoId + ":nao", TextosConversa.BOTAO_NAO_ENVIAR)));
        return "AGUARDANDO_CONFIRMACAO";
    }

    private DecisaoEncaminhamento decidir(Classificacao classificacao) {
        return roteador.decidir(classificacao, categorias.buscar(classificacao.categoria()).orElse(null));
    }

    private void confirmar(Colaborador colaborador, long relatoId) {
        relatos.definirConfirmacao(relatoId, RelatoRepository.CONFIRMADO);
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        auditoria.registrar(quem(colaborador), "RELATO_CONFIRMADO", "relato/" + relatoId);
    }

    private static String quem(Colaborador colaborador) {
        return "colaborador/" + colaborador.id();
    }
}
