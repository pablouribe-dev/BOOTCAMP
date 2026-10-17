package com.bank.account.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio: segunda cuenta de
 * ahorro de un cliente personal, cuenta de ahorro para una empresa, fondos
 * insuficientes, etc. Se traduce a HTTP 409.
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
