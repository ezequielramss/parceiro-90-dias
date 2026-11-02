package br.com.kallopolli.parceiro.consentimento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextoNormalizadoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "parar|parar",
            "PARAR|parar",
            "Párar!|parar",
            "'  PÁRAR.  '|parar",
            "Aceito!!|aceito",
            "'não quero parar'|nao quero parar",
            "|''"
    })
    void normaliza(String entrada, String esperado) {
        assertThat(TextoNormalizado.normalizar(entrada)).isEqualTo(esperado);
    }
}
