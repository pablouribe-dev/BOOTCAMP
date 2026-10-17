package com.bank.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.AverageDailyBalanceResponse;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.BalanceResponse;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ServiceUnavailableException;
import com.bank.account.model.AccountProfile;
import com.bank.account.service.AccountService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AccountService accountService;

    @Test
    void createReturns201AndAcceptsLegacyInitialBalance() {
        when(accountService.create(any(AccountRequest.class))).thenAnswer(invocation -> {
            AccountRequest request = invocation.getArgument(0);
            return Single.just(AccountResponse.builder().id("a1").profile(AccountProfile.STANDARD)
                    .openingAmount(request.getOpeningAmount()).build());
        });

        webTestClient.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"accountType\":\"SAVINGS\",\"customerId\":\"c1\",\"initialBalance\":50}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.openingAmount").isEqualTo(50);
    }

    @Test
    void negativeOpeningAmountReturns400() {
        webTestClient.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"accountType\":\"SAVINGS\",\"customerId\":\"c1\",\"openingAmount\":-1}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").value(message -> message.toString().contains("openingAmount"));
    }

    @Test
    void vipWithoutCardReturns409AndCustomerServiceDownReturns503() {
        when(accountService.create(any(AccountRequest.class)))
                .thenReturn(Single.error(new BusinessRuleException("A VIP account requires the customer to hold a "
                        + "credit card")))
                .thenReturn(Single.error(new ServiceUnavailableException("bank-customer-service is not available")));
        String body = "{\"accountType\":\"SAVINGS\",\"customerId\":\"c1\"}";

        webTestClient.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isEqualTo(409);
        webTestClient.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo("bank-customer-service is not available");
    }

    @Test
    void listFiltersByAccountNumber() {
        when(accountService.findAll(null, "123")).thenReturn(Flowable.just(AccountResponse.builder().id("a1").build()));

        webTestClient.get().uri("/accounts?accountNumber=123").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$[0].id").isEqualTo("a1");
    }

    @Test
    void averageDailyBalanceEndpoint() {
        when(accountService.getAverageDailyBalance("a1", YearMonth.of(2026, 10)))
                .thenReturn(Single.just(AverageDailyBalanceResponse.builder().accountId("a1").month("2026-10")
                        .averageDailyBalance(new BigDecimal("1200")).minimumDailyAverage(new BigDecimal("1000"))
                        .meetsMinimum(true).build()));

        webTestClient.get().uri("/accounts/a1/average-daily-balance?month=2026-10").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.meetsMinimum").isEqualTo(true).jsonPath("$.month").isEqualTo("2026-10");
    }

    @Test
    void invalidMonthReturns400() {
        webTestClient.get().uri("/accounts/a1/average-daily-balance?month=octubre").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void balanceOperationsAndCrud() {
        when(accountService.applyBalanceOperation(eq("a1"), any(BalanceOperationRequest.class)))
                .thenReturn(Single.just(BalanceResponse.builder().accountId("a1").balance(new BigDecimal("8.5"))
                        .build()));
        when(accountService.findById("a1")).thenReturn(Single.just(AccountResponse.builder().id("a1").build()));
        when(accountService.getBalance("a1")).thenReturn(Single.just(BalanceResponse.builder().accountId("a1")
                .build()));
        when(accountService.update(eq("a1"), any(AccountRequest.class)))
                .thenReturn(Single.just(AccountResponse.builder().id("a1").build()));
        when(accountService.delete("a1")).thenReturn(Completable.complete());

        webTestClient.post().uri("/accounts/a1/balance-operations").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"operation\":\"DEPOSIT\",\"amount\":10,\"fee\":1.5}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.balance").isEqualTo(8.5);
        webTestClient.get().uri("/accounts/a1").exchange().expectStatus().isOk();
        webTestClient.get().uri("/accounts/a1/balance").exchange().expectStatus().isOk();
        webTestClient.put().uri("/accounts/a1").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"accountType\":\"SAVINGS\",\"customerId\":\"c1\"}").exchange().expectStatus().isOk();
        webTestClient.delete().uri("/accounts/a1").exchange().expectStatus().isNoContent();
    }
}
