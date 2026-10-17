package com.bank.credit.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio: segundo credito de
 * un cliente personal, tipo de credito que no coincide con el tipo de cliente,
 * pago mayor al saldo pendiente, etc. Se traduce a HTTP 409.
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
