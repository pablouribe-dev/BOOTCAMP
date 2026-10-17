package com.bank.credit.service;

import com.bank.credit.event.DebtEventPayload;
import io.reactivex.rxjava3.core.Completable;

/**
 * Copia local de deudas vencidas y regla de adquisicion de productos.
 */
public interface OverdueDebtService {

    /**
     * Registra o actualiza la deuda vencida de un producto
     * ({@code DEBT_OVERDUE_DETECTED}).
     *
     * @param payload datos de la deuda
     * @return completable que termina cuando la copia quedo actualizada
     */
    Completable registerOverdue(DebtEventPayload payload);

    /**
     * Elimina la deuda vencida de un producto ({@code DEBT_OVERDUE_CLEARED}).
     *
     * @param payload producto saldado
     * @return completable que termina cuando la copia quedo actualizada
     */
    Completable clearOverdue(DebtEventPayload payload);

    /**
     * Regla de adquisicion: un cliente con deuda vencida no puede adquirir
     * productos nuevos. La consulta es local, sin REST.
     *
     * @param customerId identificador del cliente
     * @return completable que falla con 409 si el cliente tiene deuda vencida
     */
    Completable ensureNoOverdueDebt(String customerId);
}
