package com.bank.debitcard.service;

import com.bank.debitcard.dto.DebitCardRequest;
import com.bank.debitcard.dto.DebitCardResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Reglas de negocio de las tarjetas de debito. Todas las validaciones usan
 * las copias locales (sin REST).
 */
public interface DebitCardService {

    /**
     * Emite una tarjeta: el cliente y las cuentas deben existir en las copias
     * locales, las cuentas deben ser del cliente, la principal debe estar entre
     * ellas y el cliente no puede tener deuda vencida.
     *
     * @param request datos de la tarjeta
     * @return la tarjeta creada
     */
    Single<DebitCardResponse> create(DebitCardRequest request);

    /**
     * Lista tarjetas, opcionalmente de un cliente.
     *
     * @param customerId filtro opcional
     * @return flujo de tarjetas
     */
    Flowable<DebitCardResponse> findAll(String customerId);

    /**
     * Busca una tarjeta.
     *
     * @param id identificador
     * @return la tarjeta
     */
    Single<DebitCardResponse> findById(String id);

    /**
     * Actualiza cuentas, cuenta principal y estado. El cliente no cambia.
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return la tarjeta actualizada
     */
    Single<DebitCardResponse> update(String id, DebitCardRequest request);

    /**
     * Elimina una tarjeta.
     *
     * @param id identificador
     * @return completable que termina cuando se elimino
     */
    Completable delete(String id);

    /**
     * Asocia otra cuenta del mismo cliente (al final del orden).
     *
     * @param id        identificador de la tarjeta
     * @param accountId cuenta a asociar
     * @return la tarjeta actualizada
     */
    Single<DebitCardResponse> addAccount(String id, String accountId);

    /**
     * Cambia la cuenta principal por otra ya asociada.
     *
     * @param id        identificador de la tarjeta
     * @param accountId nueva cuenta principal
     * @return la tarjeta actualizada
     */
    Single<DebitCardResponse> changeMainAccount(String id, String accountId);

    /**
     * Quita una cuenta eliminada de todas las tarjetas que la tengan. Si era la
     * principal, pasa a serlo la primera que quede; si no queda ninguna, la
     * tarjeta queda {@code INACTIVE}.
     *
     * @param accountId cuenta eliminada
     * @return completable que termina cuando se actualizaron las tarjetas
     */
    Completable removeDeletedAccount(String accountId);
}
