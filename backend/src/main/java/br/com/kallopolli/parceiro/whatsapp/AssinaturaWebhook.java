package br.com.kallopolli.parceiro.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Valida X-Hub-Signature-256: "sha256=" + HMAC-SHA256 em hexadecimal do corpo bruto,
 * com o App Secret como chave. Comparação em tempo constante.
 */
@Component
public class AssinaturaWebhook {

    private static final String PREFIXO = "sha256=";
    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    public AssinaturaWebhook(WhatsAppProperties props) {
        this.chave = new SecretKeySpec(props.appSecret().getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    public boolean valida(byte[] corpo, String cabecalho) {
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            return false;
        }
        byte[] recebida;
        try {
            recebida = HexFormat.of().parseHex(cabecalho.substring(PREFIXO.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(calcular(corpo == null ? new byte[0] : corpo), recebida);
    }

    private byte[] calcular(byte[] corpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return mac.doFinal(corpo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
