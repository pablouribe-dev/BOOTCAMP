package com.bank.transaction.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Prueba el circuit breaker y el time limiter con registros reales.
 */
class RemoteCallDecoratorTest {

    private static final String SERVICE = "bank-customer-service";

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RemoteCallDecorator decorator;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(200)).build());
        decorator = new RemoteCallDecorator(circuitBreakerRegistry, timeLimiterRegistry);
    }

    private WebClientRequestException connectionRefused() {
        return new WebClientRequestException(new java.net.ConnectException("Connection refused"), HttpMethod.GET,
                URI.create("http://bank-customer-service/customers/1"), new HttpHeaders());
    }

    @Test
    void successfulCallPassesThrough() {
        StepVerifier.create(decorator.decorate(SERVICE, Mono.just("ok"))).expectNext("ok").verifyComplete();
    }

    @Test
    void connectionErrorBecomes503() {
        StepVerifier.create(decorator.decorate(SERVICE, Mono.error(connectionRefused())))
                .expectErrorMatches(error -> error instanceof ServiceUnavailableException
                        && error.getMessage().equals(SERVICE + " is not available"))
                .verify();
    }

    @Test
    void slowCallTimesOutAs503() {
        StepVerifier.create(decorator.decorate(SERVICE, Mono.just("late").delayElement(Duration.ofSeconds(2))))
                .expectErrorMatches(error -> error instanceof ServiceUnavailableException
                        && error.getMessage().contains("time limit"))
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void circuitOpensAfterFailuresAndThenFailsFastWithoutCallingTheService() {
        decorator.decorate(SERVICE, Mono.error(connectionRefused())).onErrorResume(e -> Mono.empty()).block();
        decorator.decorate(SERVICE, Mono.error(connectionRefused())).onErrorResume(e -> Mono.empty()).block();

        assertThat(circuitBreakerRegistry.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        boolean[] called = {false};
        Mono<String> call = Mono.fromCallable(() -> {
            called[0] = true;
            return "ok";
        });
        StepVerifier.create(decorator.decorate(SERVICE, call))
                .expectErrorMatches(error -> error instanceof ServiceUnavailableException
                        && error.getMessage().contains("circuit breaker open"))
                .verify();
        assertThat(called[0]).isFalse();
    }

    @Test
    void businessErrorsDoNotOpenTheCircuit() {
        for (int i = 0; i < 4; i++) {
            StepVerifier.create(decorator.decorate(SERVICE, Mono.error(new BusinessRuleException("409"))))
                    .expectError(BusinessRuleException.class).verify();
        }
        assertThat(circuitBreakerRegistry.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
