package com.bank.account.service.rule;

import com.bank.account.config.AccountProperties;
import com.bank.account.dto.AccountRequest;
import com.bank.account.model.Account;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Estrategia de la cuenta de ahorro VIP: igual que la de ahorro estandar, mas
 * un promedio diario minimo mensual y su propio minimo de apertura.
 *
 * <p>Reutiliza {@link SavingsAccountRule} por composicion.</p>
 */
@Component
@RequiredArgsConstructor
public class VipSavingsAccountRule implements AccountRule {

    private final SavingsAccountRule savingsAccountRule;
    private final AccountProperties accountProperties;

    /**
     * {@inheritDoc}
     *
     * <p>Usa el minimo del perfil VIP; si no esta configurado, el de ahorro.</p>
     */
    @Override
    public BigDecimal minimumOpeningAmount() {
        return Optional.ofNullable(accountProperties.getVip().getMinimumOpeningAmount())
                .orElseGet(savingsAccountRule::minimumOpeningAmount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable validate(AccountRequest request) {
        return savingsAccountRule.validate(request);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ademas de los valores de ahorro, guarda el promedio diario minimo.</p>
     */
    @Override
    public void applyDefaults(Account account, AccountRequest request) {
        savingsAccountRule.applyDefaults(account, request);
        account.setMinimumDailyAverage(accountProperties.getVip().getMinimumDailyAverage());
    }
}
