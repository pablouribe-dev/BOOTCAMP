package com.bank.creditcard.service;

import com.bank.creditcard.dto.CardBalanceResponse;
import com.bank.creditcard.dto.CardOperationRequest;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.dto.CreditCardResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Reglas de negocio de las tarjetas de credito.
 */
public interface CreditCardService {

    /**
     * Emite una tarjeta nueva, verificando que su tipo coincida con el tipo del
     * cliente.
     *
     * @param request datos de la tarjeta
     * @return la tarjeta creada
     */
    Single<CreditCardResponse> create(CreditCardRequest request);

    /**
     * Lista las tarjetas, opcionalmente filtradas por cliente.
     *
     * @param customerId identificador del cliente, o {@code null} para listar todas
     * @return flujo de tarjetas
     */
    Flowable<CreditCardResponse> findAll(String customerId);

    /**
     * Busca una tarjeta por su identificador.
     *
     * @param id identificador de la tarjeta
     * @return la tarjeta encontrada
     */
    Single<CreditCardResponse> findById(String id);

    /**
     * Actualiza los datos editables de una tarjeta.
     *
     * @param id      identificador de la tarjeta
     * @param request datos nuevos
     * @return la tarjeta actualizada
     */
    Single<CreditCardResponse> update(String id, CreditCardRequest request);

    /**
     * Elimina una tarjeta.
     *
     * @param id identificador de la tarjeta
     * @return completable que termina cuando la tarjeta fue eliminada
     */
    Completable delete(String id);

    /**
     * Consulta el saldo disponible de una tarjeta.
     *
     * @param id identificador de la tarjeta
     * @return linea, consumo y saldo disponible
     */
    Single<CardBalanceResponse> getBalance(String id);

    /**
     * Aplica un consumo o un pago sobre el importe usado de la tarjeta.
     *
     * <p>Operacion interna invocada por {@code bank-transaction-service}.</p>
     *
     * @param id      identificador de la tarjeta
     * @param request operacion e importe
     * @return saldo disponible resultante
     */
    Single<CardBalanceResponse> applyBalanceOperation(String id, CardOperationRequest request);
}
