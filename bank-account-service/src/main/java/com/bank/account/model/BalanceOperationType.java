package com.bank.account.model;

/**
 * Operacion que puede afectar el saldo de una cuenta.
 *
 * <p>Las solicita {@code bank-transaction-service} a traves del endpoint
 * interno {@code POST /accounts/{id}/balance-operations}.</p>
 */
public enum BalanceOperationType {

    /** Abono: incrementa el saldo. */
    DEPOSIT,

    /** Cargo: reduce el saldo y exige fondos suficientes. */
    WITHDRAWAL
}
