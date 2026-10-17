package com.bank.credit.exception;

/**
 * Se lanza cuando un servicio remoto responde con un error 5xx o no esta
 * disponible. Se traduce a HTTP 503, sin inventar datos.
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * Crea la excepcion con un mensaje descriptivo.
     *
     * @param message detalle del servicio remoto que fallo
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
