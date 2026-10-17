package com.bank.debitcard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.debitcard.dto.DebitCardRequest;
import com.bank.debitcard.dto.DebitCardResponse;
import com.bank.debitcard.dto.DebitPaymentRequest;
import com.bank.debitcard.dto.DebitPaymentResponse;
import com.bank.debitcard.exception.BusinessRuleException;
import com.bank.debitcard.exception.ServiceUnavailableException;
import com.bank.debitcard.model.PaymentStatus;
import com.bank.debitcard.service.DebitCardService;
import com.bank.debitcard.service.DebitPaymentService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(DebitCardController.class)
class DebitCardControllerTest {

    private static final String CARD = "{\"customerId\":\"c1\",\"accountIds\":[\"a1\"],\"mainAccountId\":\"a1\"}";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DebitCardService debitCardService;

    @MockBean
    private DebitPaymentService debitPaymentService;

    @Test
    void cardCrudAndAccountLinks() {
        DebitCardResponse card = DebitCardResponse.builder().id("d1").build();
        when(debitCardService.create(any(DebitCardRequest.class))).thenReturn(Single.just(card));
        when(debitCardService.findAll("c1")).thenReturn(Flowable.just(card));
        when(debitCardService.findById("d1")).thenReturn(Single.just(card));
        when(debitCardService.update(eq("d1"), any(DebitCardRequest.class))).thenReturn(Single.just(card));
        when(debitCardService.delete("d1")).thenReturn(Completable.complete());
        when(debitCardService.addAccount("d1", "a2")).thenReturn(Single.just(card));
        when(debitCardService.changeMainAccount("d1", "a2")).thenReturn(Single.just(card));

        webTestClient.post().uri("/debit-cards").contentType(MediaType.APPLICATION_JSON).bodyValue(CARD).exchange()
                .expectStatus().isCreated();
        webTestClient.get().uri("/debit-cards?customerId=c1").exchange().expectStatus().isOk();
        webTestClient.get().uri("/debit-cards/d1").exchange().expectStatus().isOk();
        webTestClient.put().uri("/debit-cards/d1").contentType(MediaType.APPLICATION_JSON).bodyValue(CARD).exchange()
                .expectStatus().isOk();
        webTestClient.delete().uri("/debit-cards/d1").exchange().expectStatus().isNoContent();
        webTestClient.post().uri("/debit-cards/d1/accounts").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"accountId\":\"a2\"}").exchange().expectStatus().isOk();
        webTestClient.put().uri("/debit-cards/d1/main-account").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"accountId\":\"a2\"}").exchange().expectStatus().isOk();
    }

    @Test
    void createValidatesTheBodyAndMapsRuleViolationsTo409() {
        webTestClient.post().uri("/debit-cards").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\":\"c1\",\"accountIds\":[]}").exchange().expectStatus().isBadRequest();

        when(debitCardService.create(any(DebitCardRequest.class)))
                .thenReturn(Single.error(new BusinessRuleException("overdue debt")));
        webTestClient.post().uri("/debit-cards").contentType(MediaType.APPLICATION_JSON).bodyValue(CARD).exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void paymentsAreAcceptedWith202AndCanBeQueried() {
        DebitPaymentResponse payment = DebitPaymentResponse.builder().paymentId("p1").status(PaymentStatus.PENDING)
                .build();
        when(debitPaymentService.pay(eq("d1"), any(DebitPaymentRequest.class))).thenReturn(Single.just(payment));
        when(debitPaymentService.findByCard("d1")).thenReturn(Flowable.just(payment));
        when(debitPaymentService.findById("d1", "p1")).thenReturn(Single.just(payment));

        webTestClient.post().uri("/debit-cards/d1/payments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":50,\"description\":\"cafe\"}").exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.paymentId").isEqualTo("p1").jsonPath("$.status").isEqualTo("PENDING");
        webTestClient.get().uri("/debit-cards/d1/payments").exchange().expectStatus().isOk();
        webTestClient.get().uri("/debit-cards/d1/payments/p1").exchange().expectStatus().isOk();
    }

    @Test
    void kafkaDownIs503() {
        when(debitPaymentService.pay(eq("d1"), any(DebitPaymentRequest.class)))
                .thenReturn(Single.error(new ServiceUnavailableException("try again later")));

        webTestClient.post().uri("/debit-cards/d1/payments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":50}").exchange().expectStatus().isEqualTo(503);
    }
}
