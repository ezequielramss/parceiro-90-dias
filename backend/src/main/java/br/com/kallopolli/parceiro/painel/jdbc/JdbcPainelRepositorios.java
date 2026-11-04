package br.com.kallopolli.parceiro.painel.jdbc;

import br.com.kallopolli.parceiro.classificacao.RoteadorRelato;
import br.com.kallopolli.parceiro.painel.Acesso;
import br.com.kallopolli.parceiro.painel.AcessoRepository;
import br.com.kallopolli.parceiro.painel.PainelRepository;
import br.com.kallopolli.parceiro.painel.RetencaoRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leituras do painel e retenção (migrações 20261102200100, 20261103120000 e 20261104120000).
 * Toda consulta é parametrizada e o filtro de papel e loja fica no WHERE.
 */
@Repository
public class JdbcPainelRepositorios implements PainelRepository, AcessoRepository, RetencaoRepository {

    private static final List<String> RESTRITAS = List.copyOf(RoteadorRelato.RESTRITAS);

    /** Colaborador ativo, com aceite ativo, e da loja do gerente (o RH vê todas). Alias c. */
    private static final String COLABORADOR_VISIVEL = """
            c.ativo
            and exists (select 1 from public.consentimentos k
                        where k.colaborador_id = c.id and k.retirado_em is null)
            and (:rh or public.normalizar_loja(c.loja) = public.normalizar_loja(cast(:loja as text)))""";

    /** Alerta que o papel vê. Aliases a, r, c e cat. */
    private static final String ALERTA_VISIVEL = """
            (:rh or (public.normalizar_loja(c.loja) = public.normalizar_loja(cast(:loja as text))
                     and a.destino in ('GERENTE', 'GERENTE_E_RH') and not a.urgente
                     and cat.destino in ('GERENTE', 'GERENTE_E_RH')
                     and r.categoria not in (:restritas)))""";

    private static final String STATUS_ALERTA =
            "case when a.estado = 'ABERTO' and a.criado_em < :limite24h then 'SEM_RETORNO_24H' else a.estado end";

    private static final String COLUNAS_COLABORADOR = """
            c.id, c.nome, c.loja, c.turno, c.data_inicio,
            (select max(m.criada_em) from public.mensagens m
             where m.colaborador_id = c.id and m.direcao = 'ENTRADA') as ultimo_contato""";

    private static final RowMapper<ColaboradorPainel> COLABORADOR = (rs, n) -> new ColaboradorPainel(
            rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("loja"), rs.getString("turno"),
            rs.getObject("data_inicio", LocalDate.class), instante(rs, "ultimo_contato"));

    private static final RowMapper<RelatoPainel> RELATO = (rs, n) -> new RelatoPainel(
            rs.getLong("id"), rs.getObject("colaborador_id", UUID.class), rs.getString("loja"), rs.getString("turno"),
            rs.getString("categoria"), rs.getString("estado"), rs.getBoolean("urgente"), instante(rs, "criado_em"),
            rs.getString("texto"), rs.getString("motivo"), rs.getString("acao_sugerida"));

    private final JdbcClient jdbc;

    public JdbcPainelRepositorios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // AcessoRepository

    @Override
    public Optional<Acesso> buscarAcesso(UUID userId) {
        return jdbc.sql("select user_id, papel, loja, nome from public.acessos_dashboard where user_id = :id")
                .param("id", userId)
                .query((rs, n) -> new Acesso(rs.getObject("user_id", UUID.class), rs.getString("papel"),
                        rs.getString("loja"), rs.getString("nome")))
                .optional();
    }

    // PainelRepository

    @Override
    public List<ColaboradorPainel> colaboradores(Acesso acesso) {
        return escopo(jdbc.sql("select " + COLUNAS_COLABORADOR + " from public.colaboradores c where "
                        + COLABORADOR_VISIVEL + " order by c.data_inicio desc, c.nome, c.id"), acesso)
                .query(COLABORADOR)
                .list();
    }

