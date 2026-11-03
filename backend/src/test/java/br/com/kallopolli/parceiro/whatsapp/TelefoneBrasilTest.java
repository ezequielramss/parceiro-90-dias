package br.com.kallopolli.parceiro.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelefoneBrasilTest {

    @Test
    void celularSemONonoDigitoGeraAFormaComO9() {
        assertThat(TelefoneBrasil.candidatosE164("551199990404")).containsExactly("+551199990404", "+5511999990404");
    }

    @Test
    void celularComONonoDigitoGeraAFormaSemO9() {
        assertThat(TelefoneBrasil.candidatosE164("5511999990404")).containsExactly("+5511999990404", "+551199990404");
    }

    @Test
    void numeroDeOutroPaisFicaComoVeio() {
        assertThat(TelefoneBrasil.candidatosE164("14155550100")).containsExactly("+14155550100");
    }

    @Test
    void entradaInvalidaNaoGeraCandidato() {
        assertThat(TelefoneBrasil.candidatosE164(null)).isEmpty();
        assertThat(TelefoneBrasil.candidatosE164("+5511999990404")).isEmpty();
        assertThat(TelefoneBrasil.candidatosE164("abc")).isEmpty();
    }

    @Test
    void mesmoNumeroComparaComESemO9() {
        assertThat(TelefoneBrasil.mesmoNumero("+5511999990404", "551199990404")).isTrue();
        assertThat(TelefoneBrasil.mesmoNumero("+5511999990404", "5511999990404")).isTrue();
        assertThat(TelefoneBrasil.mesmoNumero("+5511999990404", "5511999990405")).isFalse();
        assertThat(TelefoneBrasil.mesmoNumero(null, "5511999990404")).isFalse();
    }
}
