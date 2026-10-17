package com.bank.creditcard.model;

/**
 * Operacion que puede afectar el importe consumido de una tarjeta.
 *
 * <p>Las solicita {@code bank-transaction-service} a traves del endpoint
 * interno {@code POST /credit-cards/{id}/balance-operations}.</p>
 */
public enum CardOperationType {

    /** Consumo: incrementa el importe usado y exige linea disponible. */
    CHARGE,

    /** Pago: reduce el importe usado y no puede dejarlo negativo. */
    PAYMENT
}
