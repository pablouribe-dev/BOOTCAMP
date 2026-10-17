package com.bank.debitcard.model;

/**
 * Estado de un pago con tarjeta de debito.
 */
public enum PaymentStatus {

    /** Esperando el resultado de la operacion de cuenta. */
    PENDING,

    /** Cobrado en alguna de las cuentas asociadas. */
    COMPLETED,

    /** Ninguna cuenta asociada tenia fondos (o fallo la publicacion). */
    FAILED
}
