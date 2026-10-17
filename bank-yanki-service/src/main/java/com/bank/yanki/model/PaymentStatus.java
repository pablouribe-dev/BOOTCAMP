package com.bank.yanki.model;

/**
 * Estado de un pago Yanki.
 */
public enum PaymentStatus {

    /** En curso. */
    PENDING,

    /** Debitado al emisor y acreditado al receptor. */
    COMPLETED,

    /** No se pudo completar (con el debito compensado si se habia hecho). */
    FAILED
}
