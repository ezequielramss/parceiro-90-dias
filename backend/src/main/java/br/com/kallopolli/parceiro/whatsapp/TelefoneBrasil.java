package br.com.kallopolli.parceiro.whatsapp;

import java.util.ArrayList;
import java.util.List;

/**
 * A Meta pode entregar celulares brasileiros sem o nono dígito (55 + DDD + 8 dígitos).
 * Gera as formas E.164 a comparar com o cadastro: com e sem o 9 depois do DDD.
 */
public final class TelefoneBrasil {

    private TelefoneBrasil() {}

    /** Candidatos em E.164 (com +), na ordem: o número como veio e, se for do Brasil, a outra forma. */
    public static List<String> candidatosE164(String digitos) {
        if (digitos == null || !digitos.matches("\\d{8,15}")) {
            return List.of();
        }
        List<String> candidatos = new ArrayList<>(2);
        candidatos.add("+" + digitos);
        if (digitos.startsWith("55")) {
            if (digitos.length() == 12) {
                candidatos.add("+" + digitos.substring(0, 4) + "9" + digitos.substring(4));
            } else if (digitos.length() == 13 && digitos.charAt(4) == '9') {
                candidatos.add("+" + digitos.substring(0, 4) + digitos.substring(5));
            }
        }
        return candidatos;
    }

    /** O número cadastrado (E.164) corresponde ao remetente da Meta, com ou sem o 9? */
    public static boolean mesmoNumero(String cadastradoE164, String digitosMeta) {
        return cadastradoE164 != null && candidatosE164(digitosMeta).contains(cadastradoE164);
    }
}
