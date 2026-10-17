package com.bank.creditcard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.creditcard.dto.CardBalanceResponse;
import com.bank.creditcard.dto.CardOperationRequest;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.dto.CreditCardResponse;
import com.bank.creditcard.exception.BusinessRuleException;
import com.bank.creditcard.exception.ResourceNotFoundException;
import com.bank.creditcard.service.CreditCardService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(CreditCardController.class)
class CreditCardControllerTest {

    private static final String BODY = "{\"cardType\":\"PERSONAL\",\"customerId\":\"c1\",\"creditLimit\":1000}";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CreditCardService creditCardService;

    @Test
    void createReturns201And409() {
        when(creditCardService.create(any(CreditCardRequest.class)))
                .thenReturn(Single.just(CreditCardResponse.builder().id("k1").build()))
                .thenReturn(Single.error(new BusinessRuleException("type mismatch")));

        webTestClient.post().uri("/credit-cards").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("k1");
        webTestClient.post().uri("/credit-cards").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void zeroLimitReturns400() {
        webTestClient.post().uri("/credit-cards").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"cardType\":\"PERSONAL\",\"customerId\":\"c1\",\"creditLimit\":0}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void readUpdateDeleteAndOperations() {
        when(creditCardService.findAll("c1")).thenReturn(Flowable.just(CreditCardResponse.builder().id("k1").build()));
        when(creditCardService.findById("x")).thenReturn(Single.error(new ResourceNotFoundException("missing")));
        when(creditCardService.getBalance("k1")).thenReturn(Single.just(CardBalanceResponse.builder().build()));
        when(creditCardService.update(eq("k1"), any(CreditCardRequest.class)))
                .thenReturn(Single.just(CreditCardResponse.builder().id("k1").build()));
        when(creditCardService.delete("k1")).thenReturn(Completable.complete());
        when(creditCardService.applyBalanceOperation(eq("k1"), any(CardOperationRequest.class)))
                .thenReturn(Single.just(CardBalanceResponse.builder().cardId("k1").build()));

        webTestClient.get().uri("/credit-cards?customerId=c1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("k1");
        webTestClient.get().uri("/credit-cards/x").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/credit-cards/k1/balance").exchange().expectStatus().isOk();
        webTestClient.put().uri("/credit-cards/k1").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/credit-cards/k1").exchange().expectStatus().isNoContent();
        webTestClient.post().uri("/credit-cards/k1/balance-operations").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"operation\":\"CHARGE\",\"amount\":20}").exchange().expectStatus().isOk();
    }
}
