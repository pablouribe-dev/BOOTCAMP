package com.bank.creditcard.exception;

/**
 * Se lanza cuando el recurso solicitado no existe. Se traduce a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Crea la excepcion con un mensaje descriptivo.
     *
     * @param message detalle de que recurso no se encontro
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
