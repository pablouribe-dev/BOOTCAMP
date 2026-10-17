package com.bank.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.auth.config.JwtProperties;
import com.bank.auth.model.Role;
import com.bank.auth.model.User;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-with-at-least-32-bytes!!";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(Duration.ofMinutes(60));
        return properties;
    }

    @Test
    void issuesAnHs256TokenWithTheExpectedClaims() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(properties(SECRET), clock);
        User user = User.builder().id("u1").username("ana").roles(List.of(Role.USER)).customerId("c1")
                .phoneNumber("999").build();

        SignedJWT jwt = SignedJWT.parse(provider.createToken(user));

        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("u1");
        assertThat(claims.getStringClaim("username")).isEqualTo("ana");
        assertThat(claims.getStringListClaim("roles")).containsExactly("USER");
        assertThat(claims.getStringClaim("customerId")).isEqualTo("c1");
        assertThat(claims.getStringClaim("phoneNumber")).isEqualTo("999");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(Instant.parse("2026-10-16T12:00:00Z"));
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(Instant.parse("2026-10-16T13:00:00Z"));
        assertThat(provider.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void refusesToStartWithAMissingOrShortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(properties(null), clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(properties("too-short"), clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
