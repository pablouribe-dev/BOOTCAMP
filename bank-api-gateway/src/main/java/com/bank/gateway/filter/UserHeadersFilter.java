package com.bank.gateway.filter;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propaga la identidad del token a los microservicios.
 *
 * <p>Primero elimina de la peticion entrante cualquier cabecera
 * {@code X-User-*} (y {@code X-Customer-Id}), para que el cliente no pueda
 * falsificarlas. Luego, si la peticion trae un JWT valido, agrega
 * {@code X-User-Id}, {@code X-User-Roles}, {@code X-Customer-Id} y
 * {@code X-User-Phone} tomadas del token. Son informativas.</p>
 */
@Slf4j
@Component
public class UserHeadersFilter implements GlobalFilter, Ordered {

    /** Id del usuario ({@code sub}). */
    public static final String USER_ID = "X-User-Id";

    /** Roles separados por coma. */
    public static final String USER_ROLES = "X-User-Roles";

    /** Cliente asociado al usuario. */
    public static final String CUSTOMER_ID = "X-Customer-Id";

    /** Celular del usuario. */
    public static final String USER_PHONE = "X-User-Phone";

    /**
     * Limpia y agrega las cabeceras de identidad.
     *
     * @param exchange intercambio HTTP en curso
     * @param chain    resto de la cadena
     * @return continuacion de la cadena
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> ((JwtAuthenticationToken) principal).getToken())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(jwt -> chain.filter(exchange.mutate().request(rewrite(exchange.getRequest(), jwt)).build()));
    }

    /**
     * Despues del bloqueo de endpoints internos y antes del enrutamiento.
     *
     * @return orden del filtro
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * Arma la peticion sin las cabeceras de identidad del cliente y con las del token.
     *
     * @param request peticion original
     * @param jwt     token validado, si hay
     * @return peticion reescrita
     */
    private ServerHttpRequest rewrite(ServerHttpRequest request, Optional<Jwt> jwt) {
        return request.mutate().headers(headers -> {
            List<String> forged = headers.keySet().stream()
                    .filter(name -> name.regionMatches(true, 0, "X-User-", 0, 7) || CUSTOMER_ID.equalsIgnoreCase(name))
                    .toList();
            if (!forged.isEmpty()) {
                log.warn("Removing client-sent identity headers {}", forged);
            }
            forged.forEach(headers::remove);
            jwt.ifPresent(token -> addIdentity(headers, token));
        }).build();
    }

    /**
     * Copia los claims del token a las cabeceras.
     *
     * @param headers cabeceras a completar
     * @param token   token validado
     */
    private void addIdentity(HttpHeaders headers, Jwt token) {
        headers.set(USER_ID, token.getSubject());
        Optional.ofNullable(token.getClaimAsStringList("roles"))
                .ifPresent(roles -> headers.set(USER_ROLES, String.join(",", roles)));
        Optional.ofNullable(token.getClaimAsString("customerId")).ifPresent(id -> headers.set(CUSTOMER_ID, id));
        Optional.ofNullable(token.getClaimAsString("phoneNumber")).ifPresent(phone -> headers.set(USER_PHONE, phone));
    }
}
