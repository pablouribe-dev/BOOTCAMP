package com.bank.transaction.client;

import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ResourceNotFoundException;
import com.bank.transaction.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * Aplica circuit breaker y time limiter de Resilience4j a cada llamada
 * WebClient hacia otro servicio.
 *
 * <p>Se usa una instancia de cada uno por servicio destino (el nombre de
 * Eureka), con los valores {@code default} del {@code application.yml} comun:
 * timeout de 2 segundos y apertura del circuito con 50% de fallos.</p>
 *
 * <p>Fallback: nunca se inventan datos. Si el circuito esta abierto, hay timeout
 * o el destino no acepta la conexion, se lanza
 * {@link ServiceUnavailableException} (HTTP 503).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteCallDecorator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    /**
     * Envuelve la llamada remota con time limiter y circuit breaker.
     *
     * <p>Las respuestas de negocio del destino (404 y 409) no cuentan como
     * fallo del circuito: el servicio respondio bien, solo rechazo la
     * operacion.</p>
     *
     * @param serviceName nombre del servicio destino en Eureka
     * @param call        llamada WebClient aun sin suscribir
     * @param <T>         tipo del cuerpo de la respuesta
     * @return la misma llamada, protegida
     */
    public <T> Mono<T> decorate(String serviceName, Mono<T> call) {
        CircuitBreaker circuitBreaker = circuitBreaker(serviceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(serviceName);
        return call
                .onErrorMap(WebClientRequestException.class, ex -> unavailable(serviceName,
                        serviceName + " is not available", ex))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(TimeoutException.class, ex -> unavailable(serviceName,
                        serviceName + " did not answer within the time limit", ex))
                .onErrorMap(CallNotPermittedException.class, ex -> unavailable(serviceName,
                        serviceName + " is not available (circuit breaker open)", ex));
    }

    /**
     * Obtiene el circuit breaker del destino, creado a partir de la
     * configuracion {@code default} pero ignorando las excepciones de negocio.
     *
     * @param serviceName nombre del servicio destino
     * @return circuit breaker del destino
     */
    private CircuitBreaker circuitBreaker(String serviceName) {
        CircuitBreakerConfig config = CircuitBreakerConfig.from(circuitBreakerRegistry.getDefaultConfig())
                .ignoreExceptions(ResourceNotFoundException.class, BusinessRuleException.class)
                .build();
        return circuitBreakerRegistry.circuitBreaker(serviceName, config);
    }

    /**
     * Registra el fallo y construye la excepcion que se responde como 503.
     *
     * @param serviceName nombre del servicio destino
     * @param message     mensaje para el cliente de la API
     * @param cause       causa original
     * @return excepcion de servicio no disponible
     */
    private ServiceUnavailableException unavailable(String serviceName, String message, Throwable cause) {
        log.error("Call to {} failed: {} ({})", serviceName, message, cause.getClass().getSimpleName());
        return new ServiceUnavailableException(message);
    }
}
