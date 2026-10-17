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
 * Estrategia de la cuenta de ahorro estandar: sin comision de mantenimiento y
 * con limite maximo de movimientos mensuales.
 */
@Component
@RequiredArgsConstructor
public class SavingsAccountRule implements AccountRule {

    private final AccountProperties accountProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public BigDecimal minimumOpeningAmount() {
        return accountProperties.getDefaults().getSavings().getMinimumOpeningAmount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>La cuenta de ahorro no tiene validaciones propias.</p>
     */
    @Override
    public Completable validate(AccountRequest request) {
        return Completable.complete();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDefaults(Account account, AccountRequest request) {
        AccountProperties.TypeSettings savings = accountProperties.getDefaults().getSavings();
        account.setMaintenanceFee(BigDecimal.ZERO);
        account.setMonthlyMovementLimit(Optional.ofNullable(request.getMonthlyMovementLimit())
                .orElse(savings.getMonthlyMovementLimit()));
        account.setMovementDay(null);
        account.setFreeTransactionLimit(savings.getFreeTransactionLimit());
        account.setTransactionFee(savings.getTransactionFee());
        account.setMinimumDailyAverage(null);
    }
}
