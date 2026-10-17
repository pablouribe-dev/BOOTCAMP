package com.bank.yanki.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.dto.WalletResponse;
import com.bank.yanki.dto.YankiPaymentRequest;
import com.bank.yanki.dto.YankiPaymentResponse;
import com.bank.yanki.exception.BusinessRuleException;
import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.service.WalletService;
import com.bank.yanki.service.YankiPaymentService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest({WalletController.class, YankiPaymentController.class})
class YankiControllersTest {

    private static final String WALLET = "{\"documentType\":\"DNI\",\"documentNumber\":\"12345678\","
            + "\"phoneNumber\":\"911111111\",\"imei\":\"356938035643809\",\"email\":\"ana@mail.com\"}";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private WalletService walletService;

    @MockBean
    private YankiPaymentService yankiPaymentService;

    @Test
    void walletEndpoints() {
        WalletResponse wallet = WalletResponse.builder().id("w1").build();
        when(walletService.create(any(WalletRequest.class))).thenReturn(Single.just(wallet));
        when(walletService.findAll("911111111")).thenReturn(Flowable.just(wallet));
        when(walletService.findById("w1")).thenReturn(Single.just(wallet));
        when(walletService.update(eq("w1"), any(WalletRequest.class))).thenReturn(Single.just(wallet));
        when(walletService.delete("w1")).thenReturn(Completable.complete());
        when(walletService.linkDebitCard("w1", "5555")).thenReturn(Single.just(wallet));
        when(walletService.unlinkDebitCard("w1")).thenReturn(Single.just(wallet));

        webTestClient.post().uri("/yanki/wallets").contentType(MediaType.APPLICATION_JSON).bodyValue(WALLET)
                .exchange().expectStatus().isCreated();
        webTestClient.get().uri("/yanki/wallets?phoneNumber=911111111").exchange().expectStatus().isOk();
        webTestClient.get().uri("/yanki/wallets/w1").exchange().expectStatus().isOk();
        webTestClient.put().uri("/yanki/wallets/w1").contentType(MediaType.APPLICATION_JSON).bodyValue(WALLET)
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/yanki/wallets/w1").exchange().expectStatus().isNoContent();
        webTestClient.put().uri("/yanki/wallets/w1/debit-card").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"cardNumber\":\"5555\"}").exchange().expectStatus().isOk();
        webTestClient.delete().uri("/yanki/wallets/w1/debit-card").exchange().expectStatus().isOk();
    }

    @Test
    void walletValidation() {
        webTestClient.post().uri("/yanki/wallets").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"documentType\":\"DNI\",\"phoneNumber\":\"12\"}").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void paymentEndpoints() {
        YankiPaymentResponse payment = YankiPaymentResponse.builder().paymentId("p1").status(PaymentStatus.PENDING)
                .build();
        when(yankiPaymentService.send(any(YankiPaymentRequest.class))).thenReturn(Single.just(payment));
        when(yankiPaymentService.findById("p1")).thenReturn(Single.just(payment));
        when(yankiPaymentService.findAll("911111111")).thenReturn(Flowable.just(payment));

        webTestClient.post().uri("/yanki/payments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fromPhoneNumber\":\"911111111\",\"toPhoneNumber\":\"922222222\",\"amount\":10}")
                .exchange().expectStatus().isAccepted().expectBody().jsonPath("$.status").isEqualTo("PENDING");
        webTestClient.get().uri("/yanki/payments/p1").exchange().expectStatus().isOk();
        webTestClient.get().uri("/yanki/payments?phoneNumber=911111111").exchange().expectStatus().isOk();
    }

    @Test
    void insufficientBalanceIs409() {
        when(yankiPaymentService.send(any(YankiPaymentRequest.class)))
                .thenReturn(Single.error(new BusinessRuleException("Insufficient wallet balance")));

        webTestClient.post().uri("/yanki/payments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fromPhoneNumber\":\"911111111\",\"toPhoneNumber\":\"922222222\",\"amount\":10}")
                .exchange().expectStatus().isEqualTo(409);
    }
}
