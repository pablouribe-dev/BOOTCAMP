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
 * Estrategia de la cuenta corriente PYME: igual que la corriente estandar pero
 * sin comision de mantenimiento.
 *
 * <p>Reutiliza {@link CurrentAccountRule} por composicion.</p>
 */
@Component
@RequiredArgsConstructor
public class PymeCurrentAccountRule implements AccountRule {

    private final CurrentAccountRule currentAccountRule;
    private final AccountProperties accountProperties;

    /**
     * {@inheritDoc}
     *
     * <p>Usa el minimo del perfil PYME; si no esta configurado, el de corriente.</p>
     */
    @Override
    public BigDecimal minimumOpeningAmount() {
        return Optional.ofNullable(accountProperties.getPyme().getMinimumOpeningAmount())
                .orElseGet(currentAccountRule::minimumOpeningAmount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable validate(AccountRequest request) {
        return currentAccountRule.validate(request);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Toma los valores de la corriente y fuerza la comision de mantenimiento
     * a cero, aunque venga otra en la peticion.</p>
     */
    @Override
    public void applyDefaults(Account account, AccountRequest request) {
        currentAccountRule.applyDefaults(account, request);
        account.setMaintenanceFee(BigDecimal.ZERO);
    }
}
