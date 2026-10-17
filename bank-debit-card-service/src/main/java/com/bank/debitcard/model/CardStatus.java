package com.bank.debitcard.model;

/**
 * Estado de una tarjeta de debito.
 */
public enum CardStatus {

    /** Operativa. */
    ACTIVE,

    /** Sin cuentas asociadas o desactivada: no admite pagos. */
    INACTIVE
}
