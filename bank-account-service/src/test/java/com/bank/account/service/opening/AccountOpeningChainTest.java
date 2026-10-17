package com.bank.account.service.opening;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.TestData;
import com.bank.account.client.CreditCardClient;
import com.bank.account.client.CustomerDto;
import com.bank.account.client.CustomerType;
import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.HolderDto;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ServiceUnavailableException;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import com.bank.account.model.DocumentType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.OverdueDebtService;
import com.bank.account.service.rule.AccountRuleFactory;
import com.bank.account.service.rule.CurrentAccountRule;
import com.bank.account.service.rule.FixedTermAccountRule;
import com.bank.account.service.rule.PymeCurrentAccountRule;
import com.bank.account.service.rule.SavingsAccountRule;
import com.bank.account.service.rule.VipSavingsAccountRule;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * Prueba la cadena de validaciones completa con eslabones y estrategias reales.
 */
@ExtendWith(MockitoExtension.class)
class AccountOpeningChainTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CreditCardClient creditCardClient;

    @Mock
    private OverdueDebtService overdueDebtService;

    private AccountOpeningChain chain;
    private AccountRuleFactory factory;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(overdueDebtService.ensureNoOverdueDebt(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.rxjava3.core.Completable.complete());
        var properties = TestData.accountProperties();
        SavingsAccountRule savings = new SavingsAccountRule(properties);
        CurrentAccountRule current = new CurrentAccountRule(properties);
        factory = new AccountRuleFactory(savings, current, new FixedTermAccountRule(properties),
                new VipSavingsAccountRule(savings, properties), new PymeCurrentAccountRule(current, properties));
        chain = new AccountOpeningChain(new OverdueDebtHandler(overdueDebtService),
                new CustomerTypeRuleHandler(accountRepository), new AccountTypeRuleHandler(),
                new OpeningAmountHandler(), new VipProfileHandler(creditCardClient),
                new PymeProfileHandler(creditCardClient));
    }

    private AccountOpeningContext context(CustomerType type, AccountProfile profile, AccountType accountType,
                                          String openingAmount) {
        AccountRequest request = AccountRequest.builder().accountType(accountType).customerId("c1")
                .openingAmount(openingAmount == null ? null : new BigDecimal(openingAmount)).build();
        if (type == CustomerType.BUSINESS) {
            request.setHolders(List.of(HolderDto.builder().documentType(DocumentType.DNI).documentNumber("1")
                    .fullName("Ana").build()));
        }
        return AccountOpeningContext.builder()
                .request(request)
                .customer(CustomerDto.builder().id("c1").type(type).profile(profile).build())
                .profile(profile)
                .rule(factory.getRule(accountType, profile))
                .build();
    }

    @Test
    void vipWithoutCreditCardIsRejected() {
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.SAVINGS)).thenReturn(Mono.just(0L));
        when(creditCardClient.hasCreditCard("c1")).thenReturn(Single.just(false));

        chain.validate(context(CustomerType.PERSONAL, AccountProfile.VIP, AccountType.SAVINGS, "1000")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("requires the customer to hold a credit card"));
    }

    @Test
    void vipWithCreditCardAndEnoughOpeningAmountPasses() {
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.SAVINGS)).thenReturn(Mono.just(0L));
        when(creditCardClient.hasCreditCard("c1")).thenReturn(Single.just(true));

        chain.validate(context(CustomerType.PERSONAL, AccountProfile.VIP, AccountType.SAVINGS, "500")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void vipBelowItsMinimumOpeningAmountIsRejectedBeforeCheckingCards() {
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.SAVINGS)).thenReturn(Mono.just(0L));

        chain.validate(context(CustomerType.PERSONAL, AccountProfile.VIP, AccountType.SAVINGS, "100")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("opening amount must be at least 500"));
        verify(creditCardClient, never()).hasCreditCard(any());
    }

    @Test
    void vipCanOnlyOpenSavings() {
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.CURRENT)).thenReturn(Mono.just(0L));

        chain.validate(context(CustomerType.PERSONAL, AccountProfile.VIP, AccountType.CURRENT, "100")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("VIP customer can only open SAVINGS"));
    }

    @Test
    void pymeWithoutCreditCardIsRejected() {
        when(creditCardClient.hasCreditCard("c1")).thenReturn(Single.just(false));

        chain.validate(context(CustomerType.BUSINESS, AccountProfile.PYME, AccountType.CURRENT, "0")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("PYME account requires"));
    }

    @Test
    void pymeWithCreditCardPasses() {
        when(creditCardClient.hasCreditCard("c1")).thenReturn(Single.just(true));

        chain.validate(context(CustomerType.BUSINESS, AccountProfile.PYME, AccountType.CURRENT, null)).test()
                .awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void cardServiceDownPropagates503() {
        when(creditCardClient.hasCreditCard("c1"))
                .thenReturn(Single.error(new ServiceUnavailableException("bank-credit-card-service is not available")));

        chain.validate(context(CustomerType.BUSINESS, AccountProfile.PYME, AccountType.CURRENT, "0")).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(ServiceUnavailableException.class);
    }

    @Test
    void openingBelowConfiguredMinimumIsRejected() {
        var properties = TestData.accountProperties();
        properties.getDefaults().getSavings().setMinimumOpeningAmount(new BigDecimal("100"));
        SavingsAccountRule savings = new SavingsAccountRule(properties);
        AccountOpeningContext context = AccountOpeningContext.builder()
                .request(AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c1")
                        .openingAmount(new BigDecimal("50")).build())
                .customer(CustomerDto.builder().type(CustomerType.PERSONAL).build())
                .profile(AccountProfile.STANDARD)
                .rule(savings)
                .build();
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.SAVINGS)).thenReturn(Mono.just(0L));

        chain.validate(context).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("at least 100"));
    }

    @Test
    void personalSecondSavingsIsRejected() {
        when(accountRepository.countByCustomerIdAndAccountType("c1", AccountType.SAVINGS)).thenReturn(Mono.just(1L));

        chain.validate(context(CustomerType.PERSONAL, AccountProfile.STANDARD, AccountType.SAVINGS, "0")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only hold one SAVINGS"));
    }

    @Test
    void personalFixedTermRequiresMovementDay() {
        chain.validate(context(CustomerType.PERSONAL, AccountProfile.STANDARD, AccountType.FIXED_TERM, "0")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("movementDay is required"));
    }

    @Test
    void businessCannotOpenSavingsAndNeedsHolders() {
        chain.validate(context(CustomerType.BUSINESS, AccountProfile.STANDARD, AccountType.SAVINGS, "0")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only hold CURRENT"));

        AccountOpeningContext noHolders = context(CustomerType.BUSINESS, AccountProfile.STANDARD,
                AccountType.CURRENT, "0");
        noHolders.getRequest().setHolders(List.of());
        chain.validate(noHolders).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("at least one holder"));
    }

    @Test
    void customerWithoutTypeIsAServiceError() {
        AccountOpeningContext context = AccountOpeningContext.builder()
                .request(AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c1").build())
                .customer(CustomerDto.builder().id("c1").build())
                .profile(AccountProfile.STANDARD)
                .rule(factory.getRule(AccountType.SAVINGS, AccountProfile.STANDARD))
                .build();

        chain.validate(context).test().awaitDone(1, TimeUnit.SECONDS).assertError(ServiceUnavailableException.class);
    }

    @Test
    void customerWithOverdueDebtCannotOpenAccounts() {
        org.mockito.Mockito.when(overdueDebtService.ensureNoOverdueDebt("c-debt")).thenReturn(
                io.reactivex.rxjava3.core.Completable.error(new BusinessRuleException("has an overdue debt")));
        AccountOpeningContext context = AccountOpeningContext.builder()
                .request(AccountRequest.builder().accountType(AccountType.SAVINGS).customerId("c-debt").build())
                .customer(CustomerDto.builder().id("c-debt").type(CustomerType.PERSONAL).build())
                .profile(AccountProfile.STANDARD)
                .build();

        chain.validate(context).test().awaitDone(1, java.util.concurrent.TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("overdue debt"));
        org.mockito.Mockito.verifyNoInteractions(accountRepository);
    }
}
