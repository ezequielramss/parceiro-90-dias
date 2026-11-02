package br.com.kallopolli.parceiro.jornada;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositórios em memória para os testes do webhook, com as mesmas regras do banco
 * que importam aqui: wamid único e um consentimento ativo por colaborador.
 * Os repositórios JDBC têm teste próprio contra o Postgres (JdbcRepositoriosIT).
 */
public class RepositoriosEmMemoria
        implements ColaboradorRepository, ConsentimentoRepository, MensagemRepository, AuditoriaRepository {

    public record Mensagem(String wamid, UUID colaboradorId, String direcao, String tipo, String texto) {}

    public record Consentimento(long id, UUID colaboradorId, String texto, boolean ativo) {}

    public record Auditoria(String quem, String acao, String objeto) {}

    private final Map<String, Colaborador> colaboradores = new ConcurrentHashMap<>();
    private final Map<String, Mensagem> mensagens = new ConcurrentHashMap<>();
    private final List<Consentimento> consentimentos = new CopyOnWriteArrayList<>();
    private final List<Auditoria> auditoria = new CopyOnWriteArrayList<>();
    private final AtomicLong sequencia = new AtomicLong();
    private volatile RuntimeException falha;

    public void limpar() {
        colaboradores.clear();
        mensagens.clear();
        consentimentos.clear();
        auditoria.clear();
        falha = null;
    }

    public Colaborador cadastrar(String telefoneE164) {
        var colaborador = new Colaborador(UUID.randomUUID(), telefoneE164);
        colaboradores.put(telefoneE164, colaborador);
        return colaborador;
    }

    /** Faz a próxima gravação de mensagem falhar, para testar o log de erro. */
    public void falharNaProximaGravacao(RuntimeException erro) {
        this.falha = erro;
    }

    public List<Mensagem> mensagens() {
        return List.copyOf(mensagens.values());
    }

    public List<Consentimento> consentimentos() {
        return List.copyOf(consentimentos);
    }

    public List<Auditoria> auditoria() {
        return List.copyOf(auditoria);
    }

    @Override
    public Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164) {
        return Optional.ofNullable(telefoneE164 == null ? null : colaboradores.get(telefoneE164));
    }

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

    @Override
    public boolean registrarEntrada(String wamid, UUID colaboradorId, String tipo, String texto) {
        RuntimeException erro = falha;
        if (erro != null) {
            falha = null;
            throw erro;
        }
        return mensagens.putIfAbsent(wamid, new Mensagem(wamid, colaboradorId, "ENTRADA", tipo, texto)) == null;
    }

    @Override
    public void registrarSaida(String wamid, UUID colaboradorId, String texto) {
        mensagens.putIfAbsent(wamid, new Mensagem(wamid, colaboradorId, "SAIDA", "text", texto));
    }

    @Override
    public void registrar(String quem, String acao, String objeto) {
        auditoria.add(new Auditoria(quem, acao, objeto));
    }
}
