package br.com.kallopolli.parceiro.painel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Erros do painel no formato do contrato: {"erro":{"codigo":"...","mensagem":"..."}}. */
final class RespostasPainel {

    static final String LOGIN_RECUSADO = "E-mail ou senha incorretos.";
    static final String SESSAO_EXPIRADA = "Sua sessão expirou. Entre novamente para continuar.";
    static final String SEM_ACESSO = "Seu usuário não tem acesso ao painel.";
    static final String SO_RH = "Esta área é exclusiva do RH.";
    static final String NAO_ENCONTRADO = "Registro não encontrado.";
    static final String NAO_CONFIGURADO =
            "O painel não está configurado neste servidor (SUPABASE_URL e SUPABASE_PUBLISHABLE_KEY).";
    static final String AUTH_INDISPONIVEL = "Não foi possível entrar agora. Tente novamente em alguns minutos.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private RespostasPainel() {}

    static Map<String, Object> corpo(String codigo, String mensagem) {
        Map<String, String> erro = new LinkedHashMap<>();
        erro.put("codigo", codigo);
        erro.put("mensagem", mensagem);
        return Map.of("erro", erro);
    }

    static ResponseEntity<Object> erro(HttpStatus status, String codigo, String mensagem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(corpo(codigo, mensagem));
    }

    /** Para o filtro, que responde antes do Spring MVC. */
    static void escrever(HttpServletResponse resposta, HttpStatus status, String codigo, String mensagem)
            throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getOutputStream().write(JSON.writeValueAsBytes(corpo(codigo, mensagem)));
    }
}
