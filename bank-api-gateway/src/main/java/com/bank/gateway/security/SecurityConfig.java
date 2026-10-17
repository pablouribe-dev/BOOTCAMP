package com.bank.gateway.security;

import com.bank.gateway.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Seguridad del gateway (Parte III): valida el JWT y aplica los roles.
 *
 * <ul>
 *   <li>Publicos: {@code POST /auth/login}, {@code POST /auth/register} y health.</li>
 *   <li>{@code /auth/users/**}, {@code /admin/**} y el resto de Actuator: {@code ADMIN}.</li>
 *   <li>{@code PUT} y {@code DELETE}: {@code ADMIN}.</li>
 *   <li>{@code GET} y {@code POST}: {@code USER} o {@code ADMIN}.</li>
 * </ul>
 *
 * <p>Sin token o con token invalido o vencido: 401. Rol insuficiente: 403.
 * Sin sesion: cada peticion se valida por su token.</p>
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Largo minimo del secreto para HS256 (256 bits). */
    static final int MIN_SECRET_BYTES = 32;

    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";

    /**
     * Cadena de filtros de seguridad.
     *
     * @param http       configurador reactivo de Spring Security
     * @param jwtDecoder decodificador de tokens
     * @return cadena de filtros
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/auth/users", "/auth/users/**", "/admin/**", "/actuator/**").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/**").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.DELETE, "/**").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.GET, "/**").hasAnyRole(USER, ADMIN)
                        .pathMatchers(HttpMethod.POST, "/**").hasAnyRole(USER, ADMIN)
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(authenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    /**
     * Decodificador HS256 con el secreto compartido con {@code bank-auth-service}.
     * Valida firma y expiracion.
     *
     * @param jwtProperties secreto
     * @return decodificador reactivo
     * @throws IllegalStateException si el secreto falta o tiene menos de 32 bytes
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and have at least " + MIN_SECRET_BYTES + " bytes");
        }
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Convierte el claim {@code roles} en autoridades {@code ROLE_*}.
     *
     * @return conversor reactivo
     */
    static ReactiveJwtAuthenticationConverterAdapter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("username");
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /**
     * Respuesta 401 con el cuerpo de error uniforme.
     *
     * @return punto de entrada de autenticacion
     */
    static ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            log.warn("401 on {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return ErrorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing, invalid or expired token");
        };
    }

    /**
     * Respuesta 403 con el cuerpo de error uniforme.
     *
     * @return manejador de acceso denegado
     */
    static ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, ex) -> {
            log.warn("403 on {} {}: insufficient role", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath());
            return ErrorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, "Insufficient role for this operation");
        };
    }
}
