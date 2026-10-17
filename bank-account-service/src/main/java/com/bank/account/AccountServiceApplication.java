package com.bank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del microservicio de cuentas bancarias.
 *
 * <p>Administra los productos pasivos (ahorro, corriente y plazo fijo) y
 * aplica las reglas de apertura segun el tipo de cliente, que consulta por
 * REST a {@code bank-customer-service}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AccountServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
