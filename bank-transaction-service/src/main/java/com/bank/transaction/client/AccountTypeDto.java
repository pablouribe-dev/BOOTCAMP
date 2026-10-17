package com.bank.transaction.client;

/**
 * Tipo de cuenta tal como lo devuelve {@code bank-account-service}.
 *
 * <p>Copia local: no se comparten clases entre repositorios.</p>
 */
public enum AccountTypeDto {

    /** Ahorro: limite maximo de movimientos al mes. */
    SAVINGS,

    /** Corriente: sin restriccion de movimientos. */
    CURRENT,

    /** Plazo fijo: un unico movimiento al mes, en un dia fijo. */
    FIXED_TERM
}
