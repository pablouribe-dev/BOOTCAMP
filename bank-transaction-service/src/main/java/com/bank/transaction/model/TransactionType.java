package com.bank.transaction.model;

/**
 * Tipo de movimiento registrado.
 */
public enum TransactionType {

    /** Deposito en una cuenta. */
    DEPOSIT,

    /** Retiro de una cuenta. */
    WITHDRAWAL,

    /** Pago de un credito o de una tarjeta de credito. */
    PAYMENT,

    /** Consumo con tarjeta de credito. */
    CHARGE,

    /** Salida de dinero de la cuenta origen de una transferencia. */
    TRANSFER_OUT,

    /** Entrada de dinero en la cuenta destino de una transferencia. */
    TRANSFER_IN,

    /** Parte III: pago con tarjeta de debito (cargo en una cuenta asociada). */
    DEBIT_PAYMENT
}
