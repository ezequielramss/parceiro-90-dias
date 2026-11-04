package br.com.kallopolli.parceiro.painel;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Confere o token de acesso do Supabase Auth: assinatura pelas chaves públicas do JWKS do projeto
 * (SUPABASE_URL/auth/v1/.well-known/jwks.json, em cache), emissor, validade e audience
 * "authenticated". Só aceita chaves assimétricas (ES256 e RS256); o segredo HS256 legado não
 * aparece no JWKS e nunca é aceito.
 */
@Component
class VerificadorToken {

    /** As chaves do Supabase não puderam ser lidas; não é culpa do token. */
    static class ChavesIndisponiveis extends RuntimeException {
        ChavesIndisponiveis() {
            super("JWKS do Supabase indisponível");
        }
    }

    private static final int LIMITE_JWKS_BYTES = 64 * 1024;
    private static final long ESPERA_RENOVACAO_MS = 15_000;

    private final PainelProperties props;
    private volatile ConfigurableJWTProcessor<SecurityContext> processador;

    VerificadorToken(PainelProperties props) {
        this.props = props;
    }

    /** O sub (id do usuário no Supabase Auth) de um token válido; vazio para qualquer token inválido. */
    Optional<UUID> usuario(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processador().process(token, null);
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (KeySourceException e) {
            throw new ChavesIndisponiveis();
        } catch (Exception e) {
            // Assinatura errada, vencido, outro emissor, outra audience, sub que não é UUID...
            return Optional.empty();
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> processador() throws MalformedURLException {
        var atual = processador;
        if (atual == null) {
            synchronized (this) {
                atual = processador;
                if (atual == null) {
                    atual = criar();
                    processador = atual;
                }
            }
        }
        return atual;
    }

    private ConfigurableJWTProcessor<SecurityContext> criar() throws MalformedURLException {
        var leitor = new DefaultResourceRetriever((int) props.connectTimeout().toMillis(),
                (int) props.readTimeout().toMillis(), LIMITE_JWKS_BYTES);
        JWKSource<SecurityContext> chaves = JWKSourceBuilder
                .create(URI.create(props.urlBase() + "/auth/v1/.well-known/jwks.json").toURL(), leitor)
                .cache(props.jwksCache().toMillis(), ESPERA_RENOVACAO_MS)
                .build();

        var p = new DefaultJWTProcessor<SecurityContext>();
        p.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null));
        p.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.ES256, JWSAlgorithm.RS256), chaves));
        p.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                "authenticated",
                new JWTClaimsSet.Builder().issuer(props.emissor()).build(),
                Set.of("sub", "exp", "iat")));
        return p;
    }
}
