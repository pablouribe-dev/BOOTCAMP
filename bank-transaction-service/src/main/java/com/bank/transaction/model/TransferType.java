package com.bank.transaction.model;

/**
 * Tipo de transferencia entre cuentas del banco.
 */
public enum TransferType {

    /** Entre cuentas del mismo cliente. */
    OWN,

    /** Hacia la cuenta de otro cliente del mismo banco. */
    THIRD_PARTY
}
