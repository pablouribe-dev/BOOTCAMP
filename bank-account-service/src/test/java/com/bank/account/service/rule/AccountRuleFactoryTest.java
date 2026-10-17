package com.bank.account.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.account.TestData;
import com.bank.account.dto.AccountRequest;
import com.bank.account.model.Account;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountRuleFactoryTest {

    private AccountRuleFactory factory;

    @BeforeEach
    void setUp() {
        var properties = TestData.accountProperties();
        SavingsAccountRule savings = new SavingsAccountRule(properties);
        CurrentAccountRule current = new CurrentAccountRule(properties);
        factory = new AccountRuleFactory(savings, current, new FixedTermAccountRule(properties),
                new VipSavingsAccountRule(savings, properties), new PymeCurrentAccountRule(current, properties));
    }

    private Account apply(AccountType type, AccountProfile profile, AccountRequest request) {
        Account account = Account.builder().accountType(type).build();
        factory.getRule(type, profile).applyDefaults(account, request);
        return account;
    }

    @Test
    void selectsProfileSpecificRulesFirst() {
        assertThat(factory.getRule(AccountType.SAVINGS, AccountProfile.VIP)).isInstanceOf(VipSavingsAccountRule.class);
        assertThat(factory.getRule(AccountType.CURRENT, AccountProfile.PYME))
                .isInstanceOf(PymeCurrentAccountRule.class);
        assertThat(factory.getRule(AccountType.SAVINGS, AccountProfile.STANDARD))
                .isInstanceOf(SavingsAccountRule.class);
        assertThat(factory.getRule(AccountType.CURRENT, AccountProfile.VIP)).isInstanceOf(CurrentAccountRule.class);
        assertThat(factory.getRule(AccountType.FIXED_TERM, AccountProfile.PYME))
                .isInstanceOf(FixedTermAccountRule.class);
    }

    @Test
    void savingsDefaults() {
        Account account = apply(AccountType.SAVINGS, AccountProfile.STANDARD, new AccountRequest());

        assertThat(account.getMaintenanceFee()).isEqualByComparingTo("0");
        assertThat(account.getMonthlyMovementLimit()).isEqualTo(20);
        assertThat(account.getFreeTransactionLimit()).isEqualTo(5);
        assertThat(account.getTransactionFee()).isEqualByComparingTo("1.50");
        assertThat(account.getMinimumDailyAverage()).isNull();
    }

    @Test
    void currentUsesRequestedOrConfiguredMaintenanceFee() {
        assertThat(apply(AccountType.CURRENT, AccountProfile.STANDARD, new AccountRequest()).getMaintenanceFee())
                .isEqualByComparingTo("10.00");
        AccountRequest custom = AccountRequest.builder().maintenanceFee(new BigDecimal("7")).build();
        Account account = apply(AccountType.CURRENT, AccountProfile.STANDARD, custom);
        assertThat(account.getMaintenanceFee()).isEqualByComparingTo("7");
        assertThat(account.getMonthlyMovementLimit()).isNull();
        assertThat(account.getFreeTransactionLimit()).isEqualTo(10);
    }

    @Test
    void fixedTermKeepsMovementDayAndSingleMovement() {
        Account account = apply(AccountType.FIXED_TERM, AccountProfile.STANDARD,
                AccountRequest.builder().movementDay(15).build());

        assertThat(account.getMovementDay()).isEqualTo(15);
        assertThat(account.getMonthlyMovementLimit()).isEqualTo(1);
        assertThat(account.getMaintenanceFee()).isEqualByComparingTo("0");
    }

    @Test
    void vipAddsMinimumDailyAverageAndOwnOpeningMinimum() {
        Account account = apply(AccountType.SAVINGS, AccountProfile.VIP, new AccountRequest());

        assertThat(account.getMinimumDailyAverage()).isEqualByComparingTo("1000");
        assertThat(factory.getRule(AccountType.SAVINGS, AccountProfile.VIP).minimumOpeningAmount())
                .isEqualByComparingTo("500");
    }

    @Test
    void pymeForcesZeroMaintenanceFeeAndFallsBackToCurrentMinimum() {
        AccountRequest request = AccountRequest.builder().maintenanceFee(new BigDecimal("25")).build();
        Account account = apply(AccountType.CURRENT, AccountProfile.PYME, request);

        assertThat(account.getMaintenanceFee()).isEqualByComparingTo("0");
        assertThat(factory.getRule(AccountType.CURRENT, AccountProfile.PYME).minimumOpeningAmount())
                .isEqualByComparingTo("0");
    }
}
