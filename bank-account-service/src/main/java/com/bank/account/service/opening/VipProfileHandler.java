package com.bank.account.service.opening;

import com.bank.account.client.CreditCardClient;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import org.springframework.stereotype.Component;

/**
 * Eslabon 4: un cliente VIP solo puede abrir cuenta de ahorro y debe tener al
 * menos una tarjeta de credito en el banco.
 */
@Component
public class VipProfileHandler extends ProfileRequirementHandler {

    /**
     * Inyeccion por constructor.
     *
     * @param creditCardClient cliente hacia {@code bank-credit-card-service}
     */
    public VipProfileHandler(CreditCardClient creditCardClient) {
        super(creditCardClient, AccountProfile.VIP, AccountType.SAVINGS);
    }
}
