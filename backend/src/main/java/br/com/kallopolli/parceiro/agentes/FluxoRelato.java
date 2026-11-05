package br.com.kallopolli.parceiro.agentes;

import br.com.kallopolli.parceiro.agentes.Agente.Desfecho;
import br.com.kallopolli.parceiro.alerta.ServicoAlertas;
import br.com.kallopolli.parceiro.classificacao.CategoriaRepository;
import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.classificacao.ClassificadorRelato;
import br.com.kallopolli.parceiro.classificacao.DecisaoEncaminhamento;
import br.com.kallopolli.parceiro.classificacao.RoteadorRelato;
import br.com.kallopolli.parceiro.conversa.ConversaEstadoRepository;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.RelatoRepository;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.whatsapp.MensageiroWhatsApp;
import br.com.kallopolli.parceiro.whatsapp.RespostaPorTexto;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * O fluxo do relato (H13, H16, H37), usado pelos agentes Relato e Elogio e pela confirmação:
 * o texto é gravado, classificado e roteado. O comum espera SIM ou NÃO; o urgente (inclui sinais
 * graves) e a falha da IA vão ao RH na hora; o elogio só é agradecido.
 *
 * <p>Nada sai para gerente ou RH antes do SIM, exceto urgente e falha da IA. O estado é gravado
 * antes de cada envio: se o envio falhar, a pessoa não fica presa nem gera um segundo alerta.
 */
@Service
public class FluxoRelato {

    private final ConversaEstadoRepository estados;
    private final RelatoRepository relatos;
    private final ClassificadorRelato classificador;
    private final CategoriaRepository categorias;
    private final RoteadorRelato roteador;
    private final ServicoAlertas alertas;
    private final AuditoriaRepository auditoria;
    private final MensageiroWhatsApp mensageiro;

    public FluxoRelato(ConversaEstadoRepository estados, RelatoRepository relatos, ClassificadorRelato classificador,
                       CategoriaRepository categorias, RoteadorRelato roteador, ServicoAlertas alertas,
                       AuditoriaRepository auditoria, MensageiroWhatsApp mensageiro) {
        this.estados = estados;
        this.relatos = relatos;
        this.classificador = classificador;
        this.categorias = categorias;
        this.roteador = roteador;
        this.alertas = alertas;
        this.auditoria = auditoria;
        this.mensageiro = mensageiro;
    }

    /** Texto novo, que não está em branco: grava como relato, classifica e decide. */
    public Desfecho novo(Colaborador colaborador, String texto, long mensagemId) {
        long relatoId = relatos.criar(colaborador.id(), mensagemId, texto);
        return classificarEDecidir(colaborador, relatoId, texto);
    }

    /**
     * Texto solto de quem não escolheu nada no menu: só o que é grave segue, e vai ao RH na hora, como
     * qualquer urgente. O resto não deixa rastro de relato. Falha da IA aqui não alerta ninguém: em
     * texto solto, um limite de cota da IA avisaria o RH de cada "tudo certo por aí?".
     *
     * @return o resultado para o log, se o texto era grave
     */
    public Optional<String> triarSinaisGraves(Colaborador colaborador, String texto, long mensagemId) {
        Classificacao classificacao = classificador.classificar(texto);
        DecisaoEncaminhamento decisao = decidir(classificacao);
        if (classificacao.falhaIa() || !decisao.urgente()) {
            return Optional.empty();
        }
        long relatoId = relatos.criar(colaborador.id(), mensagemId, texto);
        relatos.gravarClassificacao(relatoId, classificacao);
        confirmar(colaborador, relatoId);
        alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
        mensageiro.enviar(colaborador, TextosConversa.relatoUrgente());
        return Optional.of("URGENTE_TEXTO_SOLTO");
    }

    /** Resposta enquanto o relato aguarda confirmação: SIM envia, NÃO descarta, outro texto reclassifica. */
    public String confirmacao(Colaborador colaborador, String texto, long relatoId) {
        // Ação interna conf:<relatoId>:sim|nao, a mesma dos antigos botões; vale só para o relato que aguarda.
        String acao = RespostaPorTexto.confirmacao(texto)
                .map(r -> RespostaPorTexto.acaoConfirmacao(relatoId, r))
                .orElse("");

        if (acao.endsWith(":sim")) {
            var relato = relatos.buscarRelato(relatoId).orElseThrow();
            Classificacao classificacao = relato.classificacao();
            DecisaoEncaminhamento decisao = decidir(classificacao);
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.relatoEncaminhado());
            return "ENCAMINHADO";
        }
        if (acao.endsWith(":nao")) {
            descartar(colaborador, relatoId);
            return "DESCARTADO";
        }
        if (texto.isBlank()) {
            mensageiro.enviar(colaborador, TextosConversa.conviteRelato());
            return "RELATO_VAZIO";
        }
        // Escreveu de novo: o texto substitui o anterior e a classificação é refeita (H16).
        relatos.atualizarTexto(relatoId, texto);
        return "RECLASSIFICADO_" + classificarEDecidir(colaborador, relatoId, texto).resultado();
    }

    /** NÃO, ou voltar ao menu durante a confirmação: nada é enviado a ninguém. */
    public void descartar(Colaborador colaborador, long relatoId) {
        relatos.definirConfirmacao(relatoId, RelatoRepository.DESCARTADO);
        estados.definir(colaborador.id(), EstadoConversa.LIVRE);
        auditoria.registrar(quem(colaborador), "RELATO_DESCARTADO", "relato/" + relatoId);
        mensageiro.enviar(colaborador, TextosConversa.nadaEnviado());
    }

    /** PARAR: descarta o que aguardava confirmação. */
    public void descartarAoParar(Colaborador colaborador) {
        if (relatos.descartarAguardando(colaborador.id()) > 0) {
            auditoria.registrar(quem(colaborador), "RELATO_DESCARTADO", "colaborador/" + colaborador.id());
        }
    }

    private Desfecho classificarEDecidir(Colaborador colaborador, long relatoId, String texto) {
        Classificacao classificacao = classificador.classificar(texto);
        relatos.gravarClassificacao(relatoId, classificacao);
        DecisaoEncaminhamento decisao = decidir(classificacao);

        if (decisao.semAlerta()) {
            confirmar(colaborador, relatoId);
            mensageiro.enviar(colaborador, TextosConversa.elogioRecebido());
            return Desfecho.estadoDefinido("ELOGIO");
        }
        if (classificacao.falhaIa()) {
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.iaIndisponivel());
            return Desfecho.estadoDefinido("FALHA_IA");
        }
        if (decisao.urgente()) {
            confirmar(colaborador, relatoId);
            alertas.criarEEnviar(colaborador, relatoId, decisao, classificacao);
            mensageiro.enviar(colaborador, TextosConversa.relatoUrgente());
            return Desfecho.estadoDefinido("URGENTE");
        }
        estados.definir(colaborador.id(), EstadoConversa.aguardandoConfirmacao(relatoId));
        mensageiro.enviar(colaborador, TextosConversa.confirmacaoRelato(classificacao.motivo(), decisao.destino()));
        return Desfecho.estadoDefinido("AGUARDANDO_CONFIRMACAO");
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
