package com.bank.account.service.rule;

import com.bank.account.dto.AccountRequest;
import com.bank.account.model.Account;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;

/**
 * Patron Strategy: reglas propias de un tipo de cuenta y perfil.
 *
 * <p>Cada implementacion encapsula lo que cambia entre ahorro, corriente, plazo
 * fijo, ahorro VIP y corriente PYME: el minimo de apertura, las validaciones
 * del tipo y los valores por defecto (comisiones, limites). La estrategia
 * concreta la elige {@link AccountRuleFactory}.</p>
 */
public interface AccountRule {

    /**
     * Monto minimo con el que se puede abrir la cuenta.
     *
     * @return minimo de apertura; puede ser cero
     */
    BigDecimal minimumOpeningAmount();

    /**
     * Valida los datos propios del tipo de cuenta (por ejemplo, el dia de
     * movimiento de un plazo fijo).
     *
     * @param request datos de apertura
     * @return completable que falla con {@code BusinessRuleException} si algo
     *         no corresponde
     */
    Completable validate(AccountRequest request);

    /**
     * Completa la cuenta con comision de mantenimiento, limites, comision por
     * exceso de transacciones y demas valores del tipo y perfil.
     *
     * @param account cuenta en construccion
     * @param request datos de apertura
     */
    void applyDefaults(Account account, AccountRequest request);
}
