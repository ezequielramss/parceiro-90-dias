package br.com.kallopolli.parceiro.painel;

import br.com.kallopolli.parceiro.classificacao.RoteadorRelato;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Versão em memória das leituras do painel, com as mesmas regras de visibilidade do SQL de
 * JdbcPainelRepositorios (o teste JDBC confere o SQL contra o Postgres). Dados fictícios.
 */
public class PainelEmMemoria implements PainelRepository, AcessoRepository, RetencaoRepository {

    /** Destinos da tabela categorias (migração 20261102190100). */
    static final Map<String, String> DESTINO_CATEGORIA = Map.ofEntries(
            Map.entry("CANSACO", "GERENTE"), Map.entry("SOBRECARGA", "GERENTE"),
            Map.entry("DESMOTIVACAO", "GERENTE"), Map.entry("DUVIDA", "GERENTE"),
            Map.entry("CONFLITO_COLEGA", "GERENTE"), Map.entry("VONTADE_DE_SAIR", "GERENTE_E_RH"),
            Map.entry("CONFLITO_CHEFIA", "RH"), Map.entry("NAO_CLASSIFICADO", "RH"), Map.entry("ASSEDIO", "RH"),
            Map.entry("HUMILHACAO", "RH"), Map.entry("SITUACAO_GRAVE", "RH"), Map.entry("ELOGIO", "AGREGADO"));
    private static final Set<String> PARA_GERENTE = Set.of("GERENTE", "GERENTE_E_RH");

    public record Colab(UUID id, String nome, String loja, String turno, LocalDate dataInicio, boolean ativo,
                        boolean aceite, Instant ultimoContato) {}

    public static final class Relato {
        public final long id;
        public final UUID colaboradorId;
        public final String categoria;
        public final String estado;
        public final Instant criadoEm;
        public final String acao;
        public final boolean sinaisGraves;
        public volatile String texto;
        public volatile String motivo;

        Relato(long id, UUID colaboradorId, String categoria, String estado, Instant criadoEm, String texto,
               String motivo, String acao, boolean sinaisGraves) {
            this.id = id;
            this.colaboradorId = colaboradorId;
            this.categoria = categoria;
            this.estado = estado;
            this.criadoEm = criadoEm;
            this.texto = texto;
            this.motivo = motivo;
            this.acao = acao;
            this.sinaisGraves = sinaisGraves;
        }
    }

    public record Alerta(long id, long relatoId, String destino, boolean urgente, String estado, Instant criadoEm,
                         String retorno, Instant respondidoEm, String destinatarios, boolean entregueAoRh) {}

    public static final class Mensagem {
        final Instant criadaEm;
        volatile String texto;

        Mensagem(Instant criadaEm, String texto) {
            this.criadaEm = criadaEm;
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }
    }

    private final Map<UUID, Acesso> acessos = new ConcurrentHashMap<>();
    private final Map<UUID, Colab> colaboradores = new ConcurrentHashMap<>();
    private final List<CheckinPainel> checkins = new CopyOnWriteArrayList<>();
    private final Map<Long, Relato> relatos = new ConcurrentHashMap<>();
    private final Map<Long, Alerta> alertas = new ConcurrentHashMap<>();
    private final List<Mensagem> mensagens = new CopyOnWriteArrayList<>();
    private final AtomicLong sequencia = new AtomicLong(100);

    public synchronized void limpar() {
        acessos.clear();
        colaboradores.clear();
        checkins.clear();
        relatos.clear();
        alertas.clear();
        mensagens.clear();
    }

    // Cadastro de dados fictícios

    public void darAcesso(Acesso acesso) {
        acessos.put(acesso.userId(), acesso);
    }

    public Colab colaborador(String nome, String loja, LocalDate inicio, Instant ultimoContato) {
        var c = new Colab(UUID.randomUUID(), nome, loja, "Noite", inicio, true, true, ultimoContato);
        colaboradores.put(c.id(), c);
        return c;
    }

    public void guardar(Colab c) {
        colaboradores.put(c.id(), c);
    }

    public void checkin(UUID colaborador, int dia, Instant enviado, Instant respondido, Integer humor) {
        checkins.add(new CheckinPainel(colaborador, dia, enviado, respondido, humor));
    }

