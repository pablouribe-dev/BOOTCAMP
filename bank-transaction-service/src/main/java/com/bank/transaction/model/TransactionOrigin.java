package com.bank.transaction.model;

/**
 * Parte III: origen de un movimiento.
 */
public enum TransactionOrigin {

    /** Registrado por la API REST de este servicio (valor por defecto). */
    API,

    /** Pago con tarjeta de debito, recibido por Kafka. */
    DEBIT_CARD,

    /** Pago del monedero Yanki, recibido por Kafka. */
    YANKI
}
