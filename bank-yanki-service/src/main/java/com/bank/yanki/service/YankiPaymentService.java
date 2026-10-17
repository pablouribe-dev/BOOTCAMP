package com.bank.yanki.service;

import com.bank.yanki.dto.YankiPaymentRequest;
import com.bank.yanki.dto.YankiPaymentResponse;
import com.bank.yanki.event.AccountOperationResultPayload;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Pagos Yanki por numero de celular (saga con compensacion).
 */
public interface YankiPaymentService {

    /**
     * Inicia un pago: valida, debita al emisor y acredita al receptor. Con
     * tarjeta asociada el saldo vive en la cuenta principal de la tarjeta (se
     * pide por Kafka); sin tarjeta se usa el saldo propio del monedero.
     *
     * @param request celulares, importe y descripcion
     * @return el pago con su estado actual ({@code PENDING} si espera a las cuentas)
     */
    Single<YankiPaymentResponse> send(YankiPaymentRequest request);

    /**
     * Consulta un pago.
     *
     * @param id identificador
     * @return el pago
     */
    Single<YankiPaymentResponse> findById(String id);

    /**
     * Historial de pagos, opcionalmente de un celular (como emisor o receptor).
     *
     * @param phoneNumber filtro opcional
     * @return pagos, del mas reciente al mas antiguo
     */
    Flowable<YankiPaymentResponse> findAll(String phoneNumber);

    /**
     * Avanza el pago con el resultado de una operacion de cuenta. Los
     * resultados que no son de Yanki se ignoran.
     *
     * @param eventType {@code ACCOUNT_OPERATION_COMPLETED} o {@code ACCOUNT_OPERATION_REJECTED}
     * @param result    resultado de la operacion
     * @return completable que termina cuando el pago quedo actualizado
     */
    Completable handleOperationResult(String eventType, AccountOperationResultPayload result);

    /**
     * Marca como {@code FAILED} los pagos que llevan mas del tiempo permitido
     * en {@code PENDING}, compensando si el debito ya se habia hecho.
     *
     * @return cantidad de pagos tratados
     */
    Single<Long> expireStalePayments();
}