    public Relato relato(UUID colaborador, String categoria, Instant criadoEm, String texto, String motivo,
                         String acao) {
        var r = new Relato(sequencia.incrementAndGet(), colaborador, categoria, "NOVO", criadoEm, texto, motivo, acao,
                false);
        relatos.put(r.id, r);
        mensagens.add(new Mensagem(criadoEm, texto));
        return r;
    }

    public Alerta alerta(Relato relato, String destino, boolean urgente, String estado, Instant criadoEm,
                         String retorno, Instant respondidoEm) {
        var a = new Alerta(sequencia.incrementAndGet(), relato.id, destino, urgente, estado, criadoEm, retorno,
                respondidoEm, "Responsavel Ficticio", destino.contains("RH"));
        alertas.put(a.id(), a);
        return a;
    }

    public List<Mensagem> mensagens() {
        return List.copyOf(mensagens);
    }

    // AcessoRepository

    @Override
    public Optional<Acesso> buscarAcesso(UUID userId) {
        return Optional.ofNullable(acessos.get(userId));
    }

    // PainelRepository

    @Override
    public List<ColaboradorPainel> colaboradores(Acesso acesso) {
        return colaboradores.values().stream()
                .filter(c -> visivel(acesso, c))
                .sorted(Comparator.comparing(Colab::dataInicio).reversed().thenComparing(Colab::nome))
                .map(PainelEmMemoria::linha)
                .toList();
    }

    @Override
    public Optional<ColaboradorPainel> colaborador(Acesso acesso, UUID id) {
        return Optional.ofNullable(colaboradores.get(id)).filter(c -> visivel(acesso, c)).map(PainelEmMemoria::linha);
    }

    @Override
    public List<CheckinPainel> checkins(Acesso acesso, UUID colaboradorId) {
        return checkins.stream()
                .filter(k -> visivel(acesso, colaboradores.get(k.colaboradorId())))
                .filter(k -> colaboradorId == null || colaboradorId.equals(k.colaboradorId()))
                .sorted(Comparator.comparing(CheckinPainel::enviadoEm))
                .toList();
    }

    @Override
    public List<AlertaPainel> alertas(Acesso acesso, Instant agora, String status, String destino,
                                      UUID colaboradorId) {
        List<AlertaPainel> lista = new ArrayList<>();
        for (Alerta a : alertas.values()) {
            Relato r = relatos.get(a.relatoId());
            Colab c = colaboradores.get(r.colaboradorId);
            if (!alertaVisivel(acesso, a, r, c)) {
                continue;
            }
            String s = "ABERTO".equals(a.estado()) && a.criadoEm().isBefore(agora.minus(Duration.ofHours(24)))
                    ? "SEM_RETORNO_24H" : a.estado();
            if ((status != null && !status.equals(s))
                    || (destino != null && !destino.equals(a.destino()) && !"GERENTE_E_RH".equals(a.destino()))
                    || (colaboradorId != null && !colaboradorId.equals(r.colaboradorId))) {
                continue;
            }
            boolean mostraPessoa = acesso.rh() && !RoteadorRelato.RESTRITAS.contains(r.categoria);
            lista.add(new AlertaPainel(a.id(), mostraPessoa ? r.colaboradorId : null, r.categoria, a.destino(),
                    a.urgente(), s, a.criadoEm(), acesso.rh() ? r.motivo : null, r.acao, a.retorno(),
                    a.respondidoEm(), a.destinatarios()));
        }
        lista.sort(Comparator.comparing(AlertaPainel::criadoEm).reversed());
        return lista;
    }

    @Override
    public List<RelatoPainel> relatos(Acesso acesso, String estado) {
        if (!acesso.rh()) {
            return List.of();
        }
        return relatos.values().stream()
                .filter(r -> estado == null || estado.equals(r.estado))
                .filter(r -> alertasDo(r).anyMatch(Alerta::entregueAoRh))
                .sorted(Comparator.comparing((Relato r) -> r.criadoEm).reversed())
                .map(r -> linha(r, alertasDo(r).filter(Alerta::entregueAoRh).anyMatch(Alerta::urgente), r.texto))
                .toList();
    }

