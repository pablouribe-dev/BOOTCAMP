package com.bank.debitcard.service;

import com.bank.debitcard.event.AccountEventPayload;
import com.bank.debitcard.event.CustomerEventPayload;
import io.reactivex.rxjava3.core.Completable;

/**
 * Copias locales de clientes y cuentas, alimentadas por eventos.
 */
public interface ReplicaService {

    /**
     * Guarda o reemplaza el snapshot de un cliente.
     *
     * @param payload snapshot
     * @return completable que termina cuando la copia quedo actualizada
     */
    Completable upsertCustomer(CustomerEventPayload payload);

    /**
     * Elimina la copia de un cliente.
     *
     * @param customerId identificador del cliente
     * @return completable que termina cuando se elimino
     */
    Completable deleteCustomer(String customerId);

    /**
     * Guarda o reemplaza el snapshot de una cuenta.
     *
     * @param payload snapshot
     * @return completable que termina cuando la copia quedo actualizada
     */
    Completable upsertAccount(AccountEventPayload payload);

    /**
     * Elimina la copia de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return completable que termina cuando se elimino
     */
    Completable deleteAccount(String accountId);
}
