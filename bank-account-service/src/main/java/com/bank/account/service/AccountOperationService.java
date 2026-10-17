package com.bank.account.service;

import com.bank.account.event.AccountOperationRequestedPayload;
import io.reactivex.rxjava3.core.Completable;

/**
 * Operaciones de cuenta pedidas por Kafka ({@code bank.account.operation-requests}).
 */
public interface AccountOperationService {

    /**
     * Aplica un deposito o retiro pedido por la tarjeta de debito o por Yanki
     * y publica el resultado en {@code bank.account.operation-results}.
     *
     * <p>Usa la misma logica que {@code balance-operations}, sin comision y sin
     * contar para los maximos de movimientos. Es idempotente por
     * {@code operationId}: si la operacion ya se proceso, se republica el
     * mismo resultado sin aplicarla otra vez.</p>
     *
     * @param request operacion pedida
     * @return completable que termina cuando el resultado fue publicado
     */
    Completable process(AccountOperationRequestedPayload request);
}
