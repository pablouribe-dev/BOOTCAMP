package com.bank.credit.service;

import com.bank.credit.dto.OverdueScanResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Deteccion de deudas vencidas de creditos.
 */
public interface OverdueScanService {

    /**
     * Busca los creditos con deuda vencida ({@code dueAmount > 0} y
     * vencimiento anterior a hoy) aun no notificados, publica
     * {@code DEBT_OVERDUE_DETECTED} por cada uno y los marca como notificados.
     *
     * @return cantidad de deudas notificadas
     */
    Single<OverdueScanResponse> scan();
}
