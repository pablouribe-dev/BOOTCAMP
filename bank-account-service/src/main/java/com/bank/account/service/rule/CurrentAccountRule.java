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
 * Estrategia de la cuenta corriente estandar: con comision de mantenimiento y
 * sin limite de movimientos.
 */
@Component
@RequiredArgsConstructor
public class CurrentAccountRule implements AccountRule {

    private final AccountProperties accountProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public BigDecimal minimumOpeningAmount() {
        return accountProperties.getDefaults().getCurrent().getMinimumOpeningAmount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>La cuenta corriente no tiene validaciones propias.</p>
     */
    @Override
    public Completable validate(AccountRequest request) {
        return Completable.complete();
    }

    /**
     * {@inheritDoc}
     *
     * <p>La comision de mantenimiento puede venir en la peticion; si no, se usa
     * la configurada.</p>
     */
    @Override
    public void applyDefaults(Account account, AccountRequest request) {
        AccountProperties.TypeSettings current = accountProperties.getDefaults().getCurrent();
        account.setMaintenanceFee(Optional.ofNullable(request.getMaintenanceFee())
                .orElse(current.getMaintenanceFee()));
        account.setMonthlyMovementLimit(null);
        account.setMovementDay(null);
        account.setFreeTransactionLimit(current.getFreeTransactionLimit());
        account.setTransactionFee(current.getTransactionFee());
        account.setMinimumDailyAverage(null);
    }
}
