package com.bank.transaction.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bank.transaction.client.AccountDto;
import com.bank.transaction.client.AccountTypeDto;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ServiceUnavailableException;
import com.bank.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountMovementRulesTest {

    /** Hoy es 15 de octubre. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Mock
    private TransactionRepository repository;

    private AccountMovementRules rules;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        rules = new AccountMovementRules(repository, clock);
    }

    private AccountDto account(AccountTypeDto type, Integer limit, Integer movementDay) {
        return AccountDto.builder().id("a1").accountType(type).monthlyMovementLimit(limit).movementDay(movementDay)
                .build();
    }

    private void movementsThisMonth(long count) {
        when(repository.countByProductIdAndOriginNotInAndCreatedAtBetween(eq("a1"), anyCollection(), any(), any()))
                .thenReturn(Mono.just(count));
    }

    @Test
    void currentAccountHasNoRestriction() {
        rules.validateHardRules(account(AccountTypeDto.CURRENT, null, null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
        verifyNoInteractions(repository);
    }

    @Test
    void savingsAllowsMovementsBelowTheMonthlyLimit() {
        movementsThisMonth(1);
        rules.validateHardRules(account(AccountTypeDto.SAVINGS, 2, null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
    }

    @Test
    void savingsRejectsWhenTheMonthlyLimitIsReached() {
        movementsThisMonth(2);
        rules.validateHardRules(account(AccountTypeDto.SAVINGS, 2, null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("monthly movement limit of 2"));
    }

    @Test
    void savingsWithoutLimitIsAllowed() {
        rules.validateHardRules(account(AccountTypeDto.SAVINGS, null, null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
    }

    @Test
    void fixedTermOnlyOnItsDayAndOncePerMonth() {
        rules.validateHardRules(account(AccountTypeDto.FIXED_TERM, 1, 10)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("day 10"));

        movementsThisMonth(0);
        rules.validateHardRules(account(AccountTypeDto.FIXED_TERM, 1, 15)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
    }

    @Test
    void fixedTermSecondMovementUsesDefaultLimitWhenMissing() {
        movementsThisMonth(1);
        rules.validateHardRules(account(AccountTypeDto.FIXED_TERM, null, 15)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }

    @Test
    void accountWithoutTypeIs503() {
        rules.validateHardRules(account(null, null, null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
    }

    @Test
    void noFeeWhileBelowTheFreeTransactionLimit() {
        when(repository.countByProductIdAndTransactionTypeInAndOriginNotInAndCreatedAtBetween(eq("a1"), anyCollection(),
                anyCollection(), any(),
                any())).thenReturn(Mono.just(9L));
        AccountDto account = AccountDto.builder().id("a1").freeTransactionLimit(10)
                .transactionFee(new BigDecimal("1.50")).build();

        rules.calculateFee(account).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(fee -> fee.signum() == 0);
    }

    @Test
    void feeOnceTheFreeTransactionLimitIsReached() {
        when(repository.countByProductIdAndTransactionTypeInAndOriginNotInAndCreatedAtBetween(eq("a1"), anyCollection(),
                anyCollection(), any(),
                any())).thenReturn(Mono.just(10L));
        AccountDto account = AccountDto.builder().id("a1").freeTransactionLimit(10)
                .transactionFee(new BigDecimal("1.50")).build();

        rules.calculateFee(account).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(fee -> fee.compareTo(new BigDecimal("1.50")) == 0);
    }

    @Test
    void noFeeWhenTheAccountHasNoFeeConfiguration() {
        rules.calculateFee(AccountDto.builder().id("a1").build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(fee -> fee.signum() == 0);
        rules.calculateFee(AccountDto.builder().id("a1").freeTransactionLimit(1).transactionFee(BigDecimal.ZERO)
                .build()).test().awaitDone(1, TimeUnit.SECONDS).assertValue(fee -> fee.signum() == 0);
        verifyNoInteractions(repository);
    }
}
