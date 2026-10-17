package com.bank.account.service.rule;

import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Patron Factory: entrega la estrategia {@link AccountRule} que corresponde a
 * un tipo de cuenta y un perfil de cliente.
 *
 * <p>Las combinaciones con regla propia (ahorro VIP, corriente PYME) tienen
 * prioridad; el resto usa la regla estandar del tipo. Que el perfil admita o no
 * el tipo de cuenta lo decide la cadena de validaciones, no la fabrica.</p>
 */
@Component
@RequiredArgsConstructor
public class AccountRuleFactory {

    private final SavingsAccountRule savingsAccountRule;
    private final CurrentAccountRule currentAccountRule;
    private final FixedTermAccountRule fixedTermAccountRule;
    private final VipSavingsAccountRule vipSavingsAccountRule;
    private final PymeCurrentAccountRule pymeCurrentAccountRule;

    /**
     * Selecciona la estrategia.
     *
     * @param type    tipo de cuenta
     * @param profile perfil del cliente
     * @return estrategia a aplicar
     */
    public AccountRule getRule(AccountType type, AccountProfile profile) {
        if (type == AccountType.SAVINGS && profile == AccountProfile.VIP) {
            return vipSavingsAccountRule;
        }
        if (type == AccountType.CURRENT && profile == AccountProfile.PYME) {
            return pymeCurrentAccountRule;
        }
        return switch (type) {
            case SAVINGS -> savingsAccountRule;
            case CURRENT -> currentAccountRule;
            case FIXED_TERM -> fixedTermAccountRule;
        };
    }
}
