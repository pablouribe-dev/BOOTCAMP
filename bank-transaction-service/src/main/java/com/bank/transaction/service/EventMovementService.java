package com.bank.transaction.service;

import com.bank.transaction.event.AccountOperationResultPayload;
import io.reactivex.rxjava3.core.Completable;

/**
 * Parte III: movimientos originados por eventos de Kafka (tarjeta de debito y
 * Yanki).
 */
public interface EventMovementService {

    /**
     * Registra el movimiento de una operacion de cuenta completada.
     *
     * <ul>
     *   <li>{@code DEBIT_CARD}: {@code productType = DEBIT_CARD},
     *       {@code productId} = id de la tarjeta, tipo {@code DEBIT_PAYMENT}.</li>
     *   <li>{@code YANKI}: {@code DEPOSIT} o {@code WITHDRAWAL} sobre la cuenta.</li>
     * </ul>
     *
     * <p>Idempotente por {@code operationId}: si la cuenta republica el mismo
     * resultado (con otro {@code eventId}), no se duplica el movimiento.</p>
     *
     * @param eventId evento que trajo el resultado
     * @param result  operacion completada
     * @return completable que termina cuando el movimiento quedo registrado
     */
    Completable recordCompletedOperation(String eventId, AccountOperationResultPayload result);
}
