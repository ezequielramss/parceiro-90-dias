package br.com.kallopolli.parceiro.classificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Prompt e esquema da classificação, lidos do classpath. A fonte única é ia/classificacao/
 * (prompt.md e saida.schema.json); o Maven copia os dois para classificacao/ no build.
 * Se algum faltar ou estiver vazio, a aplicação não sobe.
 */
@Component
public class PromptClassificacao {

    static final String PROMPT = "classificacao/prompt.md";
    static final String ESQUEMA = "classificacao/saida.schema.json";

    private final String prompt;
    private final JsonNode esquema;
    private final Set<String> categorias;

    public PromptClassificacao(ObjectMapper objectMapper) {
        this.prompt = ler(PROMPT);
        try {
            this.esquema = objectMapper.readTree(ler(ESQUEMA));
        } catch (IOException e) {
            throw new IllegalStateException("esquema da classificação inválido: " + ESQUEMA, e);
        }
        this.categorias = new LinkedHashSet<>();
        esquema.path("properties").path("categoria").path("enum").forEach(c -> categorias.add(c.asText()));
        if (categorias.isEmpty()) {
            throw new IllegalStateException("esquema sem a lista de categorias: " + ESQUEMA);
        }
    }

    public String prompt() {
        return prompt;
    }

    public JsonNode esquema() {
        return esquema;
    }

    /** As 12 categorias do esquema, na ordem da taxonomia. */
    public Set<String> categorias() {
        return Set.copyOf(categorias);
    }

    static String ler(String caminho) {
        try (InputStream in = PromptClassificacao.class.getClassLoader().getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("arquivo da classificação ausente no classpath: " + caminho);
            }
            String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (conteudo.isBlank()) {
                throw new IllegalStateException("arquivo da classificação vazio: " + caminho);
            }
            return conteudo;
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler " + caminho, e);
        }
    }
}
