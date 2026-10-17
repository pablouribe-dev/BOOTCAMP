package com.bank.debitcard.service;

import com.bank.debitcard.dto.DebitPaymentRequest;
import com.bank.debitcard.dto.DebitPaymentResponse;
import com.bank.debitcard.event.AccountOperationResultPayload;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Pagos con tarjeta de debito (coreografia por Kafka con fallback de cuentas).
 */
public interface DebitPaymentService {

    /**
     * Crea el pago en {@code PENDING} y pide el retiro a la cuenta principal.
     *
     * @param cardId  tarjeta
     * @param request importe y descripcion
     * @return el pago aceptado (estado {@code PENDING})
     */
    Single<DebitPaymentResponse> pay(String cardId, DebitPaymentRequest request);

    /**
     * Lista los pagos de una tarjeta.
     *
     * @param cardId tarjeta
     * @return pagos, del mas reciente al mas antiguo
     */
    Flowable<DebitPaymentResponse> findByCard(String cardId);

    /**
     * Consulta un pago.
     *
     * @param cardId    tarjeta
     * @param paymentId pago
     * @return el pago
     */
    Single<DebitPaymentResponse> findById(String cardId, String paymentId);

    /**
     * Avanza el pago con el resultado de una operacion de cuenta: completado,
     * siguiente cuenta asociada o fallido. Los resultados que no son de un pago
     * de este servicio, o de un pago ya terminado, se ignoran.
     *
     * @param eventType {@code ACCOUNT_OPERATION_COMPLETED} o {@code ACCOUNT_OPERATION_REJECTED}
     * @param result    resultado de la operacion
     * @return completable que termina cuando el pago quedo actualizado
     */
    Completable handleOperationResult(String eventType, AccountOperationResultPayload result);
}
