package com.bank.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.TestData;
import com.bank.account.client.CustomerClient;
import com.bank.account.client.CustomerDto;
import com.bank.account.client.CustomerType;
import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.HolderDto;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ResourceNotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.model.Account;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import com.bank.account.model.BalanceOperationType;
import com.bank.account.model.DocumentType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.DailyBalanceService;
import com.bank.account.service.opening.AccountOpeningChain;
import com.bank.account.service.rule.AccountRuleFactory;
import com.bank.account.service.rule.CurrentAccountRule;
import com.bank.account.service.rule.FixedTermAccountRule;
import com.bank.account.service.rule.PymeCurrentAccountRule;
import com.bank.account.service.rule.SavingsAccountRule;
import com.bank.account.service.rule.VipSavingsAccountRule;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private AccountOpeningChain accountOpeningChain;

    @Mock
    private DailyBalanceService dailyBalanceService;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        var properties = TestData.accountProperties();
        SavingsAccountRule savings = new SavingsAccountRule(properties);
        CurrentAccountRule current = new CurrentAccountRule(properties);
        AccountRuleFactory factory = new AccountRuleFactory(savings, current, new FixedTermAccountRule(properties),
                new VipSavingsAccountRule(savings, properties), new PymeCurrentAccountRule(current, properties));
        service = new AccountServiceImpl(accountRepository, new AccountMapper(), customerClient, factory,
                accountOpeningChain, dailyBalanceService, accountEventPublisher);
        org.mockito.Mockito.lenient().when(accountEventPublisher.publishUpserted(any())).thenReturn(Completable.complete());
        org.mockito.Mockito.lenient().when(accountEventPublisher.publishDeleted(any())).thenReturn(Completable.complete());
    }

    private Account account(String id, AccountType type, String balance) {
        return Account.builder().id(id).accountNumber("12345678901234").accountType(type).customerId("c1")
                .balance(new BigDecimal(balance)).createdAt(LocalDateTime.now()).build();
    }

    private void saveReturnsArgument() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("a1");
            }
            return Mono.just(saved);
        });
    }

    @Test
    void createVipSavingsCopiesProfileAppliesStrategyAndRecordsFirstDailyBalance() {
        when(customerClient.findById("c1")).thenReturn(Single.just(CustomerDto.builder().id("c1")
                .type(CustomerType.PERSONAL).profile(AccountProfile.VIP).documentType(DocumentType.DNI)
                .documentNumber("456").name("Valeria").build()));
        when(accountOpeningChain.validate(any())).thenReturn(Completable.complete());
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(Mono.just(false));
        saveReturnsArgument();
        when(dailyBalanceService.recordClosingBalance(eq("a1"), any())).thenReturn(Completable.complete());

        AccountRequest request = AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c1")
                .openingAmount(new BigDecimal("2000")).build();

        service.create(request).test().awaitDone(1, TimeUnit.SECONDS).assertValue(response ->
                response.getProfile() == AccountProfile.VIP
                        && response.getBalance().compareTo(new BigDecimal("2000")) == 0
                        && response.getOpeningAmount().compareTo(new BigDecimal("2000")) == 0
                        && response.getMinimumDailyAverage().compareTo(new BigDecimal("1000")) == 0
                        && response.getFreeTransactionLimit() == 5
                        && response.getAccountNumber().matches("\\d{14}")
                        && "456".equals(response.getHolders().get(0).getDocumentNumber()));
        verify(dailyBalanceService).recordClosingBalance(eq("a1"), eq(new BigDecimal("2000")));
    }

    @Test
    void createBusinessUsesRequestHoldersAndStandardProfileWhenCustomerHasNone() {
        when(customerClient.findById("c1"))
                .thenReturn(Single.just(CustomerDto.builder().id("c1").type(CustomerType.BUSINESS).build()));
        when(accountOpeningChain.validate(any())).thenReturn(Completable.complete());
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(Mono.just(true), Mono.just(false));
        saveReturnsArgument();
        when(dailyBalanceService.recordClosingBalance(any(), any())).thenReturn(Completable.complete());
        AccountRequest request = AccountRequest.builder().accountType(AccountType.CURRENT).customerId("c1")
                .holders(List.of(HolderDto.builder().documentType(DocumentType.DNI).documentNumber("9")
                        .fullName("Jorge").build()))
                .build();

        service.create(request).test().awaitDone(1, TimeUnit.SECONDS).assertValue(response ->
                response.getProfile() == AccountProfile.STANDARD
                        && response.getBalance().signum() == 0
                        && response.getMaintenanceFee().compareTo(new BigDecimal("10.00")) == 0
                        && "9".equals(response.getHolders().get(0).getDocumentNumber()));
    }

    @Test
    void createStopsWhenTheChainRejects() {
        when(customerClient.findById("c1"))
                .thenReturn(Single.just(CustomerDto.builder().id("c1").type(CustomerType.PERSONAL).build()));
        when(accountOpeningChain.validate(any()))
                .thenReturn(Completable.error(new BusinessRuleException("requires a credit card")));

        service.create(AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c1").build()).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void findAllFiltersByAccountNumberCustomerOrNothing() {
        Account account = account("a1", AccountType.SAVINGS, "10");
        when(accountRepository.findByAccountNumber("123")).thenReturn(Mono.just(account));
        when(accountRepository.findByCustomerId("c1")).thenReturn(Flux.just(account, account));
        when(accountRepository.findAll()).thenReturn(Flux.just(account, account, account));

        service.findAll("c1", "123").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll("c1", " ").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(2);
        service.findAll(null, null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(3);
    }

    @Test
    void findAllByUnknownAccountNumberIsEmpty() {
        when(accountRepository.findByAccountNumber("0")).thenReturn(Mono.empty());

        service.findAll(null, "0").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues().assertComplete();
    }

    @Test
    void findByIdAndBalance() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.SAVINGS, "55")));
        when(accountRepository.findById("x")).thenReturn(Mono.empty());

        service.findById("a1").test().awaitDone(1, TimeUnit.SECONDS).assertValue(r -> "a1".equals(r.getId()));
        service.getBalance("a1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getBalance().compareTo(new BigDecimal("55")) == 0);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateMergesEditableFieldsButNotTheBalance() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));
        saveReturnsArgument();

        service.update("a1", AccountRequest.builder().accountType(AccountType.CURRENT).customerId("c1")
                        .maintenanceFee(new BigDecimal("15")).openingAmount(new BigDecimal("999")).build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getMaintenanceFee().compareTo(new BigDecimal("15")) == 0
                        && r.getBalance().compareTo(new BigDecimal("100")) == 0);
    }

    @Test
    void updateRejectsChangingTypeOrCustomer() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));

        service.update("a1", AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c1").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("accountType cannot be modified"));
        service.update("a1", AccountRequest.builder().accountType(AccountType.CURRENT).customerId("c2").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("customerId cannot be modified"));
    }

    @Test
    void deleteAlsoRemovesDailyBalanceHistory() {
        Account account = account("a1", AccountType.SAVINGS, "0");
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account));
        when(accountRepository.delete(account)).thenReturn(Mono.empty());
        when(dailyBalanceService.deleteHistory("a1")).thenReturn(Completable.complete());

        service.delete("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(dailyBalanceService).deleteHistory("a1");
    }

    @Test
    void depositSubtractsTheFeeAndRecordsTheDailyBalance() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));
        saveReturnsArgument();
        when(dailyBalanceService.recordClosingBalance(eq("a1"), any())).thenReturn(Completable.complete());

        service.applyBalanceOperation("a1", BalanceOperationRequest.builder().operation(BalanceOperationType.DEPOSIT)
                        .amount(new BigDecimal("10")).fee(new BigDecimal("1.50")).build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getBalance().compareTo(new BigDecimal("108.50")) == 0);
        ArgumentCaptor<BigDecimal> balance = ArgumentCaptor.forClass(BigDecimal.class);
        verify(dailyBalanceService).recordClosingBalance(eq("a1"), balance.capture());
        assertThat(balance.getValue()).isEqualByComparingTo("108.50");
    }

    @Test
    void withdrawalAddsTheFeeAndWithoutFeeDefaultsToZero() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));
        saveReturnsArgument();
        when(dailyBalanceService.recordClosingBalance(eq("a1"), any())).thenReturn(Completable.complete());

        service.applyBalanceOperation("a1", BalanceOperationRequest.builder()
                        .operation(BalanceOperationType.WITHDRAWAL).amount(new BigDecimal("40")).build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getBalance().compareTo(new BigDecimal("60")) == 0);
    }

    @Test
    void withdrawalPlusFeeAboveBalanceIsRejected() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));

        service.applyBalanceOperation("a1", BalanceOperationRequest.builder()
                        .operation(BalanceOperationType.WITHDRAWAL).amount(new BigDecimal("99"))
                        .fee(new BigDecimal("1.50")).build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("Insufficient funds"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void averageDailyBalanceReportsWhetherTheVipMinimumIsMet() {
        Account vip = account("a1", AccountType.SAVINGS, "0");
        vip.setMinimumDailyAverage(new BigDecimal("1000"));
        Account standard = account("a2", AccountType.SAVINGS, "0");
        when(accountRepository.findById("a1")).thenReturn(Mono.just(vip));
        when(accountRepository.findById("a2")).thenReturn(Mono.just(standard));
        when(dailyBalanceService.averageForMonth(eq("a1"), any(), any()))
                .thenReturn(Single.just(new BigDecimal("900.00")));
        when(dailyBalanceService.averageForMonth(eq("a2"), any(), any()))
                .thenReturn(Single.just(new BigDecimal("50.00")));

        service.getAverageDailyBalance("a1", YearMonth.of(2026, 10)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> Boolean.FALSE.equals(r.getMeetsMinimum()) && "2026-10".equals(r.getMonth()));
        service.getAverageDailyBalance("a2", YearMonth.of(2026, 10)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getMeetsMinimum() == null && r.getMinimumDailyAverage() == null);
    }

    // --- Parte III: eventos de cuentas ----------------------------------------

    @Test
    void updateAndDeletePublishSnapshots() {
        Account account = account("a1", AccountType.CURRENT, "100");
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account));
        saveReturnsArgument();
        when(accountRepository.delete(account)).thenReturn(Mono.empty());
        when(dailyBalanceService.deleteHistory("a1")).thenReturn(Completable.complete());

        service.update("a1", AccountRequest.builder().accountType(AccountType.CURRENT).customerId("c1").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.delete("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(accountEventPublisher).publishUpserted(account);
        verify(accountEventPublisher).publishDeleted(account);
    }

    @Test
    void kafkaFailuresDoNotUndoAStoredAccount() {
        when(accountRepository.findById("a1")).thenReturn(Mono.just(account("a1", AccountType.CURRENT, "100")));
        saveReturnsArgument();
        when(accountEventPublisher.publishUpserted(any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.update("a1", AccountRequest.builder().accountType(AccountType.CURRENT).customerId("c1").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete().assertValueCount(1);
    }
}
