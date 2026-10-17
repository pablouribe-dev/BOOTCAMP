package com.bank.account.service;

import com.bank.account.dto.RepublishResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Republica los snapshots de las cuentas ya guardadas, para poblar las copias
 * locales de otros servicios con datos anteriores a la Parte III.
 */
public interface EventRepublishService {

    /**
     * Publica un {@code ACCOUNT_UPSERTED} por cada cuenta registrada.
     *
     * @return cantidad de eventos publicados
     */
    Single<RepublishResponse> republishAll();
}
