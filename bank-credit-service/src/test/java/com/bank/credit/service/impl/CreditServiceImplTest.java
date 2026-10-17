package com.bank.credit.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.credit.client.CustomerClient;
import com.bank.credit.client.CustomerDto;
import com.bank.credit.client.CustomerType;
import com.bank.credit.config.CreditRulesProperties;
import com.bank.credit.dto.CreditOperationRequest;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.event.CreditDebtEventPublisher;
import com.bank.credit.exception.BusinessRuleException;
import com.bank.credit.exception.ResourceNotFoundException;
import com.bank.credit.exception.ServiceUnavailableException;
import com.bank.credit.mapper.CreditMapper;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditOperationType;
import com.bank.credit.model.CreditType;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditRepository creditRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private OverdueDebtService overdueDebtService;

    @Mock
    private CreditDebtEventPublisher creditDebtEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private CreditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreditServiceImpl(creditRepository, new CreditMapper(), customerClient,
                new CreditRulesProperties(), overdueDebtService, creditDebtEventPublisher, clock);
        lenient().when(overdueDebtService.ensureNoOverdueDebt(any())).thenReturn(Completable.complete());
        lenient().when(creditDebtEventPublisher.publishCleared(any())).thenReturn(Completable.complete());
    }

    private CreditRequest request(CreditType type, String amount) {
        return CreditRequest.builder().creditType(type).customerId("c1").grantedAmount(new BigDecimal(amount))
                .interestRate(new BigDecimal("12")).build();
    }

    private Credit credit(String granted, String outstanding) {
        return Credit.builder().id("cr1").creditType(CreditType.PERSONAL).customerId("c1")
                .grantedAmount(new BigDecimal(granted)).outstandingBalance(new BigDecimal(outstanding))
                .interestRate(BigDecimal.TEN).build();
    }

    private void customerIs(CustomerType type) {
        when(customerClient.findById("c1")).thenReturn(Single.just(CustomerDto.builder().id("c1").type(type).build()));
    }

    private void saveReturnsArgument() {
        when(creditRepository.save(any(Credit.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void personalCustomerGetsOnePersonalCredit() {
        customerIs(CustomerType.PERSONAL);
        when(creditRepository.countByCustomerId("c1")).thenReturn(Mono.just(0L));
        saveReturnsArgument();

        service.create(request(CreditType.PERSONAL, "5000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getOutstandingBalance().compareTo(new BigDecimal("5000")) == 0);
    }

    @Test
    void personalCustomerCannotGetASecondCreditOrABusinessOne() {
        customerIs(CustomerType.PERSONAL);
        when(creditRepository.countByCustomerId("c1")).thenReturn(Mono.just(1L));

        service.create(request(CreditType.PERSONAL, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("credit(s)"));
        service.create(request(CreditType.BUSINESS, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only hold PERSONAL credits"));
        verify(creditRepository, never()).save(any());
    }

    @Test
    void businessCustomerGetsBusinessCreditsOnly() {
        customerIs(CustomerType.BUSINESS);
        saveReturnsArgument();

        service.create(request(CreditType.BUSINESS, "20000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValueCount(1);
        service.create(request(CreditType.PERSONAL, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only hold BUSINESS credits"));
    }

    @Test
    void customerWithoutTypeOrUnavailableIs503() {
        when(customerClient.findById("c1")).thenReturn(Single.just(CustomerDto.builder().id("c1").build()))
                .thenReturn(Single.error(new ServiceUnavailableException("down")));

        service.create(request(CreditType.PERSONAL, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
        service.create(request(CreditType.PERSONAL, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
    }

    @Test
    void findAllFindByIdAndBalance() {
        Credit credit = credit("100", "100");
        when(creditRepository.findByCustomerId("c1")).thenReturn(Flux.just(credit));
        when(creditRepository.findAll()).thenReturn(Flux.just(credit, credit));
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit));
        when(creditRepository.findById("x")).thenReturn(Mono.empty());

        service.findAll("c1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll(null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(2);
        service.findById("cr1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.getBalance("cr1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getOutstandingBalance().compareTo(new BigDecimal("100")) == 0);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesRateAndGrantedAmountOnlyWithoutPayments() {
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit("100", "100")));
        saveReturnsArgument();

        service.update("cr1", request(CreditType.PERSONAL, "300")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getGrantedAmount().compareTo(new BigDecimal("300")) == 0
                        && r.getOutstandingBalance().compareTo(new BigDecimal("300")) == 0);
    }

    @Test
    void updateRejectsGrantedAmountAfterPaymentsAndImmutableFields() {
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit("100", "60")));

        service.update("cr1", request(CreditType.PERSONAL, "300")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("received payments"));
        service.update("cr1", request(CreditType.BUSINESS, "100")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("creditType cannot be modified"));
        CreditRequest otherCustomer = request(CreditType.PERSONAL, "100");
        otherCustomer.setCustomerId("c2");
        service.update("cr1", otherCustomer).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("customerId cannot be modified"));
    }

    @Test
    void delete() {
        Credit credit = credit("100", "100");
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit));
        when(creditRepository.delete(credit)).thenReturn(Mono.empty());

        service.delete("cr1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void paymentReducesOutstandingBalanceButCannotExceedIt() {
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit("100", "100")));
        saveReturnsArgument();
        CreditOperationRequest payment = CreditOperationRequest.builder().operation(CreditOperationType.PAYMENT)
                .amount(new BigDecimal("40")).build();

        service.applyBalanceOperation("cr1", payment).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getOutstandingBalance().compareTo(new BigDecimal("60")) == 0);

        payment.setAmount(new BigDecimal("1000"));
        service.applyBalanceOperation("cr1", payment).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }

    // --- Parte III: deuda vencida ---------------------------------------------

    @Test
    void customerWithOverdueDebtCannotGetANewCredit() {
        when(overdueDebtService.ensureNoOverdueDebt("c1"))
                .thenReturn(Completable.error(new BusinessRuleException("has an overdue debt")));

        service.create(request(CreditType.PERSONAL, "5000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(customerClient, never()).findById(any());
        verify(creditRepository, never()).save(any());
    }

    @Test
    void updateSetsDueDateAndDueAmount() {
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(credit("1000", "1000")));
        saveReturnsArgument();
        CreditRequest update = request(CreditType.PERSONAL, "1000");
        update.setDueDate(LocalDate.of(2026, 10, 1));
        update.setDueAmount(new BigDecimal("150"));

        service.update("cr1", update).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> LocalDate.of(2026, 10, 1).equals(response.getDueDate())
                        && response.getDueAmount().compareTo(new BigDecimal("150")) == 0
                        && !response.getOverdueNotified());
        verify(creditDebtEventPublisher, never()).publishCleared(any());
    }

    @Test
    void payingTheWholeOverdueAmountPublishesCleared() {
        Credit overdue = credit("1000", "1000");
        overdue.setDueDate(LocalDate.of(2026, 10, 1));
        overdue.setDueAmount(new BigDecimal("150"));
        overdue.setOverdueNotified(true);
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(overdue));
        saveReturnsArgument();

        service.applyBalanceOperation("cr1", CreditOperationRequest.builder().operation(CreditOperationType.PAYMENT)
                        .amount(new BigDecimal("200")).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        org.assertj.core.api.Assertions.assertThat(overdue.getDueAmount()).isEqualByComparingTo("0");
        org.assertj.core.api.Assertions.assertThat(overdue.getOverdueNotified()).isFalse();
        verify(creditDebtEventPublisher).publishCleared(overdue);
    }

    @Test
    void partialPaymentKeepsTheDebtOverdue() {
        Credit overdue = credit("1000", "1000");
        overdue.setDueDate(LocalDate.of(2026, 10, 1));
        overdue.setDueAmount(new BigDecimal("150"));
        overdue.setOverdueNotified(true);
        when(creditRepository.findById("cr1")).thenReturn(Mono.just(overdue));
        saveReturnsArgument();

        service.applyBalanceOperation("cr1", CreditOperationRequest.builder().operation(CreditOperationType.PAYMENT)
                        .amount(new BigDecimal("100")).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        org.assertj.core.api.Assertions.assertThat(overdue.getDueAmount()).isEqualByComparingTo("50");
        org.assertj.core.api.Assertions.assertThat(overdue.getOverdueNotified()).isTrue();
        verify(creditDebtEventPublisher, never()).publishCleared(any());
    }
}
