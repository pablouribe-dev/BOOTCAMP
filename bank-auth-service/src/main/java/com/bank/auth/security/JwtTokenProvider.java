package com.bank.auth.security;

import com.bank.auth.config.JwtProperties;
import com.bank.auth.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Emite los JWT HS256 del sistema.
 *
 * <p>Claims: {@code sub} (userId), {@code username}, {@code roles},
 * {@code customerId}, {@code phoneNumber}, {@code iat} y {@code exp}. El
 * gateway valida firma y expiracion con el mismo secreto.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** Largo minimo del secreto para HS256 (256 bits). */
    static final int MIN_SECRET_BYTES = 32;

    private final MACSigner signer;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * Valida el secreto al arrancar: si falta o es corto, el servicio no levanta.
     *
     * @param jwtProperties secreto y duracion
     * @param clock         reloj
     * @throws IllegalStateException si el secreto tiene menos de 32 bytes
     */
    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and have at least " + MIN_SECRET_BYTES + " bytes");
        }
        try {
            this.signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Invalid JWT secret", ex);
        }
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * Firma un token para el usuario.
     *
     * @param user usuario autenticado
     * @return token serializado
     */
    public String createToken(User user) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .claim("customerId", user.getCustomerId())
                .claim("phoneNumber", user.getPhoneNumber())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(jwtProperties.getExpiration())))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign the JWT", ex);
        }
        log.debug("Token issued for user {} valid until {}", user.getUsername(), claims.getExpirationTime());
        return jwt.serialize();
    }

    /**
     * Duracion de los tokens en segundos ({@code expiresIn} de la respuesta).
     *
     * @return segundos de validez
     */
    public long expiresInSeconds() {
        return jwtProperties.getExpiration().toSeconds();
    }
}
