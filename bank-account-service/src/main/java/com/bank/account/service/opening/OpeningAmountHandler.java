package com.bank.account.service.opening;

import com.bank.account.exception.BusinessRuleException;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Eslabon 3: el monto de apertura debe ser mayor o igual al minimo del tipo
 * de cuenta y perfil (el minimo puede ser cero).
 */
@Slf4j
@Component
public class OpeningAmountHandler extends AccountOpeningHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Completable check(AccountOpeningContext context) {
        BigDecimal openingAmount = Optional.ofNullable(context.getRequest().getOpeningAmount())
                .orElse(BigDecimal.ZERO);
        BigDecimal minimum = context.getRule().minimumOpeningAmount();
        if (openingAmount.compareTo(minimum) < 0) {
            log.warn("Opening amount {} is below the minimum {} for a {} {} account",
                    openingAmount, minimum, context.getProfile(), context.getRequest().getAccountType());
            return Completable.error(new BusinessRuleException("The opening amount must be at least " + minimum
                    + " for a " + context.getProfile() + " " + context.getRequest().getAccountType() + " account"));
        }
        return Completable.complete();
    }
}
