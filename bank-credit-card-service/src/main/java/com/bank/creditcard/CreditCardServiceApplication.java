package com.bank.creditcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio de tarjetas de credito.
 *
 * <p>Administra las tarjetas de credito personales y empresariales, su linea de
 * credito y el importe consumido. El tipo de tarjeta debe coincidir con el tipo
 * de cliente, que consulta por REST a {@code bank-customer-service}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CreditCardServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(CreditCardServiceApplication.class, args);
    }
}
