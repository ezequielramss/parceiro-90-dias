package br.com.kallopolli.parceiro.jornada.jdbc;

import br.com.kallopolli.parceiro.jornada.AuditoriaRepository;
import br.com.kallopolli.parceiro.jornada.Colaborador;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.jornada.ConsentimentoRepository;
import br.com.kallopolli.parceiro.jornada.MensagemRepository;
import java.util.Optional;
import java.util.UUID;
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

    @Override
    public Optional<Colaborador> buscarAtivoPorTelefone(String telefoneE164) {
        return jdbc.sql("select id, telefone from public.colaboradores where telefone = :telefone and ativo")
                .param("telefone", telefoneE164)
                .query((rs, n) -> new Colaborador(rs.getObject("id", UUID.class), rs.getString("telefone")))
                .optional();
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
    public boolean registrarEntrada(String wamid, UUID colaboradorId, String tipo, String texto) {
        return inserirMensagem(wamid, colaboradorId, "ENTRADA", tipo, texto) == 1;
    }

    @Override
    public void registrarSaida(String wamid, UUID colaboradorId, String texto) {
        inserirMensagem(wamid, colaboradorId, "SAIDA", "text", texto);
    }

    private int inserirMensagem(String wamid, UUID colaboradorId, String direcao, String tipo, String texto) {
        return jdbc.sql("""
                        insert into public.mensagens (wamid, colaborador_id, direcao, tipo, texto)
                        values (:wamid, :colaborador, :direcao, :tipo, :texto)
                        on conflict (wamid) do nothing""")
                .param("wamid", wamid)
                .param("colaborador", colaboradorId)
                .param("direcao", direcao)
                .param("tipo", tipo)
                .param("texto", texto)
                .update();
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
