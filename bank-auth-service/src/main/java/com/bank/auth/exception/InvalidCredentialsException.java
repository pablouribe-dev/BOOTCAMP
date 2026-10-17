package com.bank.auth.exception;

/**
 * Credenciales invalidas en el login. Se traduce a 401 con un mensaje generico
 * que no revela si fallo el usuario o la contrasena.
 */
public class InvalidCredentialsException extends RuntimeException {

    /** Mensaje unico para cualquier fallo de credenciales. */
    public static final String MESSAGE = "Invalid username or password";

    /**
     * Crea la excepcion con el mensaje generico.
     */
    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
