package com.bank.account.service.opening;

import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

/**
 * Arma la cadena de validaciones de apertura en el orden de la guia:
 *
 * <ol>
 *   <li>Deuda vencida (Parte III, copia local).</li>
 *   <li>Reglas de la Parte I segun tipo de cliente.</li>
 *   <li>Reglas propias del tipo de cuenta (estrategia).</li>
 *   <li>Monto de apertura.</li>
 *   <li>Perfil VIP.</li>
 *   <li>Perfil PYME.</li>
 * </ol>
 */
@Component
public class AccountOpeningChain {

    private final AccountOpeningHandler first;

    /**
     * Enlaza los eslabones.
     *
     * @param overdueDebtHandler      eslabon de deuda vencida (Parte III)
     * @param customerTypeRuleHandler eslabon de reglas por tipo de cliente
     * @param accountTypeRuleHandler  eslabon de reglas por tipo de cuenta
     * @param openingAmountHandler    eslabon de monto de apertura
     * @param vipProfileHandler       eslabon del perfil VIP
     * @param pymeProfileHandler      eslabon del perfil PYME
     */
    public AccountOpeningChain(OverdueDebtHandler overdueDebtHandler,
                               CustomerTypeRuleHandler customerTypeRuleHandler,
                               AccountTypeRuleHandler accountTypeRuleHandler,
                               OpeningAmountHandler openingAmountHandler,
                               VipProfileHandler vipProfileHandler,
                               PymeProfileHandler pymeProfileHandler) {
        overdueDebtHandler.linkWith(customerTypeRuleHandler)
                .linkWith(accountTypeRuleHandler)
                .linkWith(openingAmountHandler)
                .linkWith(vipProfileHandler)
                .linkWith(pymeProfileHandler);
        this.first = overdueDebtHandler;
    }

    /**
     * Recorre la cadena completa.
     *
     * @param context datos de la apertura
     * @return completable que falla con el error del primer eslabon que no pasa
     */
    public Completable validate(AccountOpeningContext context) {
        return first.handle(context);
    }
}
