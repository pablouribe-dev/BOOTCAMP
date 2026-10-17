package com.bank.transaction.service;

import com.bank.transaction.dto.AccountTransactionRequest;
import com.bank.transaction.dto.ChargeRequest;
import com.bank.transaction.dto.PaymentRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransactionUpdateRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Reglas de negocio de los movimientos.
 *
 * <p>Todas las operaciones que mueven saldo siguen el mismo patron: validar las
 * reglas del producto, pedir el cambio de saldo al servicio dueno y registrar
 * el movimiento solo si ese cambio fue exitoso.</p>
 */
public interface TransactionService {

    /**
     * Registra un deposito en una cuenta.
     *
     * @param request cuenta, importe y descripcion
     * @return el movimiento registrado
     */
    Single<TransactionResponse> deposit(AccountTransactionRequest request);

    /**
     * Registra un retiro de una cuenta.
     *
     * @param request cuenta, importe y descripcion
     * @return el movimiento registrado
     */
    Single<TransactionResponse> withdrawal(AccountTransactionRequest request);

    /**
     * Registra el pago de un credito o de una tarjeta de credito.
     *
     * @param request producto, importe y descripcion
     * @return el movimiento registrado
     */
    Single<TransactionResponse> payment(PaymentRequest request);

    /**
     * Registra un consumo con tarjeta de credito.
     *
     * @param request tarjeta, importe y descripcion
     * @return el movimiento registrado
     */
    Single<TransactionResponse> charge(ChargeRequest request);

    /**
     * Lista los movimientos, opcionalmente filtrados por producto.
     *
     * @param productId identificador del producto, o {@code null} para listar todos
     * @return flujo de movimientos
     */
    Flowable<TransactionResponse> findAll(String productId);

    /**
     * Busca un movimiento por su identificador.
     *
     * @param id identificador del movimiento
     * @return el movimiento encontrado
     */
    Single<TransactionResponse> findById(String id);

    /**
     * Corrige los datos de un movimiento ya registrado.
     *
     * <p>Operacion administrativa: <strong>no recalcula saldos</strong>.</p>
     *
     * @param id      identificador del movimiento
     * @param request datos corregidos
     * @return el movimiento actualizado
     */
    Single<TransactionResponse> update(String id, TransactionUpdateRequest request);

    /**
     * Elimina un movimiento.
     *
     * <p>Operacion administrativa: <strong>no recalcula saldos</strong>.</p>
     *
     * @param id identificador del movimiento
     * @return completable que termina cuando el movimiento fue eliminado
     */
    Completable delete(String id);
}
