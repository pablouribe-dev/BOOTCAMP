package com.bank.account.model;

/**
 * Tipo de cuenta bancaria.
 */
public enum AccountType {

    /** Ahorro: sin comision de mantenimiento, con limite de movimientos mensuales. */
    SAVINGS,

    /** Corriente: con comision de mantenimiento, sin limite de movimientos. */
    CURRENT,

    /** Plazo fijo: sin comision, un unico movimiento al mes en un dia fijo. */
    FIXED_TERM
}
