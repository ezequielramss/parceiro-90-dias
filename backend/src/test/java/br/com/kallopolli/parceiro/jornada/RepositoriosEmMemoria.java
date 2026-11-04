package br.com.kallopolli.parceiro.jornada;

import br.com.kallopolli.parceiro.alerta.AlertaEntregaRepository;
import br.com.kallopolli.parceiro.alerta.AlertaRepository;
import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.alerta.ResponsavelRepository;
import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.conversa.CheckinRepository;
import br.com.kallopolli.parceiro.conversa.ConversaEstadoRepository;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.RelatoRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositórios em memória para os testes, com as regras do banco que importam aqui: wamid único,
 * um consentimento ativo por colaborador, um check-in por dia e uma entrega por alerta e
 * responsável. Os repositórios JDBC têm testes próprios contra o Postgres.
 */
public class RepositoriosEmMemoria implements ColaboradorRepository, ConsentimentoRepository, MensagemRepository,
        AuditoriaRepository, ConversaEstadoRepository, CheckinRepository, RelatoRepository, ResponsavelRepository,
        AlertaRepository, AlertaEntregaRepository {

    public static final String LOJA = "Loja Teste";
    public static final LocalDate INICIO = LocalDate.of(2026, 11, 2);

    public record Mensagem(long id, String wamid, UUID colaboradorId, String direcao, String tipo, String texto) {}

    public record Consentimento(long id, UUID colaboradorId, String texto, boolean ativo) {}

    public record Auditoria(String quem, String acao, String objeto) {}

    public record Checkin(long id, UUID colaboradorId, int dia, Integer humor) {}

    public record Relato(long id, UUID colaboradorId, long mensagemId, String texto, Classificacao classificacao,
                         String confirmacao) {}

    public record Alerta(long id, long relatoId, String destino, boolean urgente, String estado) {}

    public record EntregaSalva(long id, long alertaId, UUID responsavelId, String wamid, boolean falhaEnvio,
                               String resposta) {}

    private final Map<String, Colaborador> colaboradores = new ConcurrentHashMap<>();
    private final Map<String, Mensagem> mensagens = new ConcurrentHashMap<>();
    private final List<Consentimento> consentimentos = new CopyOnWriteArrayList<>();
    private final List<Auditoria> auditoria = new CopyOnWriteArrayList<>();
    private final Map<UUID, EstadoConversa> estados = new ConcurrentHashMap<>();
    private final Map<Long, Checkin> checkins = new ConcurrentHashMap<>();
    private final Map<Long, Relato> relatos = new ConcurrentHashMap<>();
    private final Map<UUID, Responsavel> responsaveis = new ConcurrentHashMap<>();
    private final Map<Long, Alerta> alertas = new ConcurrentHashMap<>();
    private final Map<Long, EntregaSalva> entregas = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();
    private volatile RuntimeException falha;

    public synchronized void limpar() {
        colaboradores.clear();
        mensagens.clear();
        consentimentos.clear();
        auditoria.clear();
        estados.clear();
        checkins.clear();
        relatos.clear();
        responsaveis.clear();
        alertas.clear();
        entregas.clear();
        falha = null;
    }

    // Cadastro de dados fictícios

    public Colaborador cadastrar(String telefoneE164) {
        return cadastrar(telefoneE164, LOJA, INICIO);
    }

    public Colaborador cadastrar(String telefoneE164, String loja, LocalDate dataInicio) {
        var colaborador = new Colaborador(UUID.randomUUID(), telefoneE164, "Pessoa Ficticia", loja, "Noite", dataInicio);
        colaboradores.put(telefoneE164, colaborador);
        return colaborador;
    }

    public Responsavel cadastrarResponsavel(String telefoneE164, String papel, String loja) {
        var responsavel = new Responsavel(UUID.randomUUID(), "Responsavel Ficticio", telefoneE164, papel, loja);
        responsaveis.put(responsavel.id(), responsavel);
        return responsavel;
    }

    /** Faz a próxima gravação de mensagem falhar, para testar o log de erro. */
    public void falharNaProximaGravacao(RuntimeException erro) {
        this.falha = erro;
    }

    // Consultas para os testes

    public List<Mensagem> mensagens() {
        return List.copyOf(mensagens.values());
    }

    public List<Consentimento> consentimentos() {
        return List.copyOf(consentimentos);
    }

    public List<Auditoria> auditoria() {
        return List.copyOf(auditoria);
    }

    public List<Checkin> checkins() {
        return checkins.values().stream().sorted(Comparator.comparingLong(Checkin::id)).toList();
    }

    public List<Relato> relatos() {
        return relatos.values().stream().sorted(Comparator.comparingLong(Relato::id)).toList();
    }

    public List<Alerta> alertas() {
        return alertas.values().stream().sorted(Comparator.comparingLong(Alerta::id)).toList();
    }

    public List<EntregaSalva> entregas() {
        return entregas.values().stream().sorted(Comparator.comparingLong(EntregaSalva::id)).toList();
    }

    // ColaboradorRepository

    @Override
    public Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164) {
        return Optional.ofNullable(telefoneE164 == null ? null : colaboradores.get(telefoneE164));
    }

    @Override
    public List<Colaborador> ativosComAceiteIniciadosEntre(LocalDate de, LocalDate ate) {
        return colaboradores.values().stream()
                .filter(c -> !c.dataInicio().isBefore(de) && !c.dataInicio().isAfter(ate))
                .filter(c -> temAtivo(c.id()))
                .toList();
    }

    @Override
    public List<Colaborador> colaboradoresAtivos() {
        return colaboradores.values().stream().sorted(Comparator.comparing(Colaborador::telefone)).toList();
    }

    // ConsentimentoRepository

    @Override
    public boolean temAtivo(UUID colaboradorId) {
        return consentimentos.stream().anyMatch(c -> c.colaboradorId().equals(colaboradorId) && c.ativo());
    }

    @Override
    public synchronized long registrarAceite(UUID colaboradorId, String textoAceito) {
        if (temAtivo(colaboradorId)) {
            throw new IllegalStateException("consentimento ativo duplicado");
        }
        long id = sequencia.incrementAndGet();
        consentimentos.add(new Consentimento(id, colaboradorId, textoAceito, true));
        return id;
    }

    @Override
    public synchronized Optional<Long> registrarRetirada(UUID colaboradorId) {
        for (int i = 0; i < consentimentos.size(); i++) {
            var c = consentimentos.get(i);
            if (c.colaboradorId().equals(colaboradorId) && c.ativo()) {
                consentimentos.set(i, new Consentimento(c.id(), c.colaboradorId(), c.texto(), false));
                return Optional.of(c.id());
            }
        }
        return Optional.empty();
    }

    // MensagemRepository

    @Override
    public synchronized Optional<Long> registrarEntrada(String wamid, UUID colaboradorId, String tipo, String texto) {
        RuntimeException erro = falha;
        if (erro != null) {
            falha = null;
            throw erro;
        }
        if (mensagens.containsKey(wamid)) {
            return Optional.empty();
        }
        long id = sequencia.incrementAndGet();
        mensagens.put(wamid, new Mensagem(id, wamid, colaboradorId, "ENTRADA", tipo, texto));
        return Optional.of(id);
    }

    @Override
    public synchronized void registrarSaida(String wamid, UUID colaboradorId, String texto) {
        mensagens.putIfAbsent(wamid, new Mensagem(sequencia.incrementAndGet(), wamid, colaboradorId, "SAIDA", "text", texto));
    }

    // AuditoriaRepository

    @Override
    public void registrar(String quem, String acao, String objeto) {
        auditoria.add(new Auditoria(quem, acao, objeto));
    }

    // ConversaEstadoRepository

    @Override
    public EstadoConversa estadoDe(UUID colaboradorId) {
        return estados.getOrDefault(colaboradorId, EstadoConversa.LIVRE);
    }

    @Override
    public void definir(UUID colaboradorId, EstadoConversa estado) {
        estados.put(colaboradorId, estado);
    }

    // CheckinRepository

    @Override
    public synchronized Optional<Long> criarSeNovo(UUID colaboradorId, int diaJornada) {
        boolean existe = checkins.values().stream()
                .anyMatch(c -> c.colaboradorId().equals(colaboradorId) && c.dia() == diaJornada);
        if (existe) {
            return Optional.empty();
        }
        long id = sequencia.incrementAndGet();
        checkins.put(id, new Checkin(id, colaboradorId, diaJornada, null));
        return Optional.of(id);
    }

    @Override
    public void registrarHumor(long checkinId, int humor) {
        checkins.computeIfPresent(checkinId, (k, c) -> new Checkin(c.id(), c.colaboradorId(), c.dia(), humor));
    }

    // RelatoRepository

    @Override
    public long criar(UUID colaboradorId, long mensagemId, String texto) {
        long id = sequencia.incrementAndGet();
        relatos.put(id, new Relato(id, colaboradorId, mensagemId, texto,
                new Classificacao("NAO_CLASSIFICADO", null, null, null, false, false), AGUARDANDO));
        return id;
    }

    @Override
    public void gravarClassificacao(long relatoId, Classificacao classificacao) {
        relatos.computeIfPresent(relatoId, (k, r) ->
                new Relato(r.id(), r.colaboradorId(), r.mensagemId(), r.texto(), classificacao, r.confirmacao()));
    }

    @Override
    public void atualizarTexto(long relatoId, String texto) {
        relatos.computeIfPresent(relatoId, (k, r) ->
                new Relato(r.id(), r.colaboradorId(), r.mensagemId(), texto, r.classificacao(), r.confirmacao()));
    }

    @Override
    public void definirConfirmacao(long relatoId, String confirmacao) {
        relatos.computeIfPresent(relatoId, (k, r) ->
                new Relato(r.id(), r.colaboradorId(), r.mensagemId(), r.texto(), r.classificacao(), confirmacao));
    }

    @Override
    public synchronized int descartarAguardando(UUID colaboradorId) {
        List<Long> ids = new ArrayList<>();
        relatos.values().stream()
                .filter(r -> r.colaboradorId().equals(colaboradorId) && AGUARDANDO.equals(r.confirmacao()))
                .forEach(r -> ids.add(r.id()));
        ids.forEach(id -> definirConfirmacao(id, DESCARTADO));
        return ids.size();
    }

    @Override
    public Optional<RelatoSalvo> buscarRelato(long relatoId) {
        return Optional.ofNullable(relatos.get(relatoId))
                .map(r -> new RelatoSalvo(r.id(), r.colaboradorId(), r.classificacao(), r.confirmacao()));
    }

    // ResponsavelRepository

    @Override
    public List<Responsavel> gerentesAtivosDaLoja(String loja) {
        return responsaveis.values().stream()
                .filter(r -> Responsavel.GERENTE.equals(r.papel()) && normalizar(r.loja()).equals(normalizar(loja)))
                .toList();
    }

    @Override
    public List<Responsavel> rhAtivos() {
        return responsaveis.values().stream().filter(r -> Responsavel.RH.equals(r.papel())).toList();
    }

    @Override
    public List<Responsavel> ativosPorTelefones(List<String> telefonesE164) {
        return responsaveis.values().stream().filter(r -> telefonesE164.contains(r.telefone())).toList();
    }

    @Override
    public Optional<Responsavel> buscarResponsavel(UUID id) {
        return Optional.ofNullable(responsaveis.get(id));
    }

    @Override
    public List<Responsavel> responsaveisAtivos() {
        return responsaveis.values().stream()
                .sorted(Comparator.comparing(Responsavel::papel).thenComparing(Responsavel::telefone))
                .toList();
    }

    /** Mesma regra de public.normalizar_loja. */
    private static String normalizar(String loja) {
        return loja == null ? "" : loja.strip().replaceAll("\\s+", " ").toLowerCase();
    }

    // AlertaRepository

    @Override
    public long criar(long relatoId, String destino, boolean urgente) {
        long id = sequencia.incrementAndGet();
        alertas.put(id, new Alerta(id, relatoId, destino, urgente, "ABERTO"));
        return id;
    }

    @Override
    public void marcarAtendido(long alertaId) {
        alertas.computeIfPresent(alertaId, (k, a) -> new Alerta(a.id(), a.relatoId(), a.destino(), a.urgente(), "ATENDIDO"));
    }

    // AlertaEntregaRepository

    @Override
    public synchronized long criar(long alertaId, UUID responsavelId) {
        boolean existe = entregas.values().stream()
                .anyMatch(e -> e.alertaId() == alertaId && e.responsavelId().equals(responsavelId));
        if (existe) {
            throw new IllegalStateException("entrega duplicada");
        }
        long id = sequencia.incrementAndGet();
        entregas.put(id, new EntregaSalva(id, alertaId, responsavelId, null, false, null));
        return id;
    }

    @Override
    public void registrarEnvio(long entregaId, String wamid) {
        entregas.computeIfPresent(entregaId, (k, e) ->
                new EntregaSalva(e.id(), e.alertaId(), e.responsavelId(), wamid, e.falhaEnvio(), e.resposta()));
    }

    @Override
    public void marcarFalha(long entregaId) {
        entregas.computeIfPresent(entregaId, (k, e) ->
                new EntregaSalva(e.id(), e.alertaId(), e.responsavelId(), e.wamid(), true, e.resposta()));
    }

    @Override
    public Optional<Entrega> buscarEntrega(long entregaId) {
        return Optional.ofNullable(entregas.get(entregaId))
                .map(e -> new Entrega(e.id(), e.alertaId(), e.responsavelId(), e.falhaEnvio(), e.resposta()));
    }

    /** Mesma regra do SQL: enviada sem falha, sem CONVERSEI, a mais recente (id maior). */
    @Override
    public Optional<Long> maisRecenteAberta(List<UUID> responsavelIds) {
        return entregas.values().stream()
                .filter(e -> responsavelIds.contains(e.responsavelId()))
                .filter(e -> !e.falhaEnvio() && !AlertaEntregaRepository.CONVERSEI.equals(e.resposta()))
                .max(Comparator.comparingLong(EntregaSalva::id))
                .map(EntregaSalva::id);
    }

    @Override
    public void responder(long entregaId, String resposta) {
        entregas.computeIfPresent(entregaId, (k, e) ->
                new EntregaSalva(e.id(), e.alertaId(), e.responsavelId(), e.wamid(), e.falhaEnvio(), resposta));
    }
}
