package br.com.kallopolli.parceiro.painel;

import br.com.kallopolli.parceiro.painel.PainelRepository.AlertaPainel;
import br.com.kallopolli.parceiro.painel.PainelRepository.CheckinPainel;
import br.com.kallopolli.parceiro.painel.PainelRepository.ColaboradorPainel;
import br.com.kallopolli.parceiro.painel.PainelRepository.RelatoPainel;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Monta as respostas do painel no formato de docs/contrato-api.md a partir das linhas que o
 * repositório já filtrou por papel e loja. Nada aqui amplia o que o repositório devolveu.
 *
 * <p>Níveis de atenção são explicáveis e sempre vêm com o motivo; não há pontuação de risco.
 * Regra: notas 1 ou 2 nos check-ins dos últimos 7 dias — duas ou mais = "Prioridade", uma =
 * "Atenção", nenhuma = "Estável".
 */
@Service
public class ServicoPainel {

    static final String ESTAVEL = "Estável";
    static final String ATENCAO = "Atenção";
    static final String PRIORIDADE = "Prioridade";
    static final int DIAS_JORNADA = 90;
    static final int DIAS_CHECKIN = 10;

    private static final String[] MESES = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out",
            "Nov", "Dez"};
    private static final Map<String, String> ROTULOS = Map.ofEntries(
            Map.entry("CANSACO", "Cansaço"), Map.entry("SOBRECARGA", "Sobrecarga"),
            Map.entry("DESMOTIVACAO", "Desmotivação"), Map.entry("DUVIDA", "Dúvidas"),
            Map.entry("CONFLITO_COLEGA", "Conflito com colega"), Map.entry("VONTADE_DE_SAIR", "Vontade de sair"),
            Map.entry("CONFLITO_CHEFIA", "Conflito com chefia"), Map.entry("ASSEDIO", "Assédio"),
            Map.entry("HUMILHACAO", "Humilhação"), Map.entry("SITUACAO_GRAVE", "Situação grave"),
            Map.entry("ELOGIO", "Elogio"), Map.entry("NAO_CLASSIFICADO", "Não classificado"));
    private static final String[] HUMOR = {"", "muito mal", "mal", "mais ou menos", "bem", "muito bem"};
    private static final String SUGESTAO_PADRAO = "Manter o acompanhamento regular.";
    static final String TEXTO_APAGADO = "Texto apagado após 90 dias, como prevê o termo de participação.";

    // Respostas (campos como em docs/contrato-api.md e dashboard/mock/)

    public record Perfil(String perfil, String nome, String loja) {}

    public record Insight(String titulo, String resumo) {}

    public record Kpis(int emJornada, int precisamAtencao, int alertasAbertos, int atendidos, int semRetorno24h,
                       int taxaResposta10d, double tempoMedioRetornoHoras, Insight insight) {}

    public record ItemSerie(String mes, int estavel, int atencao, int prioridade, Integer presenca) {}

    public record Serie(List<ItemSerie> itens) {}

    /** presenca e faltas ficam nulos: o sistema não recebe dados de ponto. */
    public record ColaboradorJson(String id, String nome, String iniciais, String funcao, String loja, String turno,
                                  int diaJornada, Integer presenca, Integer faltas, OffsetDateTime ultimoContato,
                                  String nivelAtencao, String motivoAtencao, LocalDate proximoCheckin) {}

    public record Evento(String tipo, String titulo, OffsetDateTime criadoEm, String resumo) {}

    public record LinhaDoTempo(String colaboradorId, List<Evento> eventos) {}

    public record AlertaJson(String id, String colaboradorId, String protocolo, String categoria, String severidade,
                             String destino, String destinatarioNome, OffsetDateTime criadoEm, String status,
                             String retorno, OffsetDateTime respondidoEm, OffsetDateTime lembreteEnviadoEm,
                             String sugestao, boolean colaboradorAvisado,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String motivo) {}

    public record HistoricoRelato(String titulo, OffsetDateTime data, String descricao) {}

    public record RelatoJson(String protocolo, String titulo, String categoria, String severidade, String status,
                             OffsetDateTime criadoEm, String loja, String turno, String conteudo,
                             List<HistoricoRelato> historico) {}

    public record CategoriaQuantidade(String categoria, long quantidade) {}

    public record HistoricoBemEstar(String tipo, String categoria, String resumoNeutro, OffsetDateTime data,
                                    String tag) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PessoaBemEstar(String colaboradorId, String categoria, OffsetDateTime ultimoContato,
                                 String tendencia, String sugestao, List<HistoricoBemEstar> historico) {}

    public record BemEstar(List<CategoriaQuantidade> distribuicaoCategorias, List<PessoaBemEstar> pessoas) {}

    private final PainelRepository repositorio;
    private final Clock relogio;
    private final ZoneId fuso;

    public ServicoPainel(PainelRepository repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.fuso = relogio.getZone();
    }

    public static Perfil perfil(Acesso acesso) {
        return new Perfil(acesso.papel(), acesso.nome(), acesso.loja());
    }

    public Kpis kpis(Acesso acesso) {
        Instant agora = relogio.instant();
        List<ColaboradorPainel> emJornada = emJornada(acesso);
        Map<UUID, List<CheckinPainel>> porColaborador = porColaborador(repositorio.checkins(acesso, null));
        int precisam = (int) emJornada.stream()
                .filter(c -> !ESTAVEL.equals(nivel(porColaborador.getOrDefault(c.id(), List.of()), agora)[0]))
                .count();

        List<AlertaPainel> alertas = repositorio.alertas(acesso, agora, null, null, null);
        int abertos = contar(alertas, "ABERTO");
        int atendidos = contar(alertas, "ATENDIDO");
        int semRetorno = contar(alertas, "SEM_RETORNO_24H");
        double tempoMedio = alertas.stream()
                .filter(a -> a.respondidoEm() != null)
                .mapToDouble(a -> Duration.between(a.criadoEm(), a.respondidoEm()).toMinutes() / 60.0)
                .average().orElse(0);

        List<CheckinPainel> primeiros = porColaborador.values().stream().flatMap(List::stream)
                .filter(k -> k.dia() <= DIAS_CHECKIN).toList();
        long respondidos = primeiros.stream().filter(k -> k.humor() != null).count();
        int taxa = primeiros.isEmpty() ? 0 : (int) Math.round(100.0 * respondidos / primeiros.size());
        Insight insight = primeiros.isEmpty()
                ? new Insight("Check-ins dos primeiros 10 dias", "Ainda não há check-ins enviados nos primeiros 10 dias.")
                : new Insight("Check-ins dos primeiros 10 dias", "%d de %d check-ins enviados nos primeiros 10 dias tiveram resposta."
                        .formatted(respondidos, primeiros.size()));

        return new Kpis(emJornada.size(), precisam, abertos, atendidos, semRetorno, taxa,
                Math.round(tempoMedio * 10) / 10.0, insight);
    }

    /** Últimos 6 meses: pessoas por nível de atenção, pelas notas dadas em cada mês. */
    public Serie serieMensal(Acesso acesso) {
        YearMonth atual = YearMonth.now(relogio);
        Map<YearMonth, Map<UUID, Integer>> notasBaixas = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            notasBaixas.put(atual.minusMonths(i), new LinkedHashMap<>());
        }
        for (CheckinPainel k : repositorio.checkins(acesso, null)) {
            if (k.humor() == null || k.respondidoEm() == null) {
                continue;
            }
            Map<UUID, Integer> mes = notasBaixas.get(YearMonth.from(k.respondidoEm().atZone(fuso)));
            if (mes != null) {
                mes.merge(k.colaboradorId(), k.humor() <= 2 ? 1 : 0, Integer::sum);
            }
        }
        List<ItemSerie> itens = new ArrayList<>();
        notasBaixas.forEach((mes, pessoas) -> itens.add(new ItemSerie(MESES[mes.getMonthValue() - 1],
                (int) pessoas.values().stream().filter(n -> n == 0).count(),
                (int) pessoas.values().stream().filter(n -> n == 1).count(),
                (int) pessoas.values().stream().filter(n -> n >= 2).count(),
                null)));
        return new Serie(itens);
    }

    public List<ColaboradorJson> colaboradores(Acesso acesso) {
        Instant agora = relogio.instant();
        LocalDate hoje = LocalDate.now(relogio);
        Map<UUID, List<CheckinPainel>> porColaborador = porColaborador(repositorio.checkins(acesso, null));
        return emJornada(acesso).stream().map(c -> {
            int dia = diaJornada(c, hoje);
            String[] nivel = nivel(porColaborador.getOrDefault(c.id(), List.of()), agora);
            return new ColaboradorJson(c.id().toString(), c.nome(), iniciais(c.nome()), funcao(c), c.loja(),
                    c.turno(), dia, null, null, data(c.ultimoContato()), nivel[0], nivel[1],
                    dia < DIAS_CHECKIN ? hoje.plusDays(1) : null);
        }).toList();
    }

    /** Vazio = 404, tanto para id inexistente quanto para colaborador de outra loja. */
    public Optional<LinhaDoTempo> linhaDoTempo(Acesso acesso, UUID id) {
        Optional<ColaboradorPainel> colaborador = repositorio.colaborador(acesso, id);
        if (colaborador.isEmpty()) {
            return Optional.empty();
        }
        List<Evento> eventos = new ArrayList<>();
        for (CheckinPainel k : repositorio.checkins(acesso, id)) {
            eventos.add(k.humor() == null
                    ? new Evento("CHECKIN", "Check-in do dia " + k.dia(), data(k.enviadoEm()), "Sem resposta.")
                    : new Evento("CHECKIN", "Check-in do dia " + k.dia(), data(k.respondidoEm()),
                            "Nota " + k.humor() + " (" + HUMOR[k.humor()] + ")."));
        }
        // Alertas e retornos só para o RH: para o gerente, ligariam o alerta a uma pessoa (H17).
        if (acesso.rh()) {
            for (AlertaPainel a : repositorio.alertas(acesso, relogio.instant(), null, null, id)) {
                eventos.add(new Evento("ALERTA", "Alerta: " + rotulo(a.categoria()), data(a.criadoEm()),
                        a.motivo() != null ? a.motivo() : "Sem resumo disponível."));
                if (a.retorno() != null) {
                    eventos.add(new Evento("RETORNO",
                            "CONVERSEI".equals(a.retorno()) ? "Retorno: Conversei" : "Retorno: Não consegui",
                            data(a.respondidoEm()), "Registrado pelo WhatsApp."));
                }
            }
        }
        eventos.sort(Comparator.comparing(Evento::criadoEm, Comparator.nullsLast(Comparator.reverseOrder())));
        return Optional.of(new LinhaDoTempo(id.toString(), eventos));
    }

    public List<AlertaJson> alertas(Acesso acesso, String status, String destino) {
        return repositorio.alertas(acesso, relogio.instant(), status, destino, null).stream()
                .map(a -> new AlertaJson(String.valueOf(a.id()),
                        a.colaboradorId() == null ? null : a.colaboradorId().toString(),
                        protocolo("PD90", a.id(), a.criadoEm()), a.categoria(), severidade(a.categoria(), a.urgente()),
                        a.destino(), destinatarios(a), data(a.criadoEm()), a.status(), a.retorno(),
                        data(a.respondidoEm()), null,
                        a.acaoSugerida() != null ? a.acaoSugerida() : "Vale um contato de acompanhamento.",
                        true, acesso.rh() ? a.motivo() : null))
                .toList();
    }

    public List<RelatoJson> relatos(Acesso acesso, String estado) {
        return repositorio.relatos(acesso, estado).stream()
                .map(r -> new RelatoJson(protocolo("RL", r.id(), r.criadoEm()), rotulo(r.categoria()), r.categoria(),
                        severidade(r.categoria(), r.urgente()), r.estado(), data(r.criadoEm()), r.loja(), r.turno(),
                        r.texto() != null ? r.texto() : TEXTO_APAGADO,
                        List.of(new HistoricoRelato("Relato recebido", data(r.criadoEm()),
                                "Registro recebido pelo canal confidencial do WhatsApp."))))
                .toList();
    }

    public BemEstar bemEstar(Acesso acesso) {
        List<CategoriaQuantidade> distribuicao = repositorio.distribuicao(acesso).stream()
                .map(c -> new CategoriaQuantidade(rotulo(c.categoria()), c.quantidade()))
                .toList();
        if (!acesso.rh()) {
            // Gerente: só categorias e sugestões, sem identificador, histórico nem resumo.
            Map<String, String> sugestoes = new LinkedHashMap<>();
            for (AlertaPainel a : repositorio.alertas(acesso, relogio.instant(), null, null, null)) {
                sugestoes.putIfAbsent(rotulo(a.categoria()),
                        a.acaoSugerida() != null ? a.acaoSugerida() : SUGESTAO_PADRAO);
            }
            List<PessoaBemEstar> pessoas = sugestoes.entrySet().stream()
                    .map(e -> new PessoaBemEstar(null, e.getKey(), null, null, e.getValue(), null))
                    .toList();
            return new BemEstar(distribuicao, pessoas);
        }
        Instant semana = relogio.instant().minus(Duration.ofDays(7));
        Map<UUID, List<RelatoPainel>> porPessoa = repositorio.relatosBemEstar(acesso).stream()
                .collect(Collectors.groupingBy(RelatoPainel::colaboradorId, LinkedHashMap::new, Collectors.toList()));
        List<PessoaBemEstar> pessoas = porPessoa.entrySet().stream().map(e -> {
            List<RelatoPainel> relatos = e.getValue();
            RelatoPainel ultimo = relatos.get(0);
            long recentes = relatos.stream().filter(r -> r.criadoEm().isAfter(semana)).count();
            String tendencia = recentes >= 2 ? "mais frequente nos últimos 7 dias"
                    : recentes == 1 ? "1 relato nos últimos 7 dias" : "sem relatos nos últimos 7 dias";
            List<HistoricoBemEstar> historico = relatos.stream()
                    .map(r -> new HistoricoBemEstar("WHATSAPP", rotulo(r.categoria()),
                            r.motivo() != null ? r.motivo() : "Resumo indisponível ou apagado após 90 dias.",
                            data(r.criadoEm()), "Relato"))
                    .toList();
            return new PessoaBemEstar(e.getKey().toString(), rotulo(ultimo.categoria()), data(ultimo.criadoEm()),
                    tendencia, ultimo.acaoSugerida() != null ? ultimo.acaoSugerida() : SUGESTAO_PADRAO, historico);
        }).toList();
        return new BemEstar(distribuicao, pessoas);
    }

    // Regras

    private List<ColaboradorPainel> emJornada(Acesso acesso) {
        LocalDate hoje = LocalDate.now(relogio);
        return repositorio.colaboradores(acesso).stream()
                .filter(c -> !c.dataInicio().isAfter(hoje) && diaJornada(c, hoje) <= DIAS_JORNADA)
                .toList();
    }

    static int diaJornada(ColaboradorPainel c, LocalDate hoje) {
        return (int) ChronoUnit.DAYS.between(c.dataInicio(), hoje) + 1;
    }

    /** [nível, motivo], pelas notas dos últimos 7 dias. */
    private static String[] nivel(List<CheckinPainel> checkins, Instant agora) {
        Instant semana = agora.minus(Duration.ofDays(7));
        long baixas = checkins.stream()
                .filter(k -> k.humor() != null && k.respondidoEm() != null && k.respondidoEm().isAfter(semana))
                .filter(k -> k.humor() <= 2)
                .count();
        if (baixas >= 2) {
            return new String[] {PRIORIDADE, "Notas 1 ou 2 em " + baixas + " check-ins nos últimos 7 dias"};
        }
        if (baixas == 1) {
            return new String[] {ATENCAO, "Nota 1 ou 2 em um check-in nos últimos 7 dias"};
        }
        boolean temNota = checkins.stream().anyMatch(k -> k.humor() != null);
        return new String[] {ESTAVEL, temNota ? "Acompanhamento dentro do esperado" : "Ainda sem nota de check-in"};
    }

    /** Urgente = ALTA; dúvida = BAIXA; o resto = MODERADA. Não é pontuação de risco da pessoa. */
    static String severidade(String categoria, boolean urgente) {
        if (urgente) {
            return "ALTA";
        }
        return "DUVIDA".equals(categoria) ? "BAIXA" : "MODERADA";
    }

    static String rotulo(String categoria) {
        return ROTULOS.getOrDefault(categoria, "Assunto não identificado");
    }

    private String protocolo(String prefixo, long id, Instant criadoEm) {
        return "%s-%d-%04d".formatted(prefixo, criadoEm.atZone(fuso).getYear(), id);
    }

    private static String destinatarios(AlertaPainel a) {
        if (a.destinatarios() != null && !a.destinatarios().isBlank()) {
            return a.destinatarios();
        }
        return "RH".equals(a.destino()) ? "Equipe de RH" : "Gerente da loja";
    }

    /** Não há cargo cadastrado; o painel mostra o turno no lugar. */
    private static String funcao(ColaboradorPainel c) {
        return c.turno() == null || c.turno().isBlank() ? "Turno não informado" : "Turno: " + c.turno();
    }

    static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = nome.strip().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primeira + ultima).toUpperCase(Locale.ROOT);
    }

    private static int contar(List<AlertaPainel> alertas, String status) {
        return (int) alertas.stream().filter(a -> status.equals(a.status())).count();
    }

    private static Map<UUID, List<CheckinPainel>> porColaborador(List<CheckinPainel> checkins) {
        return checkins.stream().collect(Collectors.groupingBy(CheckinPainel::colaboradorId));
    }

    private OffsetDateTime data(Instant instante) {
        return instante == null ? null
                : instante.truncatedTo(ChronoUnit.SECONDS).atZone(fuso).toOffsetDateTime();
    }
}
