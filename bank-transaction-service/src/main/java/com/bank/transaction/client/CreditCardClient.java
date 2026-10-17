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
 * Cliente HTTP hacia {@code bank-credit-card-service}, resuelto por nombre en
 * Eureka y protegido con circuit breaker y timeout.
 */
@Slf4j
@Component
public class CreditCardClient {

    /** Nombre del servicio destino en Eureka y de su circuit breaker. */
    static final String SERVICE_NAME = "bank-credit-card-service";

    private static final String PRODUCT_LABEL = "Credit card";

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
     * Recupera una tarjeta de credito por su identificador.
     *
     * @param creditCardId identificador de la tarjeta
     * @return los datos de la tarjeta
     */
    public Single<CreditCardDto> findById(String creditCardId) {
        log.debug("Requesting credit card id={} to {}", creditCardId, SERVICE_NAME);
        Mono<CreditCardDto> call = creditCardWebClient.get()
                .uri("/credit-cards/{id}", creditCardId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, creditCardId, SERVICE_NAME))
                .bodyToMono(CreditCardDto.class);
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }

    /**
     * Pide al servicio de tarjetas que aplique un consumo o un pago.
     *
     * <p>Si el consumo excede la linea disponible o el pago supera el importe
     * consumido, el servicio responde 409 y aqui se traduce a
     * {@code BusinessRuleException}, de modo que el movimiento nunca se
     * registra.</p>
     *
     * @param creditCardId identificador de la tarjeta
     * @param request      operacion e importe
     * @return completable que termina cuando el consumo fue actualizado
     */
    public Completable applyBalanceOperation(String creditCardId, CardOperationRequest request) {
        log.debug("Requesting {} of {} on credit card id={} to {}",
                request.getOperation(), request.getAmount(), creditCardId, SERVICE_NAME);
        Mono<Void> call = creditCardWebClient.post()
                .uri("/credit-cards/{id}/balance-operations", creditCardId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, creditCardId, SERVICE_NAME))
                .bodyToMono(Void.class);
        return ReactorRxAdapter.monoToCompletable(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }
}