    @Override
    public Optional<ColaboradorPainel> colaborador(Acesso acesso, UUID id) {
        return escopo(jdbc.sql("select " + COLUNAS_COLABORADOR + " from public.colaboradores c where "
                        + COLABORADOR_VISIVEL + " and c.id = :id"), acesso)
                .param("id", id)
                .query(COLABORADOR)
                .optional();
    }

    @Override
    public List<CheckinPainel> checkins(Acesso acesso, UUID colaboradorId) {
        return escopo(jdbc.sql("""
                        select k.colaborador_id, k.dia_jornada, k.enviado_em, k.respondido_em, k.humor
                        from public.checkins k
                        join public.colaboradores c on c.id = k.colaborador_id
                        where %s
                          and (cast(:colaborador as uuid) is null or c.id = :colaborador)
                        order by k.enviado_em, k.id""".formatted(COLABORADOR_VISIVEL)), acesso)
                .param("colaborador", colaboradorId)
                .query((rs, n) -> new CheckinPainel(rs.getObject("colaborador_id", UUID.class),
                        rs.getInt("dia_jornada"), instante(rs, "enviado_em"), instante(rs, "respondido_em"),
                        rs.getObject("humor", Integer.class)))
                .list();
    }

    @Override
    public List<AlertaPainel> alertas(Acesso acesso, Instant agora, String status, String destino,
                                      UUID colaboradorId) {
        return escopo(jdbc.sql("""
                        select a.id,
                               case when :rh and r.categoria not in (:restritas) then r.colaborador_id end
                                 as colaborador_id,
                               r.categoria, a.destino, a.urgente, %2$s as status, a.criado_em,
                               case when :rh then r.motivo end as motivo,
                               r.acao_sugerida, ent.retorno, ent.respondido_em, ent.destinatarios
                        from public.alertas a
                        join public.relatos r on r.id = a.relato_id
                        join public.colaboradores c on c.id = r.colaborador_id
                        join public.categorias cat on cat.codigo = r.categoria
                        left join lateral (
                          select case when bool_or(e.resposta = 'CONVERSEI') then 'CONVERSEI'
                                      when bool_or(e.resposta = 'NAO_CONSEGUI') then 'NAO_CONSEGUI' end as retorno,
                                 coalesce(min(e.respondido_em) filter (where e.resposta = 'CONVERSEI'),
                                          min(e.respondido_em) filter (where e.resposta = 'NAO_CONSEGUI'))
                                   as respondido_em,
                                 string_agg(distinct p.nome, ', ') as destinatarios
                          from public.alerta_entregas e
                          join public.responsaveis p on p.id = e.responsavel_id
                          where e.alerta_id = a.id) ent on true
                        where %1$s
                          and (cast(:status as text) is null or %2$s = :status)
                          and (cast(:destino as text) is null or a.destino = :destino or a.destino = 'GERENTE_E_RH')
                          and (cast(:colaborador as uuid) is null or r.colaborador_id = :colaborador)
                        order by a.criado_em desc, a.id desc""".formatted(ALERTA_VISIVEL, STATUS_ALERTA)), acesso)
                .param("restritas", RESTRITAS)
                .param("limite24h", tempo(agora.minus(Duration.ofHours(24))))
                .param("status", status)
                .param("destino", destino)
                .param("colaborador", colaboradorId)
                .query((rs, n) -> new AlertaPainel(rs.getLong("id"), rs.getObject("colaborador_id", UUID.class),
                        rs.getString("categoria"), rs.getString("destino"), rs.getBoolean("urgente"),
                        rs.getString("status"), instante(rs, "criado_em"), rs.getString("motivo"),
                        rs.getString("acao_sugerida"), rs.getString("retorno"), instante(rs, "respondido_em"),
                        rs.getString("destinatarios")))
                .list();
    }

