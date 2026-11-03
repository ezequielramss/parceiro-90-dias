package br.com.kallopolli.parceiro.consentimento;

import java.text.Normalizer;
import java.util.Locale;

/** Normaliza respostas curtas: sem acento, minúsculas, sem pontuação nem espaços nas pontas. */
public final class TextoNormalizado {

    private TextoNormalizado() {}

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }
}
