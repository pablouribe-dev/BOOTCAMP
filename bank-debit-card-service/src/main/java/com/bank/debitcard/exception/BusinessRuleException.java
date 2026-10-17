package com.bank.debitcard.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio, por ejemplo un
 * numero de documento duplicado. Se traduce a HTTP 409.
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
