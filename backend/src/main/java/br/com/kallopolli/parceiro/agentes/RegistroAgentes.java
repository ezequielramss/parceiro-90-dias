package br.com.kallopolli.parceiro.agentes;

import br.com.kallopolli.parceiro.consentimento.TextoNormalizado;
import br.com.kallopolli.parceiro.conversa.TextosConversa;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Recebe todos os agentes pelo Spring, filtra os ativos (parceiro.agentes.&lt;codigo&gt;.ativo,
 * padrão true), ordena e monta o menu. Dois agentes com o mesmo código derrubam a subida.
 */
@Component
public class RegistroAgentes {

    private final List<Agente> ativos;

    public RegistroAgentes(List<Agente> agentes, Environment ambiente) {
        Map<String, Agente> porCodigo = new HashMap<>();
        for (Agente agente : agentes) {
            Agente outro = porCodigo.put(agente.codigo(), agente);
            if (outro != null) {
                throw new IllegalStateException("Dois agentes com o mesmo código " + agente.codigo() + ": "
                        + outro.getClass().getSimpleName() + " e " + agente.getClass().getSimpleName()
                        + ". O código identifica o agente em conversa_estado e precisa ser único.");
            }
        }
        this.ativos = agentes.stream()
                .filter(Agente::ativo)
                .filter(a -> ambiente.getProperty(propriedade(a), Boolean.class, true))
                .sorted(Comparator.comparingInt(Agente::ordem).thenComparing(Agente::codigo))
                .toList();
    }

    private static String propriedade(Agente agente) {
        return "parceiro.agentes." + agente.codigo().toLowerCase(Locale.ROOT) + ".ativo";
    }

    public List<Agente> ativos() {
        return ativos;
    }

    /** O menu completo: lista numerada na ordem dos agentes, com o aviso de urgência se o Relato estiver ativo. */
    public String menu() {
        return ativos.isEmpty() ? TextosConversa.menuVazio() : comAvisoDeUrgencia(TextosConversa.menu(linhas()));
    }

    /** O menu curto, para quem digitou uma opção que não existe. */
    public String menuCurto() {
        return ativos.isEmpty() ? TextosConversa.menuVazio() : comAvisoDeUrgencia(TextosConversa.menuCurto(linhas()));
    }

    /** O aviso para texto longo fora de um agente. Sem o Relato ativo, não existe: a pessoa recebe o menu. */
    public Optional<String> avisoTextoLongo() {
        return numeroDoRelato().stream().mapToObj(TextosConversa::avisoTextoLongo).findFirst();
    }

    private String comAvisoDeUrgencia(String menu) {
        return numeroDoRelato().stream()
                .mapToObj(numero -> menu + "\n\n" + TextosConversa.avisoUrgencia(numero))
                .findFirst()
                .orElse(menu);
    }

    /** O número da opção de relato no menu: depende da ordem dos agentes ativos. */
    private OptionalInt numeroDoRelato() {
        for (int i = 0; i < ativos.size(); i++) {
            if (AgenteRelato.CODIGO.equals(ativos.get(i).codigo())) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    private List<String> linhas() {
        List<String> linhas = new ArrayList<>();
        for (int i = 0; i < ativos.size(); i++) {
            linhas.add((i + 1) + " - " + ativos.get(i).nome());
        }
        return linhas;
    }

    /** A opção pelo número ou pelo nome, ignorando maiúsculas, acentos e pontuação. */
    public Optional<Agente> escolher(String texto) {
        String t = TextoNormalizado.normalizar(texto);
        if (t.matches("\\d{1,2}")) {
            int numero = Integer.parseInt(t);
            return numero >= 1 && numero <= ativos.size() ? Optional.of(ativos.get(numero - 1)) : Optional.empty();
        }
        return ativos.stream()
                .filter(a -> a.nomesAceitos().stream().map(TextoNormalizado::normalizar).anyMatch(t::equals))
                .findFirst();
    }

    /** O texto é um número, mas nenhuma opção tem esse número. */
    public boolean numeroInvalido(String texto) {
        return TextoNormalizado.normalizar(texto).matches("\\d+") && escolher(texto).isEmpty();
    }

    public Optional<Agente> porCodigo(String codigo) {
        return ativos.stream().filter(a -> a.codigo().equals(codigo)).findFirst();
    }
}
