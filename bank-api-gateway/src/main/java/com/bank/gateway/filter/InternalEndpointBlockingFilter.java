package com.bank.gateway.filter;

import com.bank.gateway.config.BankGatewayProperties;
import com.bank.gateway.security.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bloquea las rutas internas (por ejemplo {@code .../balance-operations}) para
 * que no puedan invocarse desde afuera a traves del gateway.
 *
 * <p>Esos endpoints solo los usa {@code bank-transaction-service}, que llama a
 * los servicios directamente por Eureka sin pasar por aqui. Se responde 403
 * con el mismo cuerpo de error que el resto del sistema.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalEndpointBlockingFilter implements GlobalFilter, Ordered {

    private final BankGatewayProperties bankGatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Corta la peticion si su ruta coincide con algun patron bloqueado.
     *
     * @param exchange intercambio HTTP en curso
     * @param chain    resto de la cadena de filtros
     * @return respuesta 403 o la continuacion de la cadena
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean blocked = bankGatewayProperties.getBlockedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (!blocked) {
            return chain.filter(exchange);
        }
        log.warn("Blocked external call to internal endpoint {}", path);
        return ErrorResponseWriter.write(exchange, HttpStatus.FORBIDDEN,
                "Internal endpoint not exposed by the gateway");
    }

    /**
     * Se ejecuta antes que el enrutamiento.
     *
     * @return orden del filtro
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
