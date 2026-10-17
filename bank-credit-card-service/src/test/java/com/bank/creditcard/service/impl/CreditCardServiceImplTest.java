package com.bank.creditcard.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.creditcard.client.CustomerClient;
import com.bank.creditcard.client.CustomerDto;
import com.bank.creditcard.client.CustomerType;
import com.bank.creditcard.config.CardRulesProperties;
import com.bank.creditcard.dto.CardOperationRequest;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.event.CardDebtEventPublisher;
import com.bank.creditcard.exception.BusinessRuleException;
import com.bank.creditcard.exception.ResourceNotFoundException;
import com.bank.creditcard.exception.ServiceUnavailableException;
import com.bank.creditcard.mapper.CreditCardMapper;
import com.bank.creditcard.model.CardOperationType;
import com.bank.creditcard.model.CardType;
import com.bank.creditcard.model.CreditCard;
import com.bank.creditcard.repository.CreditCardRepository;
import com.bank.creditcard.service.OverdueDebtService;
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
class CreditCardServiceImplTest {

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private OverdueDebtService overdueDebtService;

    @Mock
    private CardDebtEventPublisher cardDebtEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private CreditCardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreditCardServiceImpl(creditCardRepository, new CreditCardMapper(), customerClient,
                new CardRulesProperties(), overdueDebtService, cardDebtEventPublisher, clock);
        lenient().when(overdueDebtService.ensureNoOverdueDebt(any())).thenReturn(Completable.complete());
        lenient().when(cardDebtEventPublisher.publishCleared(any())).thenReturn(Completable.complete());
    }

    private CreditCardRequest request(CardType type, String limit) {
        return CreditCardRequest.builder().cardType(type).customerId("c1").creditLimit(new BigDecimal(limit)).build();
    }

    private CreditCard card(String limit, String used) {
        return CreditCard.builder().id("k1").cardNumber("4532000000000000").cardType(CardType.PERSONAL)
                .customerId("c1").creditLimit(new BigDecimal(limit)).usedAmount(new BigDecimal(used)).build();
    }

    private void saveReturnsArgument() {
        when(creditCardRepository.save(any(CreditCard.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void createIssuesCardWithPrefixedNumberAndFullAvailableBalance() {
        when(customerClient.findById("c1"))
                .thenReturn(Single.just(CustomerDto.builder().id("c1").type(CustomerType.PERSONAL).build()));
        when(creditCardRepository.existsByCardNumber(anyString())).thenReturn(Mono.just(true), Mono.just(false));
        saveReturnsArgument();

        service.create(request(CardType.PERSONAL, "1000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getCardNumber().matches("4532\\d{12}")
                        && r.getAvailableBalance().compareTo(new BigDecimal("1000")) == 0);
    }

    @Test
    void cardTypeMustMatchCustomerType() {
        when(customerClient.findById("c1"))
                .thenReturn(Single.just(CustomerDto.builder().id("c1").type(CustomerType.BUSINESS).build()));

        service.create(request(CardType.PERSONAL, "1000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only hold BUSINESS credit cards"));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void customerWithoutTypeIs503() {
        when(customerClient.findById("c1")).thenReturn(Single.just(CustomerDto.builder().id("c1").build()));

        service.create(request(CardType.PERSONAL, "1000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
    }

    @Test
    void readOperations() {
        CreditCard card = card("1000", "200");
        when(creditCardRepository.findByCustomerId("c1")).thenReturn(Flux.just(card));
        when(creditCardRepository.findAll()).thenReturn(Flux.just(card, card));
        when(creditCardRepository.findById("k1")).thenReturn(Mono.just(card));
        when(creditCardRepository.findById("x")).thenReturn(Mono.empty());

        service.findAll("c1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll("").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(2);
        service.findById("k1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.getBalance("k1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getAvailableBalance().compareTo(new BigDecimal("800")) == 0);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateLimitNotBelowUsedAmountAndImmutableFields() {
        when(creditCardRepository.findById("k1")).thenReturn(Mono.just(card("1000", "200")));
        saveReturnsArgument();

        service.update("k1", request(CardType.PERSONAL, "1500")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getCreditLimit().compareTo(new BigDecimal("1500")) == 0);
        service.update("k1", request(CardType.PERSONAL, "100")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("lower than the used amount"));
        service.update("k1", request(CardType.BUSINESS, "1500")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("cardType cannot be modified"));
        CreditCardRequest other = request(CardType.PERSONAL, "1500");
        other.setCustomerId("c2");
        service.update("k1", other).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("customerId cannot be modified"));
    }

    @Test
    void delete() {
        CreditCard card = card("1000", "0");
        when(creditCardRepository.findById("k1")).thenReturn(Mono.just(card));
        when(creditCardRepository.delete(card)).thenReturn(Mono.empty());

        service.delete("k1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void chargeAndPaymentRespectLimits() {
        when(creditCardRepository.findById("k1")).thenAnswer(invocation -> Mono.just(card("1000", "200")));
        saveReturnsArgument();

        service.applyBalanceOperation("k1", operation(CardOperationType.CHARGE, "800")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertValue(r -> r.getAvailableBalance().signum() == 0);
        service.applyBalanceOperation("k1", operation(CardOperationType.CHARGE, "801")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        service.applyBalanceOperation("k1", operation(CardOperationType.PAYMENT, "200")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertValue(r -> r.getUsedAmount().signum() == 0);
        service.applyBalanceOperation("k1", operation(CardOperationType.PAYMENT, "201")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
    }

    private CardOperationRequest operation(CardOperationType type, String amount) {
        return CardOperationRequest.builder().operation(type).amount(new BigDecimal(amount)).build();
    }

    // --- Parte III: deuda vencida ---------------------------------------------

    @Test
    void customerWithOverdueDebtCannotGetANewCard() {
        when(overdueDebtService.ensureNoOverdueDebt("c1"))
                .thenReturn(Completable.error(new BusinessRuleException("has an overdue debt")));

        service.create(request(CardType.PERSONAL, "1000")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(customerClient, never()).findById(any());
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void updateSetsPaymentDueDateAndDueAmount() {
        when(creditCardRepository.findById("k1")).thenReturn(Mono.just(card("1000", "300")));
        saveReturnsArgument();
        CreditCardRequest update = request(CardType.PERSONAL, "1000");
        update.setPaymentDueDate(LocalDate.of(2026, 10, 1));
        update.setDueAmount(new BigDecimal("300"));

        service.update("k1", update).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> LocalDate.of(2026, 10, 1).equals(response.getPaymentDueDate())
                        && response.getDueAmount().compareTo(new BigDecimal("300")) == 0);
    }

    @Test
    void payingTheOverdueAmountPublishesClearedButAChargeDoesNot() {
        CreditCard overdue = card("1000", "300");
        overdue.setPaymentDueDate(LocalDate.of(2026, 10, 1));
        overdue.setDueAmount(new BigDecimal("300"));
        overdue.setOverdueNotified(true);
        when(creditCardRepository.findById("k1")).thenReturn(Mono.just(overdue));
        saveReturnsArgument();

        service.applyBalanceOperation("k1", CardOperationRequest.builder().operation(CardOperationType.CHARGE)
                .amount(new BigDecimal("10")).build()).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(cardDebtEventPublisher, never()).publishCleared(any());

        service.applyBalanceOperation("k1", CardOperationRequest.builder().operation(CardOperationType.PAYMENT)
                .amount(new BigDecimal("300")).build()).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        org.assertj.core.api.Assertions.assertThat(overdue.getDueAmount()).isEqualByComparingTo("0");
        org.assertj.core.api.Assertions.assertThat(overdue.getOverdueNotified()).isFalse();
        verify(cardDebtEventPublisher).publishCleared(overdue);
    }
}