    @Override
    public List<RelatoPainel> relatos(Acesso acesso, String estado) {
        return escopo(jdbc.sql("""
                        select r.id, r.colaborador_id, c.loja, c.turno, r.categoria, r.estado, enc.urgente,
                               r.criado_em, r.texto, r.motivo, r.acao_sugerida
                        from public.relatos r
                        join public.colaboradores c on c.id = r.colaborador_id
                        join lateral (
                          select bool_or(a.urgente) as urgente
                          from public.alertas a
                          where a.relato_id = r.id
                            and (a.destino in ('RH', 'GERENTE_E_RH')
                                 or exists (select 1 from public.alerta_entregas e
                                            join public.responsaveis p on p.id = e.responsavel_id
                                            where e.alerta_id = a.id and p.papel = 'RH'))) enc
                          on enc.urgente is not null
                        where :rh and cast(:loja as text) is null
                          and (cast(:estado as text) is null or r.estado = :estado)
                        order by r.criado_em desc, r.id desc"""), acesso)
                .param("estado", estado)
                .query(RELATO)
                .list();
    }

    @Override
    public List<RelatoPainel> relatosBemEstar(Acesso acesso) {
        return escopo(jdbc.sql("""
                        select r.id, r.colaborador_id, c.loja, c.turno, r.categoria, r.estado,
                               coalesce((select bool_or(a.urgente) from public.alertas a where a.relato_id = r.id),
                                        false) as urgente,
                               r.criado_em, null::text as texto, r.motivo, r.acao_sugerida
                        from public.relatos r
                        join public.colaboradores c on c.id = r.colaborador_id
                        join public.categorias cat on cat.codigo = r.categoria
                        where :rh and %s
                          and r.categoria not in (:restritas)
                          and (cat.destino = 'AGREGADO'
                               or exists (select 1 from public.alertas a where a.relato_id = r.id))
                        order by r.criado_em desc, r.id desc""".formatted(COLABORADOR_VISIVEL)), acesso)
                .param("restritas", RESTRITAS)
                .query(RELATO)
                .list();
    }

    @Override
    public List<Contagem> distribuicao(Acesso acesso) {
        return escopo(jdbc.sql("""
                        select r.categoria, count(*) as quantidade
                        from public.relatos r
                        join public.colaboradores c on c.id = r.colaborador_id
                        join public.categorias cat on cat.codigo = r.categoria
                        where (:rh and (cat.destino = 'AGREGADO'
                                        or exists (select 1 from public.alertas a where a.relato_id = r.id)))
                           or (not :rh
                               and public.normalizar_loja(c.loja) = public.normalizar_loja(cast(:loja as text))
                               and r.categoria not in (:restritas)
                               and ((cat.destino = 'AGREGADO' and not r.sinais_graves)
                                    or (cat.destino in ('GERENTE', 'GERENTE_E_RH')
                                        and exists (select 1 from public.alertas a
                                                    where a.relato_id = r.id and not a.urgente
                                                      and a.destino in ('GERENTE', 'GERENTE_E_RH')))))
                        group by r.categoria
                        order by quantidade desc, r.categoria"""), acesso)
                .param("restritas", RESTRITAS)
                .query((rs, n) -> new Contagem(rs.getString("categoria"), rs.getLong("quantidade")))
                .list();
    }

    // RetencaoRepository

    @Override
    @Transactional
    public Apagados apagarTextosAntesDe(Instant limite) {
        int relatos = jdbc.sql("""
                        update public.relatos set texto = null, motivo = null
                        where criado_em < :limite and (texto is not null or motivo is not null)""")
                .param("limite", tempo(limite))
                .update();
        int mensagens = jdbc.sql("update public.mensagens set texto = null where criada_em < :limite and texto is not null")
                .param("limite", tempo(limite))
                .update();
        return new Apagados(relatos, mensagens);
    }

    /** Papel e loja SEMPRE do Acesso (acessos_dashboard); o GERENTE sem loja não vê nada. */
    private static JdbcClient.StatementSpec escopo(JdbcClient.StatementSpec sql, Acesso acesso) {
        return sql.param("rh", acesso.rh()).param("loja", acesso.lojaDoFiltro());
    }

    private static OffsetDateTime tempo(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
