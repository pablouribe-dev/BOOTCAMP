package com.bank.transaction.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio: limite mensual de
 * movimientos superado, movimiento fuera del dia de una cuenta a plazo fijo,
 * fondos insuficientes o limite de tarjeta excedido. Se traduce a HTTP 409.
 *
 * <p>Tambien recoge los conflictos que reportan los servicios remotos, para que
 * el codigo de estado se conserve de punta a punta.</p>
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
