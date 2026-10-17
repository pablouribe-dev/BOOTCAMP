package com.bank.yanki.model;

/**
 * Paso en curso de un pago Yanki.
 */
public enum PaymentStep {

    /** Esperando el retiro de la cuenta principal del emisor. */
    DEBIT_REQUESTED,

    /** Esperando el deposito en la cuenta principal del receptor. */
    CREDIT_REQUESTED,

    /** Esperando la devolucion del debito al emisor. */
    COMPENSATING,

    /** Terminado. */
    DONE
}
