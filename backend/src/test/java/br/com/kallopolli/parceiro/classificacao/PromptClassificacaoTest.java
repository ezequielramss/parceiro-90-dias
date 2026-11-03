package br.com.kallopolli.parceiro.classificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** O prompt e o esquema vêm de ia/classificacao (fonte única) e chegam ao classpath pelo Maven. */
class PromptClassificacaoTest {

    @ParameterizedTest
    @ValueSource(strings = {PromptClassificacao.PROMPT, PromptClassificacao.ESQUEMA})
    void arquivoExisteNoClasspathENaoEstaVazio(String caminho) {
        assertThat(PromptClassificacao.class.getClassLoader().getResource(caminho)).as(caminho).isNotNull();
        assertThat(PromptClassificacao.ler(caminho)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prompt.md", "saida.schema.json"})
    void classpathTemOMesmoConteudoDaPastaIa(String arquivo) throws IOException {
        String fonte = Files.readString(Path.of("..", "ia", "classificacao", arquivo), StandardCharsets.UTF_8);
        assertThat(PromptClassificacao.ler("classificacao/" + arquivo).replace("\r\n", "\n"))
                .isEqualTo(fonte.replace("\r\n", "\n"));
    }

    @Test
    void esquemaTemAsDozeCategorias() {
        var prompt = new PromptClassificacao(new ObjectMapper());
        assertThat(prompt.categorias()).hasSize(12).contains("ASSEDIO", "ELOGIO", "NAO_CLASSIFICADO");
        assertThat(prompt.esquema().path("required")).hasSize(5);
    }

    @Test
    void arquivoAusenteImpedeASubida() {
        assertThatThrownBy(() -> PromptClassificacao.ler("classificacao/nao-existe.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ausente");
    }
}
