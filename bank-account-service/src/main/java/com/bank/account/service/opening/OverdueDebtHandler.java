package com.bank.account.service.opening;

import com.bank.account.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Eslabon 0 (Parte III): un cliente con deuda vencida no puede abrir cuentas.
 *
 * <p>La consulta es contra la copia local {@code overdue_debts}, sin REST.</p>
 */
@Component
@RequiredArgsConstructor
public class OverdueDebtHandler extends AccountOpeningHandler {

    private final OverdueDebtService overdueDebtService;

    /**
     * {@inheritDoc}
     */
    @Override
    protected Completable check(AccountOpeningContext context) {
        return overdueDebtService.ensureNoOverdueDebt(context.getRequest().getCustomerId());
    }
}
