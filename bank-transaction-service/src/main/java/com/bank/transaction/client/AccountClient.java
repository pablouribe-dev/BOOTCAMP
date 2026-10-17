package com.bank.transaction.client;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.exception.ResourceNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente HTTP hacia {@code bank-account-service}, resuelto por nombre en
 * Eureka y protegido con circuit breaker y timeout.
 */
@Slf4j
@Component
public class AccountClient {

    /** Nombre del servicio destino en Eureka y de su circuit breaker. */
    static final String SERVICE_NAME = "bank-account-service";

    private static final String PRODUCT_LABEL = "Account";

    private final WebClient accountWebClient;
    private final RemoteCallDecorator remoteCallDecorator;

    /**
     * Inyeccion por constructor.
     *
     * @param accountWebClient    cliente HTTP balanceado hacia el servicio de cuentas
     * @param remoteCallDecorator decorador de Resilience4j
     */
    public AccountClient(WebClient accountWebClient, RemoteCallDecorator remoteCallDecorator) {
        this.accountWebClient = accountWebClient;
        this.remoteCallDecorator = remoteCallDecorator;
    }

    /**
     * Recupera una cuenta por su identificador.
     *
     * @param accountId identificador de la cuenta
     * @return los datos de la cuenta
     */
    public Single<AccountDto> findById(String accountId) {
        log.debug("Requesting account id={} to {}", accountId, SERVICE_NAME);
        Mono<AccountDto> call = accountWebClient.get()
                .uri("/accounts/{id}", accountId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, accountId, SERVICE_NAME))
                .bodyToMono(AccountDto.class);
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }

    /**
     * Busca una cuenta por su numero, usando el filtro
     * {@code GET /accounts?accountNumber=}.
     *
     * @param accountNumber numero de cuenta
     * @return la cuenta encontrada, o 404 si no existe
     */
    public Single<AccountDto> findByAccountNumber(String accountNumber) {
        log.debug("Requesting account number={} to {}", accountNumber, SERVICE_NAME);
        Mono<AccountDto> call = accountWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/accounts").queryParam("accountNumber", accountNumber).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, accountNumber, SERVICE_NAME))
                .bodyToFlux(AccountDto.class)
                .next()
                .switchIfEmpty(Mono.error(() ->
                        new ResourceNotFoundException("Account not found with number " + accountNumber)));
        return ReactorRxAdapter.monoToSingle(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }

    /**
     * Pide al servicio de cuentas que aplique un deposito o un retiro, con una
     * comision opcional.
     *
     * <p>Si no hay fondos suficientes, el servicio responde 409 y aqui se
     * traduce a {@code BusinessRuleException}, de modo que el movimiento nunca
     * se registra.</p>
     *
     * @param accountId identificador de la cuenta
     * @param request   operacion, importe y comision
     * @return completable que termina cuando el saldo fue actualizado
     */
    public Completable applyBalanceOperation(String accountId, AccountOperationRequest request) {
        log.debug("Requesting {} of {} (fee {}) on account id={} to {}",
                request.getOperation(), request.getAmount(), request.getFee(), accountId, SERVICE_NAME);
        Mono<Void> call = accountWebClient.post()
                .uri("/accounts/{id}/balance-operations", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        RemoteErrors.translate(response, PRODUCT_LABEL, accountId, SERVICE_NAME))
                .bodyToMono(Void.class);
        return ReactorRxAdapter.monoToCompletable(remoteCallDecorator.decorate(SERVICE_NAME, call));
    }
}
