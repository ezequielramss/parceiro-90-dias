package br.com.kallopolli.parceiro.classificacao;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Classificador dos testes: devolve o que o teste mandar, sem rede. Padrão: CANSACO, sem sinais graves. */
public class ClassificadorFalso implements ClassificadorRelato {

    public static final String MOTIVO = "MOTIVO-FICTICIO-DO-RELATO";
    public static final String ACAO = "Conversar sobre a escala da semana.";

    private volatile Function<String, Classificacao> regra = texto -> categoria("CANSACO");
    private final List<String> recebidos = new CopyOnWriteArrayList<>();

    public static Classificacao categoria(String codigo) {
        return new Classificacao(codigo, MOTIVO, ACAO, "alta", false, false);
    }

    public void responder(Function<String, Classificacao> regra) {
        this.regra = regra;
    }

    public void sempre(Classificacao classificacao) {
        this.regra = texto -> classificacao;
    }

    /** A partir daqui, qualquer chamada faz o teste falhar. */
    public void proibir() {
        this.regra = texto -> {
            throw new AssertionError("o classificador não deveria ter sido chamado");
        };
    }

    public void limpar() {
        recebidos.clear();
        regra = texto -> categoria("CANSACO");
    }

    public List<String> recebidos() {
        return List.copyOf(recebidos);
    }

    @Override
    public Classificacao classificar(String textoDoRelato) {
        recebidos.add(textoDoRelato);
        return regra.apply(textoDoRelato);
    }
}
