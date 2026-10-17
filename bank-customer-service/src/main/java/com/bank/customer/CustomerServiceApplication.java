package com.bank.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del microservicio de clientes.
 *
 * <p>Expone el CRUD de clientes personales y empresariales. Es la fuente de
 * verdad del tipo de cliente, dato que consultan los servicios de cuentas,
 * creditos y tarjetas de credito.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
