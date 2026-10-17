package com.bank.credit.service;

import com.bank.credit.dto.CreditBalanceResponse;
import com.bank.credit.dto.CreditOperationRequest;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.dto.CreditResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Reglas de negocio de los creditos.
 */
public interface CreditService {

    /**
     * Otorga un credito nuevo aplicando las reglas del tipo de cliente.
     *
     * @param request datos del credito
     * @return el credito creado
     */
    Single<CreditResponse> create(CreditRequest request);

    /**
     * Lista los creditos, opcionalmente filtrados por cliente.
     *
     * @param customerId identificador del cliente, o {@code null} para listar todos
     * @return flujo de creditos
     */
    Flowable<CreditResponse> findAll(String customerId);

    /**
     * Busca un credito por su identificador.
     *
     * @param id identificador del credito
     * @return el credito encontrado
     */
    Single<CreditResponse> findById(String id);

    /**
     * Actualiza los datos editables de un credito.
     *
     * @param id      identificador del credito
     * @param request datos nuevos
     * @return el credito actualizado
     */
    Single<CreditResponse> update(String id, CreditRequest request);

    /**
     * Elimina un credito.
     *
     * @param id identificador del credito
     * @return completable que termina cuando el credito fue eliminado
     */
    Completable delete(String id);

    /**
     * Consulta el saldo pendiente de un credito.
     *
     * @param id identificador del credito
     * @return saldo pendiente
     */
    Single<CreditBalanceResponse> getBalance(String id);

    /**
     * Aplica un pago sobre el saldo pendiente del credito.
     *
     * <p>Operacion interna invocada por {@code bank-transaction-service}.</p>
     *
     * @param id      identificador del credito
     * @param request operacion e importe
     * @return saldo pendiente resultante
     */
    Single<CreditBalanceResponse> applyBalanceOperation(String id, CreditOperationRequest request);
}
