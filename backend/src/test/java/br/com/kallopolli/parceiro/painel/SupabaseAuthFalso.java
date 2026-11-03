package br.com.kallopolli.parceiro.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP local que faz o papel do Supabase Auth nos testes: login por senha e JWKS com um
 * par de chaves gerado aqui. Nada sai da máquina; e-mails, senhas e usuários são fictícios.
 */
public class SupabaseAuthFalso implements AutoCloseable {

    public static final String CHAVE_PUBLICAVEL_FALSA = "sb_publishable_FALSA-DE-TESTE-3e8d";

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Usuario(UUID id, String senha) {}

    private final HttpServer servidor;
    private final ECKey chave;
    private final ECKey outraChave;
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final AtomicInteger pedidosToken = new AtomicInteger();
    private final AtomicInteger pedidosJwks = new AtomicInteger();
    private volatile int statusToken = 200;
    private volatile String ultimaChaveRecebida;

    public SupabaseAuthFalso() throws Exception {
        chave = new ECKeyGenerator(Curve.P_256).keyID("chave-de-teste").generate();
        outraChave = new ECKeyGenerator(Curve.P_256).keyID("chave-de-teste").generate();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/auth/v1/.well-known/jwks.json", troca -> {
            pedidosJwks.incrementAndGet();
            responder(troca, 200, new JWKSet(chave.toPublicJWK()).toString());
        });
        servidor.createContext("/auth/v1/token", this::token);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public String emissor() {
        return url() + "/auth/v1";
    }

    public void cadastrar(UUID id, String email, String senha) {
        usuarios.put(email, new Usuario(id, senha));
    }

    /** Faz o /token responder com este status (ex.: 500). */
    public void responderToken(int status) {
        this.statusToken = status;
    }

    public int pedidosToken() {
        return pedidosToken.get();
    }

    public int pedidosJwks() {
        return pedidosJwks.get();
    }

    public String ultimaChaveRecebida() {
        return ultimaChaveRecebida;
    }

    public void limpar() {
        usuarios.clear();
        statusToken = 200;
        ultimaChaveRecebida = null;
    }

    // Tokens

    public String token(UUID usuario) {
        return assinar(chave, claims(usuario, emissor(), "authenticated", Instant.now().plus(Duration.ofHours(1))));
    }

    public String tokenExpirado(UUID usuario) {
        return assinar(chave, claims(usuario, emissor(), "authenticated", Instant.now().minus(Duration.ofMinutes(10))));
    }

    public String tokenDeOutroEmissor(UUID usuario) {
        return assinar(chave, claims(usuario, "https://outro-projeto.supabase.invalid/auth/v1", "authenticated",
                Instant.now().plus(Duration.ofHours(1))));
    }

    public String tokenComOutraAudience(UUID usuario) {
        return assinar(chave, claims(usuario, emissor(), "anon", Instant.now().plus(Duration.ofHours(1))));
    }

    /** Mesmo kid, chave privada diferente: a assinatura não confere. */
    public String tokenComAssinaturaErrada(UUID usuario) {
        return assinar(outraChave, claims(usuario, emissor(), "authenticated", Instant.now().plus(Duration.ofHours(1))));
    }

    private static JWTClaimsSet claims(UUID usuario, String emissor, String audience, Instant expira) {
        return new JWTClaimsSet.Builder()
                .subject(usuario.toString())
                .issuer(emissor)
                .audience(audience)
                .issueTime(Date.from(expira.minus(Duration.ofHours(1))))
                .expirationTime(Date.from(expira))
                .claim("role", "authenticated")
                // Papel e loja no token são ignorados: valem só os de acessos_dashboard.
                .claim("app_metadata", Map.of("papel", "RH", "loja", "loja b"))
                .build();
    }

    private static String assinar(ECKey chave, JWTClaimsSet claims) {
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT)
                    .keyID(chave.getKeyID()).build(), claims);
            jwt.sign(new ECDSASigner(chave));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void token(HttpExchange troca) throws IOException {
        pedidosToken.incrementAndGet();
        ultimaChaveRecebida = troca.getRequestHeaders().getFirst("apikey");
        String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (statusToken != 200) {
            responder(troca, statusToken, "{\"error\":\"falha do servidor falso\"}");
            return;
        }
        boolean metodoCerto = "POST".equals(troca.getRequestMethod())
                && "grant_type=password".equals(troca.getRequestURI().getQuery())
                && CHAVE_PUBLICAVEL_FALSA.equals(ultimaChaveRecebida);
        JsonNode no = JSON.readTree(corpo);
        Usuario usuario = usuarios.get(no.path("email").asText());
        if (!metodoCerto || usuario == null || !usuario.senha().equals(no.path("password").asText())) {
            responder(troca, 400, "{\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}");
            return;
        }
        responder(troca, 200, JSON.writeValueAsString(Map.of(
                "access_token", token(usuario.id()),
                "token_type", "bearer",
                "expires_in", 3600,
                "refresh_token", "refresh-falso",
                "user", Map.of("id", usuario.id().toString()))));
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
