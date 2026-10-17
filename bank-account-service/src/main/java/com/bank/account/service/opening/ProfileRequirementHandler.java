package com.bank.account.service.opening;

import com.bank.account.client.CreditCardClient;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * Base de los eslabones de perfil (VIP y PYME): ambos restringen el tipo de
 * cuenta y exigen que el cliente tenga al menos una tarjeta de credito.
 */
@Slf4j
public abstract class ProfileRequirementHandler extends AccountOpeningHandler {

    private final CreditCardClient creditCardClient;
    private final AccountProfile profile;
    private final AccountType allowedType;

    /**
     * Configura el eslabon para un perfil.
     *
     * @param creditCardClient cliente hacia {@code bank-credit-card-service}
     * @param profile          perfil que valida este eslabon
     * @param allowedType      unico tipo de cuenta que admite el perfil
     */
    protected ProfileRequirementHandler(CreditCardClient creditCardClient, AccountProfile profile,
                                        AccountType allowedType) {
        this.creditCardClient = creditCardClient;
        this.profile = profile;
        this.allowedType = allowedType;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si el cliente no tiene este perfil, el eslabon no aplica.</p>
     */
    @Override
    protected Completable check(AccountOpeningContext context) {
        if (context.getProfile() != profile) {
            return Completable.complete();
        }
        if (context.getRequest().getAccountType() != allowedType) {
            log.warn("{} customer {} requested a {} account", profile, context.getRequest().getCustomerId(),
                    context.getRequest().getAccountType());
            return Completable.error(new BusinessRuleException(
                    "A " + profile + " customer can only open " + allowedType + " accounts"));
        }
        String customerId = context.getRequest().getCustomerId();
        return creditCardClient.hasCreditCard(customerId)
                .flatMapCompletable(hasCard -> {
                    if (Boolean.TRUE.equals(hasCard)) {
                        return Completable.complete();
                    }
                    log.warn("{} customer {} has no credit card", profile, customerId);
                    return Completable.error(new BusinessRuleException(
                            "A " + profile + " account requires the customer to hold a credit card"));
                });
    }
}
