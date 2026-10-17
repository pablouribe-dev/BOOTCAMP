package com.bank.transaction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.transaction.dto.AccountTransactionRequest;
import com.bank.transaction.dto.ChargeRequest;
import com.bank.transaction.dto.PaymentRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransactionUpdateRequest;
import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.dto.TransferResponse;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ServiceUnavailableException;
import com.bank.transaction.model.TransferType;
import com.bank.transaction.service.TransactionService;
import com.bank.transaction.service.TransferService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private TransferService transferService;

    @Test
    void transferReturns201WithBothMovements() {
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Single.just(TransferResponse.builder()
                .transferId("tr1").transferType(TransferType.THIRD_PARTY).amount(BigDecimal.TEN)
                .outgoing(TransactionResponse.builder().id("t1").build())
                .incoming(TransactionResponse.builder().id("t2").build()).build()));

        webTestClient.post().uri("/transactions/transfer").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sourceAccountId\":\"a1\",\"destinationAccountNumber\":\"222\",\"amount\":10}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.transferType").isEqualTo("THIRD_PARTY")
                .jsonPath("$.outgoing.id").isEqualTo("t1")
                .jsonPath("$.incoming.id").isEqualTo("t2");
    }

    @Test
    void transferWithoutDestinationReturns400AndInsufficientFunds409() {
        webTestClient.post().uri("/transactions/transfer").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sourceAccountId\":\"a1\",\"amount\":10}").exchange().expectStatus().isBadRequest();

        when(transferService.transfer(any(TransferRequest.class)))
                .thenReturn(Single.error(new BusinessRuleException("Insufficient funds in account 111")));
        webTestClient.post().uri("/transactions/transfer").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"sourceAccountId\":\"a1\",\"destinationAccountNumber\":\"2\",\"amount\":10}")
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void movementsAndCrud() {
        TransactionResponse response = TransactionResponse.builder().id("t1").feeCharged(new BigDecimal("1.5"))
                .build();
        when(transactionService.deposit(any(AccountTransactionRequest.class))).thenReturn(Single.just(response));
        when(transactionService.withdrawal(any(AccountTransactionRequest.class)))
                .thenReturn(Single.error(new ServiceUnavailableException("bank-account-service is not available")));
        when(transactionService.payment(any(PaymentRequest.class))).thenReturn(Single.just(response));
        when(transactionService.charge(any(ChargeRequest.class))).thenReturn(Single.just(response));
        when(transactionService.findAll("a1")).thenReturn(Flowable.just(response));
        when(transactionService.findById("t1")).thenReturn(Single.just(response));
        when(transactionService.update(eq("t1"), any(TransactionUpdateRequest.class)))
                .thenReturn(Single.just(response));
        when(transactionService.delete("t1")).thenReturn(Completable.complete());
        String movement = "{\"accountId\":\"a1\",\"amount\":10}";

        webTestClient.post().uri("/transactions/deposit").contentType(MediaType.APPLICATION_JSON).bodyValue(movement)
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.feeCharged").isEqualTo(1.5);
        webTestClient.post().uri("/transactions/withdrawal").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(movement).exchange().expectStatus().isEqualTo(503);
        webTestClient.post().uri("/transactions/payment").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productType\":\"CREDIT\",\"productId\":\"cr1\",\"amount\":10}")
                .exchange().expectStatus().isCreated();
        webTestClient.post().uri("/transactions/charge").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditCardId\":\"k1\",\"amount\":10}").exchange().expectStatus().isCreated();
        webTestClient.get().uri("/transactions?productId=a1").exchange().expectStatus().isOk();
        webTestClient.get().uri("/transactions/t1").exchange().expectStatus().isOk();
        webTestClient.put().uri("/transactions/t1").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"description\":\"x\"}").exchange().expectStatus().isOk();
        webTestClient.delete().uri("/transactions/t1").exchange().expectStatus().isNoContent();
    }
}
