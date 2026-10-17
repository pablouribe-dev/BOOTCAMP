package com.bank.transaction.client;

import com.bank.transaction.adapter.ReactorRxAdapter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente HTTP hacia {@code bank-credit-service}, resuelto por nombre en
 * Eureka y protegido con circuit breaker y timeout.
 */
@Slf4j
@Component
public class CreditClient {

    /** Nombre del servicio destino en Eureka y de su circuit breaker. */
    static final String SERVICE_NAME = "bank-credit-service";

    private static final String PRODUCT_LABEL = "Credit";

    private final WebClient creditWebClient;
    private final RemoteCallDecorator remoteCallDecorator;

    /**
     * Inyeccion por constructor.
     *
     * @param creditWebClient     cliente HTTP balanceado hacia el servicio de creditos
     * @param remoteCallDecorator decorador de Resilience4j
     */
    public CreditClient(WebClient creditWebClient, RemoteCallDecorator remoteCallDecorator) {
        this.creditWebClient = creditWebClient;
        this.remoteCallDecorator = remoteCallDecorator;
    }

    /**
     * Recupera un credito por su identificador.
     *
     * @param creditId identificador del credito
     * @return los datos del credito
     */
    public Single<CreditDto> findById(String creditId) {
        log.debug("Requesting credit id={} to {}", creditId, SERVICE_NAME);
        Mono<CreditDto> call = creditWebClient.get()
                .uri("/credits/{id}", creditId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, creditId, SERVICE_NAME))
                .bodyToMono(CreditDto.class);
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }

    /**
     * Pide al servicio de creditos que aplique un pago.
     *
     * <p>Si el pago supera el saldo pendiente, el servicio responde 409 y aqui
     * se traduce a {@code BusinessRuleException}, de modo que el movimiento
     * nunca se registra.</p>
     *
     * @param creditId identificador del credito
     * @param request  operacion e importe
     * @return completable que termina cuando el saldo fue actualizado
     */
    public Completable applyBalanceOperation(String creditId, CreditOperationRequest request) {
        log.debug("Requesting {} of {} on credit id={} to {}",
                request.getOperation(), request.getAmount(), creditId, SERVICE_NAME);
        Mono<Void> call = creditWebClient.post()
                .uri("/credits/{id}/balance-operations", creditId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, creditId, SERVICE_NAME))
                .bodyToMono(Void.class);
        return ReactorRxAdapter.monoToCompletable(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }
}
