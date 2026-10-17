package com.bank.customer.service;

import com.bank.customer.dto.RepublishResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Republica los snapshots de los clientes ya guardados, para poblar las copias
 * locales de otros servicios con datos anteriores a la Parte III.
 */
public interface EventRepublishService {

    /**
     * Publica un {@code CUSTOMER_UPSERTED} por cada cliente registrado.
     *
     * @return cantidad de eventos publicados
     */
    Single<RepublishResponse> republishAll();
}
