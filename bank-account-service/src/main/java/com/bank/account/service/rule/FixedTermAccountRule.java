package com.bank.account.service.rule;

import com.bank.account.config.AccountProperties;
import com.bank.account.dto.AccountRequest;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.model.Account;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Estrategia de la cuenta a plazo fijo: sin comision de mantenimiento y un
 * unico movimiento al mes, en el dia {@code movementDay}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedTermAccountRule implements AccountRule {

    private final AccountProperties accountProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public BigDecimal minimumOpeningAmount() {
        return accountProperties.getDefaults().getFixedTerm().getMinimumOpeningAmount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exige el dia del mes en que la cuenta admite su movimiento.</p>
     */
    @Override
    public Completable validate(AccountRequest request) {
        if (request.getMovementDay() == null) {
            log.warn("FIXED_TERM account for customer {} came without movementDay", request.getCustomerId());
            return Completable.error(new BusinessRuleException("movementDay is required for FIXED_TERM accounts"));
        }
        return Completable.complete();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyDefaults(Account account, AccountRequest request) {
        AccountProperties.TypeSettings fixedTerm = accountProperties.getDefaults().getFixedTerm();
        account.setMaintenanceFee(BigDecimal.ZERO);
        account.setMonthlyMovementLimit(fixedTerm.getMonthlyMovementLimit());
        account.setMovementDay(request.getMovementDay());
        account.setFreeTransactionLimit(fixedTerm.getFreeTransactionLimit());
        account.setTransactionFee(fixedTerm.getTransactionFee());
        account.setMinimumDailyAverage(null);
    }
}
