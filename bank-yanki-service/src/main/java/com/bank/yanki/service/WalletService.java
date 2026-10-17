package com.bank.yanki.service;

import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.dto.WalletResponse;
import com.bank.yanki.event.DebitCardEventPayload;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Monederos Yanki y copia local de tarjetas de debito.
 */
public interface WalletService {

    /**
     * Crea un monedero con saldo cero. Documento y celular son unicos.
     *
     * @param request datos del monedero
     * @return el monedero creado
     */
    Single<WalletResponse> create(WalletRequest request);

    /**
     * Lista monederos, opcionalmente el de un celular.
     *
     * @param phoneNumber filtro opcional
     * @return monederos
     */
    Flowable<WalletResponse> findAll(String phoneNumber);

    /**
     * Busca un monedero.
     *
     * @param id identificador
     * @return el monedero
     */
    Single<WalletResponse> findById(String id);

    /**
     * Actualiza los datos del monedero (no el saldo ni la tarjeta).
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return el monedero actualizado
     */
    Single<WalletResponse> update(String id, WalletRequest request);

    /**
     * Elimina un monedero.
     *
     * @param id identificador
     * @return completable que termina cuando se elimino
     */
    Completable delete(String id);

    /**
     * Asocia una tarjeta de debito: debe existir en la copia local y estar
     * {@code ACTIVE}.
     *
     * @param id         monedero
     * @param cardNumber numero de tarjeta
     * @return el monedero actualizado
     */
    Single<WalletResponse> linkDebitCard(String id, String cardNumber);

    /**
     * Quita la tarjeta asociada.
     *
     * @param id monedero
     * @return el monedero actualizado
     */
    Single<WalletResponse> unlinkDebitCard(String id);

    /**
     * Guarda o reemplaza la copia local de una tarjeta.
     *
     * @param payload snapshot de la tarjeta
     * @return completable que termina cuando la copia quedo actualizada
     */
    Completable upsertDebitCard(DebitCardEventPayload payload);

    /**
     * Elimina la copia local de una tarjeta.
     *
     * @param cardId identificador de la tarjeta
     * @return completable que termina cuando se elimino
     */
    Completable deleteDebitCard(String cardId);
}
