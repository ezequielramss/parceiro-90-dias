package br.com.kallopolli.parceiro.alerta.jdbc;

import br.com.kallopolli.parceiro.alerta.AlertaEntregaRepository;
import br.com.kallopolli.parceiro.alerta.AlertaRepository;
import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.alerta.ResponsavelRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Responsáveis, alertas e entregas (migração 20261103120000_fluxo_etapa1.sql). */
@Repository
public class JdbcAlertaRepositorios implements ResponsavelRepository, AlertaRepository, AlertaEntregaRepository {

    private static final String COLUNAS = "id, nome, telefone, papel, loja";

    private static final RowMapper<Responsavel> RESPONSAVEL = (rs, n) -> new Responsavel(
            rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("telefone"),
            rs.getString("papel"), rs.getString("loja"));

    private final JdbcClient jdbc;

    public JdbcAlertaRepositorios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Responsavel> gerentesAtivosDaLoja(String loja) {
        return jdbc.sql("select " + COLUNAS + """
                         from public.responsaveis
                        where papel = 'GERENTE' and ativo
                          and public.normalizar_loja(loja) = public.normalizar_loja(:loja)""")
                .param("loja", loja)
                .query(RESPONSAVEL)
                .list();
    }

    @Override
    public List<Responsavel> rhAtivos() {
        return jdbc.sql("select " + COLUNAS + " from public.responsaveis where papel = 'RH' and ativo")
                .query(RESPONSAVEL)
                .list();
    }

    @Override
    public List<Responsavel> ativosPorTelefones(List<String> telefonesE164) {
        if (telefonesE164.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COLUNAS + " from public.responsaveis where ativo and telefone in (:telefones)")
                .param("telefones", telefonesE164)
                .query(RESPONSAVEL)
                .list();
    }

    @Override
    public List<Responsavel> responsaveisAtivos() {
        return jdbc.sql("select " + COLUNAS + " from public.responsaveis where ativo order by papel, nome, id")
                .query(RESPONSAVEL)
                .list();
    }

    @Override
    public Optional<Responsavel> buscarResponsavel(UUID id) {
        return jdbc.sql("select " + COLUNAS + " from public.responsaveis where id = :id")
                .param("id", id)
                .query(RESPONSAVEL)
                .optional();
    }

    @Override
    public long criar(long relatoId, String destino, boolean urgente) {
        return jdbc.sql("""
                        insert into public.alertas (relato_id, destino, urgente)
                        values (:relato, :destino, :urgente) returning id""")
                .param("relato", relatoId)
                .param("destino", destino)
                .param("urgente", urgente)
                .query(Long.class)
                .single();
    }

    @Override
    public void marcarAtendido(long alertaId) {
        jdbc.sql("update public.alertas set estado = 'ATENDIDO' where id = :id and estado <> 'ATENDIDO'")
                .param("id", alertaId)
                .update();
    }

    @Override
    public long criar(long alertaId, UUID responsavelId) {
        return jdbc.sql("""
                        insert into public.alerta_entregas (alerta_id, responsavel_id)
                        values (:alerta, :responsavel) returning id""")
                .param("alerta", alertaId)
                .param("responsavel", responsavelId)
                .query(Long.class)
                .single();
    }

    @Override
    public void registrarEnvio(long entregaId, String wamid) {
        jdbc.sql("update public.alerta_entregas set wamid_envio = :wamid where id = :id")
                .param("wamid", wamid)
                .param("id", entregaId)
                .update();
    }

    @Override
    public void marcarFalha(long entregaId) {
        jdbc.sql("update public.alerta_entregas set falha_envio = true where id = :id")
                .param("id", entregaId)
                .update();
    }

    @Override
    public Optional<Entrega> buscarEntrega(long entregaId) {
        return jdbc.sql("""
                        select id, alerta_id, responsavel_id, falha_envio, resposta
                        from public.alerta_entregas where id = :id""")
                .param("id", entregaId)
                .query((rs, n) -> new Entrega(rs.getLong("id"), rs.getLong("alerta_id"),
                        rs.getObject("responsavel_id", UUID.class), rs.getBoolean("falha_envio"),
                        rs.getString("resposta")))
                .optional();
    }

    @Override
    public Optional<Long> maisRecenteAberta(List<UUID> responsavelIds) {
        if (responsavelIds.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        select id from public.alerta_entregas
                        where responsavel_id in (:ids) and not falha_envio
                          and resposta is distinct from 'CONVERSEI'
                        order by enviado_em desc, id desc
                        limit 1""")
                .param("ids", responsavelIds)
                .query(Long.class)
                .optional();
    }

    @Override
    public void responder(long entregaId, String resposta) {
        jdbc.sql("update public.alerta_entregas set resposta = :resposta, respondido_em = now() where id = :id")
                .param("resposta", resposta)
                .param("id", entregaId)
                .update();
    }
}
