package com.bank.account.client;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.exception.ResourceNotFoundException;
import com.bank.account.exception.ServiceUnavailableException;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente HTTP hacia {@code bank-customer-service}, resuelto por nombre en
 * Eureka y protegido con circuit breaker y timeout.
 */
@Slf4j
@Component
public class CustomerClient {

    /** Nombre del servicio destino en Eureka y de su circuit breaker. */
    static final String SERVICE_NAME = "bank-customer-service";

    private final WebClient customerWebClient;
    private final RemoteCallDecorator remoteCallDecorator;

    /**
     * Inyeccion por constructor.
     *
     * @param customerWebClient   cliente HTTP balanceado hacia el servicio de clientes
     * @param remoteCallDecorator decorador de Resilience4j
     */
    public CustomerClient(WebClient customerWebClient, RemoteCallDecorator remoteCallDecorator) {
        this.customerWebClient = customerWebClient;
        this.remoteCallDecorator = remoteCallDecorator;
    }

    /**
     * Recupera un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return los datos del cliente
     */
    public Single<CustomerDto> findById(String customerId) {
        log.debug("Requesting customer id={} to {}", customerId, SERVICE_NAME);
        Mono<CustomerDto> call = customerWebClient.get()
                .uri("/customers/{id}", customerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("{} answered {} for customer id={}", SERVICE_NAME, response.statusCode(), customerId);
                    return Mono.error(new ResourceNotFoundException("Customer not found with id " + customerId));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("{} answered {} for customer id={}", SERVICE_NAME, response.statusCode(), customerId);
                    return Mono.error(new ServiceUnavailableException(SERVICE_NAME + " is not available"));
                })
                .bodyToMono(CustomerDto.class);
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }
}
