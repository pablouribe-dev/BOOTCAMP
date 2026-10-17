package com.bank.creditcard.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio: tipo de tarjeta que
 * no coincide con el tipo de cliente, consumo que excede la linea disponible,
 * pago mayor al importe consumido, etc. Se traduce a HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Crea la excepcion con un mensaje descriptivo.
     *
     * @param message detalle de la regla incumplida
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