    @Override
    public List<RelatoPainel> relatosBemEstar(Acesso acesso) {
        if (!acesso.rh()) {
            return List.of();
        }
        return relatos.values().stream()
                .filter(r -> visivel(acesso, colaboradores.get(r.colaboradorId)))
                .filter(r -> !RoteadorRelato.RESTRITAS.contains(r.categoria))
                .filter(r -> "AGREGADO".equals(DESTINO_CATEGORIA.get(r.categoria)) || alertasDo(r).findAny().isPresent())
                .sorted(Comparator.comparing((Relato r) -> r.criadoEm).reversed())
                .map(r -> linha(r, alertasDo(r).anyMatch(Alerta::urgente), null))
                .toList();
    }

    @Override
    public List<Contagem> distribuicao(Acesso acesso) {
        Map<String, Long> contagem = new LinkedHashMap<>();
        for (Relato r : relatos.values()) {
            Colab c = colaboradores.get(r.colaboradorId);
            String destinoCategoria = DESTINO_CATEGORIA.get(r.categoria);
            boolean conta;
            if (acesso.rh()) {
                conta = "AGREGADO".equals(destinoCategoria) || alertasDo(r).findAny().isPresent();
            } else {
                conta = mesmaLoja(acesso, c) && !RoteadorRelato.RESTRITAS.contains(r.categoria)
                        && (("AGREGADO".equals(destinoCategoria) && !r.sinaisGraves)
                            || (PARA_GERENTE.contains(destinoCategoria) && alertasDo(r)
                                .anyMatch(a -> !a.urgente() && PARA_GERENTE.contains(a.destino()))));
            }
            if (conta) {
                contagem.merge(r.categoria, 1L, Long::sum);
            }
        }
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Contagem(e.getKey(), e.getValue()))
                .toList();
    }

    // RetencaoRepository

    @Override
    public synchronized Apagados apagarTextosAntesDe(Instant limite) {
        int r = 0;
        for (Relato relato : relatos.values()) {
            if (relato.criadoEm.isBefore(limite) && (relato.texto != null || relato.motivo != null)) {
                relato.texto = null;
                relato.motivo = null;
                r++;
            }
        }
        int m = 0;
        for (Mensagem mensagem : mensagens) {
            if (mensagem.criadaEm.isBefore(limite) && mensagem.texto != null) {
                mensagem.texto = null;
                m++;
            }
        }
        return new Apagados(r, m);
    }

    // Mesmas regras do SQL

    private static boolean visivel(Acesso acesso, Colab c) {
        return c != null && c.ativo() && c.aceite() && (acesso.rh() || mesmaLoja(acesso, c));
    }

    private static boolean mesmaLoja(Acesso acesso, Colab c) {
        return c != null && acesso.lojaDoFiltro() != null
                && normalizar(c.loja()).equals(normalizar(acesso.lojaDoFiltro()));
    }

    private static boolean alertaVisivel(Acesso acesso, Alerta a, Relato r, Colab c) {
        return acesso.rh() || (mesmaLoja(acesso, c) && PARA_GERENTE.contains(a.destino()) && !a.urgente()
                && PARA_GERENTE.contains(DESTINO_CATEGORIA.get(r.categoria))
                && !RoteadorRelato.RESTRITAS.contains(r.categoria));
    }

    private java.util.stream.Stream<Alerta> alertasDo(Relato r) {
        return alertas.values().stream().filter(a -> a.relatoId() == r.id);
    }

    private RelatoPainel linha(Relato r, boolean urgente, String texto) {
        Colab c = colaboradores.get(r.colaboradorId);
        return new RelatoPainel(r.id, r.colaboradorId, c.loja(), c.turno(), r.categoria, r.estado, urgente,
                r.criadoEm, texto, r.motivo, r.acao);
    }

    private static ColaboradorPainel linha(Colab c) {
        return new ColaboradorPainel(c.id(), c.nome(), c.loja(), c.turno(), c.dataInicio(), c.ultimoContato());
    }

    /** Mesma regra de public.normalizar_loja. */
    static String normalizar(String loja) {
        return Objects.requireNonNullElse(loja, "").strip().replaceAll("\\s+", " ").toLowerCase();
    }
}
