package com.bank.transaction.client;

/**
 * Operacion de saldo que acepta {@code bank-account-service}.
 */
public enum AccountOperationType {

    /** Abono en la cuenta. */
    DEPOSIT,

    /** Cargo en la cuenta. */
    WITHDRAWAL
}
