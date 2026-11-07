package br.com.kallopolli.parceiro.conversa.jdbc;

import br.com.kallopolli.parceiro.classificacao.Classificacao;
import br.com.kallopolli.parceiro.conversa.CheckinRepository;
import br.com.kallopolli.parceiro.conversa.ConversaEstadoRepository;
import br.com.kallopolli.parceiro.conversa.EstadoConversa;
import br.com.kallopolli.parceiro.conversa.RelatoRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Estado da conversa, check-ins e relatos (migrações 20261102200100 e 20261103120000). */
@Repository
public class JdbcConversaRepositorios implements ConversaEstadoRepository, CheckinRepository, RelatoRepository {

    private final JdbcClient jdbc;

    public JdbcConversaRepositorios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EstadoConversa estadoDe(UUID colaboradorId) {
        return jdbc.sql("select estado, checkin_id, relato_id, agente from public.conversa_estado where colaborador_id = :id")
                .param("id", colaboradorId)
                .query((rs, n) -> new EstadoConversa(
                        EstadoConversa.Tipo.valueOf(rs.getString("estado")),
                        rs.getObject("checkin_id", Long.class),
                        rs.getObject("relato_id", Long.class),
                        rs.getString("agente")))
                .optional()
                .orElse(EstadoConversa.LIVRE);
    }

    @Override
    public void definir(UUID colaboradorId, EstadoConversa estado) {
        jdbc.sql("""
                        insert into public.conversa_estado (colaborador_id, estado, checkin_id, relato_id, agente)
                        values (:id, :estado, :checkin, :relato, :agente)
                        on conflict (colaborador_id) do update
                          set estado = excluded.estado, checkin_id = excluded.checkin_id,
                              relato_id = excluded.relato_id, agente = excluded.agente""")
                .param("id", colaboradorId)
                .param("estado", estado.tipo().name())
                .param("checkin", estado.checkinId())
                .param("relato", estado.relatoId())
                .param("agente", estado.agente())
                .update();
    }

    @Override
    public Optional<Instant> atualizadoEm(UUID colaboradorId) {
        return jdbc.sql("select atualizado_em from public.conversa_estado where colaborador_id = :id")
                .param("id", colaboradorId)
                .query((rs, n) -> rs.getObject("atualizado_em", OffsetDateTime.class).toInstant())
                .optional();
    }

    @Override
    public Optional<Long> criarSeNovo(UUID colaboradorId, int diaJornada) {
        return jdbc.sql("""
                        insert into public.checkins (colaborador_id, dia_jornada) values (:id, :dia)
                        on conflict (colaborador_id, dia_jornada) do nothing
                        returning id""")
                .param("id", colaboradorId)
                .param("dia", diaJornada)
                .query(Long.class)
                .optional();
    }

    @Override
    public void registrarHumor(long checkinId, int humor) {
        jdbc.sql("update public.checkins set humor = :humor, respondido_em = now() where id = :id")
                .param("humor", humor)
                .param("id", checkinId)
                .update();
    }

    @Override
    public long criar(UUID colaboradorId, long mensagemId, String texto) {
        return jdbc.sql("""
                        insert into public.relatos (colaborador_id, mensagem_id, texto)
                        values (:colaborador, :mensagem, :texto) returning id""")
                .param("colaborador", colaboradorId)
                .param("mensagem", mensagemId)
                .param("texto", texto)
                .query(Long.class)
                .single();
    }

    @Override
    public void gravarClassificacao(long relatoId, Classificacao c) {
        jdbc.sql("""
                        update public.relatos
                        set categoria = :categoria, motivo = :motivo, acao_sugerida = :acao, confianca = :confianca,
                            sinais_graves = :sinais, falha_ia = :falha
                        where id = :id""")
                .param("categoria", c.categoria())
                .param("motivo", c.motivo())
                .param("acao", c.acaoSugerida())
                .param("confianca", c.confianca())
                .param("sinais", c.sinaisGraves())
                .param("falha", c.falhaIa())
                .param("id", relatoId)
                .update();
    }

    @Override
    public void atualizarTexto(long relatoId, String texto) {
        jdbc.sql("update public.relatos set texto = :texto where id = :id")
                .param("texto", texto)
                .param("id", relatoId)
                .update();
    }

    @Override
    public void definirConfirmacao(long relatoId, String confirmacao) {
        jdbc.sql("update public.relatos set confirmacao = :confirmacao where id = :id")
                .param("confirmacao", confirmacao)
                .param("id", relatoId)
                .update();
    }

    @Override
    public int descartarAguardando(UUID colaboradorId) {
        return jdbc.sql("""
                        update public.relatos set confirmacao = 'DESCARTADO'
                        where colaborador_id = :id and confirmacao = 'AGUARDANDO'""")
                .param("id", colaboradorId)
                .update();
    }

    @Override
    public Optional<RelatoSalvo> buscarRelato(long relatoId) {
        return jdbc.sql("""
                        select id, colaborador_id, categoria, motivo, acao_sugerida, confianca, sinais_graves,
                               falha_ia, confirmacao
                        from public.relatos where id = :id""")
                .param("id", relatoId)
                .query((rs, n) -> new RelatoSalvo(
                        rs.getLong("id"),
                        rs.getObject("colaborador_id", UUID.class),
                        new Classificacao(rs.getString("categoria"), rs.getString("motivo"), rs.getString("acao_sugerida"),
                                rs.getString("confianca"), rs.getBoolean("sinais_graves"), rs.getBoolean("falha_ia")),
                        rs.getString("confirmacao")))
                .optional();
    }
}
