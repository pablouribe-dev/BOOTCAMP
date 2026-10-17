package com.bank.account.client;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.exception.ServiceUnavailableException;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente HTTP hacia {@code bank-credit-card-service}, resuelto por nombre en
 * Eureka y protegido con circuit breaker y timeout.
 *
 * <p>Lo usan las reglas VIP y PYME, que exigen que el cliente tenga al menos
 * una tarjeta de credito al abrir la cuenta.</p>
 */
@Slf4j
@Component
public class CreditCardClient {

    /** Nombre del servicio destino en Eureka y de su circuit breaker. */
    static final String SERVICE_NAME = "bank-credit-card-service";

    private final WebClient creditCardWebClient;
    private final RemoteCallDecorator remoteCallDecorator;

    /**
     * Inyeccion por constructor.
     *
     * @param creditCardWebClient cliente HTTP balanceado hacia el servicio de tarjetas
     * @param remoteCallDecorator decorador de Resilience4j
     */
    public CreditCardClient(WebClient creditCardWebClient, RemoteCallDecorator remoteCallDecorator) {
        this.creditCardWebClient = creditCardWebClient;
        this.remoteCallDecorator = remoteCallDecorator;
    }

    /**
     * Indica si el cliente tiene al menos una tarjeta de credito, consultando
     * {@code GET /credit-cards?customerId=}.
     *
     * @param customerId identificador del cliente
     * @return {@code true} si tiene una o mas tarjetas
     */
    public Single<Boolean> hasCreditCard(String customerId) {
        log.debug("Checking credit cards of customer id={} in {}", customerId, SERVICE_NAME);
        Mono<Boolean> call = creditCardWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/credit-cards").queryParam("customerId", customerId).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("{} answered {} listing cards of customer id={}",
                            SERVICE_NAME, response.statusCode(), customerId);
                    return Mono.error(new ServiceUnavailableException(SERVICE_NAME + " is not available"));
                })
                .bodyToFlux(CreditCardSummaryDto.class)
                .hasElements();
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }
}
