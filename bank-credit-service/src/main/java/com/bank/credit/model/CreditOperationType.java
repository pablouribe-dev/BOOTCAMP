package com.bank.credit.model;

/**
 * Operacion que puede afectar el saldo pendiente de un credito.
 *
 * <p>La solicita {@code bank-transaction-service} a traves del endpoint interno
 * {@code POST /credits/{id}/balance-operations}.</p>
 */
public enum CreditOperationType {

    /** Pago: reduce el saldo pendiente. */
    PAYMENT
}
