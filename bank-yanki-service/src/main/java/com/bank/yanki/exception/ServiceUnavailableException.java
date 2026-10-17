package com.bank.yanki.exception;

/**
 * Una dependencia de infraestructura (Kafka) no respondio. Se traduce a 503.
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * Crea la excepcion.
     *
     * @param message descripcion del problema
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
