package com.bank.transaction.model;

/**
 * Tipo de producto sobre el que se registra un movimiento.
 */
public enum ProductType {

    /** Cuenta bancaria, administrada por {@code bank-account-service}. */
    ACCOUNT,

    /** Credito, administrado por {@code bank-credit-service}. */
    CREDIT,

    /** Tarjeta de credito, administrada por {@code bank-credit-card-service}. */
    CREDIT_CARD,

    /** Parte III: tarjeta de debito (pagos recibidos por Kafka). */
    DEBIT_CARD
}
