package com.bank.transaction.client;

import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ResourceNotFoundException;
import com.bank.transaction.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Prueba los clientes REST con un {@link WebClient} cuyas respuestas se
 * simulan (sin red): exito, 404, 409 y estados inesperados.
 */
class RemoteClientsTest {

    private final RemoteCallDecorator decorator = new RemoteCallDecorator(CircuitBreakerRegistry.ofDefaults(),
            TimeLimiterRegistry.of(io.github.resilience4j.timelimiter.TimeLimiterConfig.custom()
                    .timeoutDuration(java.time.Duration.ofSeconds(5)).build()));

    private WebClient answering(HttpStatus status, String body) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build())).build();
    }

    @Test
    void customerClientFindsThePayerAndTranslates404() {
        new CustomerClient(answering(HttpStatus.OK, "{\"id\":\"c2\",\"name\":\"Ana\"}"), decorator)
                .findById("c2").test().awaitDone(6, TimeUnit.SECONDS)
                .assertValue(customer -> "c2".equals(customer.getId()) && "Ana".equals(customer.getName()));
        new CustomerClient(answering(HttpStatus.NOT_FOUND, "{}"), decorator)
                .findById("x").test().awaitDone(6, TimeUnit.SECONDS)
                .assertError(error -> error instanceof ResourceNotFoundException
                        && error.getMessage().equals("Customer not found with id x"));
    }

    @Test
    void accountClientReadsAndOperates() {
        String account = "{\"id\":\"a1\",\"customerId\":\"c1\",\"balance\":100}";
        AccountClient client = new AccountClient(answering(HttpStatus.OK, account), decorator);
        client.findById("a1").test().awaitDone(6, TimeUnit.SECONDS)
                .assertValue(found -> "c1".equals(found.getCustomerId()));
        new AccountClient(answering(HttpStatus.OK, "[" + account + "]"), decorator).findByAccountNumber("123")
                .test().awaitDone(6, TimeUnit.SECONDS).assertValue(found -> "a1".equals(found.getId()));
        new AccountClient(answering(HttpStatus.OK, "[]"), decorator).findByAccountNumber("999")
                .test().awaitDone(6, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
        new AccountClient(answering(HttpStatus.OK, "{}"), decorator).applyBalanceOperation("a1",
                        AccountOperationRequest.builder().operation(AccountOperationType.DEPOSIT)
                                .amount(BigDecimal.TEN).build())
                .test().awaitDone(6, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void conflictsBecomeBusinessRuleErrorsWithTheRemoteMessage() {
        new AccountClient(answering(HttpStatus.CONFLICT, "{\"message\":\"Insufficient funds\"}"), decorator)
                .applyBalanceOperation("a1", AccountOperationRequest.builder()
                        .operation(AccountOperationType.WITHDRAWAL).amount(BigDecimal.TEN).build())
                .test().awaitDone(6, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && "Insufficient funds".equals(error.getMessage()));
    }

    @Test
    void unexpectedStatusesBecome503() {
        new CreditClient(answering(HttpStatus.INTERNAL_SERVER_ERROR, "{}"), decorator).findById("k1")
                .test().awaitDone(6, TimeUnit.SECONDS).assertError(ServiceUnavailableException.class);
    }

    @Test
    void creditAndCreditCardClients() {
        new CreditClient(answering(HttpStatus.OK, "{\"id\":\"k1\",\"customerId\":\"c1\"}"), decorator)
                .findById("k1").test().awaitDone(6, TimeUnit.SECONDS).assertValueCount(1);
        new CreditClient(answering(HttpStatus.OK, "{}"), decorator).applyBalanceOperation("k1",
                        CreditOperationRequest.builder().operation(CreditOperationType.PAYMENT)
                                .amount(BigDecimal.ONE).build())
                .test().awaitDone(6, TimeUnit.SECONDS).assertComplete();
        new CreditCardClient(answering(HttpStatus.OK, "{\"id\":\"cc1\",\"customerId\":\"c1\"}"), decorator)
                .findById("cc1").test().awaitDone(6, TimeUnit.SECONDS).assertValueCount(1);
        new CreditCardClient(answering(HttpStatus.NOT_FOUND, "{}"), decorator).applyBalanceOperation("cc1",
                        CardOperationRequest.builder().operation(CardOperationType.PAYMENT)
                                .amount(BigDecimal.ONE).build())
                .test().awaitDone(6, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }
}
