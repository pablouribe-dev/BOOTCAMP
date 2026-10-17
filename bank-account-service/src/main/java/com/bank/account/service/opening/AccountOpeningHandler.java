package com.bank.account.service.opening;

import io.reactivex.rxjava3.core.Completable;

/**
 * Patron Chain of Responsibility: un eslabon de la cadena de validaciones que
 * se ejecuta al abrir una cuenta.
 *
 * <p>Cada eslabon hace su propia verificacion y, si pasa, delega en el
 * siguiente. El primer eslabon que falla corta la cadena con su error.</p>
 */
public abstract class AccountOpeningHandler {

    private AccountOpeningHandler next;

    /**
     * Enlaza el siguiente eslabon.
     *
     * @param nextHandler eslabon que se ejecuta despues de este
     * @return el eslabon enlazado, para encadenar llamadas
     */
    public AccountOpeningHandler linkWith(AccountOpeningHandler nextHandler) {
        this.next = nextHandler;
        return nextHandler;
    }

    /**
     * Ejecuta la verificacion de este eslabon y luego la del resto de la cadena.
     *
     * @param context datos de la apertura
     * @return completable que falla con el error del primer eslabon que no pasa
     */
    public Completable handle(AccountOpeningContext context) {
        return check(context).andThen(Completable.defer(() ->
                next == null ? Completable.complete() : next.handle(context)));
    }

    /**
     * Verificacion propia del eslabon.
     *
     * @param context datos de la apertura
     * @return completable que falla si la regla no se cumple
     */
    protected abstract Completable check(AccountOpeningContext context);
}
