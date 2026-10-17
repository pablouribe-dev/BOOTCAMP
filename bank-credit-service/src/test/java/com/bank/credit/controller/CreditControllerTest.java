package com.bank.credit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.credit.dto.CreditBalanceResponse;
import com.bank.credit.dto.CreditOperationRequest;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.dto.CreditResponse;
import com.bank.credit.exception.BusinessRuleException;
import com.bank.credit.exception.ResourceNotFoundException;
import com.bank.credit.exception.ServiceUnavailableException;
import com.bank.credit.service.CreditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(CreditController.class)
class CreditControllerTest {

    private static final String BODY = """
            {"creditType":"PERSONAL","customerId":"c1","grantedAmount":5000,"interestRate":12.5}""";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CreditService creditService;

    @Test
    void createReturns201And409And503() {
        when(creditService.create(any(CreditRequest.class)))
                .thenReturn(Single.just(CreditResponse.builder().id("cr1").build()))
                .thenReturn(Single.error(new BusinessRuleException("A personal customer can only hold 1 credit(s)")))
                .thenReturn(Single.error(new ServiceUnavailableException("bank-customer-service is not available")));

        webTestClient.post().uri("/credits").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isCreated();
        webTestClient.post().uri("/credits").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isEqualTo(409);
        webTestClient.post().uri("/credits").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void invalidBodyReturns400() {
        webTestClient.post().uri("/credits").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditType\":\"PERSONAL\"}").exchange().expectStatus().isBadRequest();
    }

    @Test
    void readUpdateDeleteAndPayment() {
        when(creditService.findAll("c1")).thenReturn(Flowable.just(CreditResponse.builder().id("cr1").build()));
        when(creditService.findById("x")).thenReturn(Single.error(new ResourceNotFoundException("missing")));
        when(creditService.getBalance("cr1")).thenReturn(Single.just(CreditBalanceResponse.builder().build()));
        when(creditService.update(eq("cr1"), any(CreditRequest.class)))
                .thenReturn(Single.just(CreditResponse.builder().id("cr1").build()));
        when(creditService.delete("cr1")).thenReturn(Completable.complete());
        when(creditService.applyBalanceOperation(eq("cr1"), any(CreditOperationRequest.class)))
                .thenReturn(Single.just(CreditBalanceResponse.builder().creditId("cr1").build()));

        webTestClient.get().uri("/credits?customerId=c1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("cr1");
        webTestClient.get().uri("/credits/x").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/credits/cr1/balance").exchange().expectStatus().isOk();
        webTestClient.put().uri("/credits/cr1").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/credits/cr1").exchange().expectStatus().isNoContent();
        webTestClient.post().uri("/credits/cr1/balance-operations").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"operation\":\"PAYMENT\",\"amount\":50}").exchange().expectStatus().isOk();
    }
}
