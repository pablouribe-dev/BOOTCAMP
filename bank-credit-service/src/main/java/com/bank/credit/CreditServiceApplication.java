package com.bank.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio de creditos.
 *
 * <p>Administra los creditos personales y empresariales (productos activos) y
 * aplica las reglas de otorgamiento segun el tipo de cliente, que consulta por
 * REST a {@code bank-customer-service}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CreditServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(CreditServiceApplication.class, args);
    }
}
