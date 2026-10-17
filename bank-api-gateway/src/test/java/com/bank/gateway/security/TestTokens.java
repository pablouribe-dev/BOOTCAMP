package com.bank.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Genera tokens HS256 como los de {@code bank-auth-service}, para los tests.
 */
final class TestTokens {

    static final String SECRET = "test-secret-with-at-least-32-bytes!!";

    private TestTokens() {
    }

    static String token(String secret, Duration validFor, String... roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("u1")
                .claim("username", "ana")
                .claim("roles", List.of(roles))
                .claim("customerId", "c1")
                .claim("phoneNumber", "999111222")
                .issueTime(Date.from(now.minus(Duration.ofHours(2))))
                .expirationTime(Date.from(now.plus(validFor)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    static String valid(String... roles) throws Exception {
        return token(SECRET, Duration.ofMinutes(30), roles);
    }
}
