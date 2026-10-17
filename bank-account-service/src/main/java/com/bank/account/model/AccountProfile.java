package com.bank.account.model;

/**
 * Perfil comercial de la cuenta, copiado del perfil del cliente al abrirla.
 */
public enum AccountProfile {

    /** Sin beneficios ni requisitos adicionales. */
    STANDARD,

    /** Cliente personal preferente: solo ahorro, requiere tarjeta de credito y promedio diario minimo. */
    VIP,

    /** Pequena o mediana empresa: solo corriente sin comision de mantenimiento, requiere tarjeta. */
    PYME
}
