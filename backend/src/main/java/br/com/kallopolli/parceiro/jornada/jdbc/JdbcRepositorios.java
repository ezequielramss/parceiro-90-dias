package br.com.kallopolli.parceiro.jornada.jdbc;

import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.jornada.ConsentimentoRepository;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso às tabelas da Etapa 1 (supabase/migrations/20261102200100_jornada_colaboradores.sql). */
@Repository
public class JdbcRepositorios
        implements ColaboradorRepository, ConsentimentoRepository, MensagemRepository, AuditoriaRepository {

    private final JdbcClient jdbc;

    public JdbcRepositorios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLUNAS_COLABORADOR = "c.id, c.telefone, c.nome, c.loja, c.turno, c.data_inicio";

    private static final RowMapper<Colaborador> COLABORADOR = (rs, n) -> new Colaborador(
            rs.getObject("id", UUID.class),
            rs.getString("telefone"),
            rs.getString("nome"),
            rs.getString("loja"),
            rs.getString("turno"),
            rs.getObject("data_inicio", LocalDate.class));

    @Override
    public Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164) {
        return jdbc.sql("select " + COLUNAS_COLABORADOR + " from public.colaboradores c where c.telefone = :telefone and c.ativo")
                .param("telefone", telefoneE164)
                .query(COLABORADOR)
                .optional();
    }

    @Override
    public List<Colaborador> ativosComAceiteIniciadosEntre(LocalDate de, LocalDate ate) {
        return jdbc.sql("""
                        select %s from public.colaboradores c
                        where c.ativo and c.data_inicio between :de and :ate
                          and exists (select 1 from public.consentimentos k
                                      where k.colaborador_id = c.id and k.retirado_em is null)""".formatted(COLUNAS_COLABORADOR))
                .param("de", de)
                .param("ate", ate)
                .query(COLABORADOR)
                .list();
    }

    @Override
    public List<Colaborador> colaboradoresAtivos() {
        return jdbc.sql("select " + COLUNAS_COLABORADOR + " from public.colaboradores c where c.ativo order by c.nome, c.id")
                .query(COLABORADOR)
                .list();
    }

    @Override
    public boolean temAtivo(UUID colaboradorId) {
        return jdbc.sql("""
                        select exists (select 1 from public.consentimentos
                                       where colaborador_id = :id and retirado_em is null)""")
                .param("id", colaboradorId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public long registrarAceite(UUID colaboradorId, String textoAceito) {
        return jdbc.sql("""
                        insert into public.consentimentos (colaborador_id, texto_aceito)
                        values (:id, :texto) returning id""")
                .param("id", colaboradorId)
                .param("texto", textoAceito)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<Long> registrarRetirada(UUID colaboradorId) {
        return jdbc.sql("""
                        update public.consentimentos set retirado_em = now()
                        where colaborador_id = :id and retirado_em is null returning id""")
                .param("id", colaboradorId)
                .query(Long.class)
                .optional();
    }

    @Override
    public Optional<Long> registrarEntrada(String wamid, UUID colaboradorId, String tipo, String texto) {
        return inserirMensagem(wamid, colaboradorId, "ENTRADA", tipo, texto);
    }

    @Override
    public void registrarSaida(String wamid, UUID colaboradorId, String texto) {
        inserirMensagem(wamid, colaboradorId, "SAIDA", "text", texto);
    }

    private Optional<Long> inserirMensagem(String wamid, UUID colaboradorId, String direcao, String tipo, String texto) {
        return jdbc.sql("""
                        insert into public.mensagens (wamid, colaborador_id, direcao, tipo, texto)
                        values (:wamid, :colaborador, :direcao, :tipo, :texto)
                        on conflict (wamid) do nothing
                        returning id""")
                .param("wamid", wamid)
                .param("colaborador", colaboradorId)
                .param("direcao", direcao)
                .param("tipo", tipo)
                .param("texto", texto)
                .query(Long.class)
                .optional();
    }

    @Override
    public void registrar(String quem, String acao, String objeto) {
        jdbc.sql("insert into public.auditoria (quem, acao, objeto) values (:quem, :acao, :objeto)")
                .param("quem", quem)
                .param("acao", acao)
                .param("objeto", objeto)
                .update();
    }
}
