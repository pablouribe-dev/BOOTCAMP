package com.bank.transaction.client;

/**
 * Operacion de saldo que acepta {@code bank-credit-card-service}.
 */
public enum CardOperationType {

    /** Consumo con la tarjeta. */
    CHARGE,

    /** Pago que reduce el importe consumido. */
    PAYMENT
}
