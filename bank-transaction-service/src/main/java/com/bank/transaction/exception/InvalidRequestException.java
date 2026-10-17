package com.bank.transaction.exception;

/**
 * Se lanza cuando los parametros de una consulta no son validos (por ejemplo,
 * un rango de fechas invertido o un tipo de producto no soportado por un
 * reporte). Se traduce a HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * Crea la excepcion con un mensaje descriptivo.
     *
     * @param message detalle del parametro invalido
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
