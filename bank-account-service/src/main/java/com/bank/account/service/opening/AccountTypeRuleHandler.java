package com.bank.account.service.opening;

import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

/**
 * Eslabon 2: validaciones propias del tipo de cuenta, delegadas en la
 * estrategia elegida (por ejemplo, el dia de movimiento del plazo fijo).
 */
@Component
public class AccountTypeRuleHandler extends AccountOpeningHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Completable check(AccountOpeningContext context) {
        return context.getRule().validate(context.getRequest());
    }
}
