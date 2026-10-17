package com.bank.account.service.opening;

import com.bank.account.client.CreditCardClient;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import org.springframework.stereotype.Component;

/**
 * Eslabon 5: un cliente PYME solo puede abrir cuenta corriente y debe tener al
 * menos una tarjeta de credito en el banco. La comision de mantenimiento cero
 * la aplica la estrategia {@code PymeCurrentAccountRule}.
 */
@Component
public class PymeProfileHandler extends ProfileRequirementHandler {

    /**
     * Inyeccion por constructor.
     *
     * @param creditCardClient cliente hacia {@code bank-credit-card-service}
     */
    public PymeProfileHandler(CreditCardClient creditCardClient) {
        super(creditCardClient, AccountProfile.PYME, AccountType.CURRENT);
    }
}
