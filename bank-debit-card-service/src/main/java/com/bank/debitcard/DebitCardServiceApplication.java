package com.bank.debitcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del microservicio de tarjetas de debito (Parte III).
 *
 * <p>No llama a otros microservicios por REST: mantiene copias locales de
 * clientes, cuentas y deudas vencidas alimentadas por Kafka, y cobra los pagos
 * pidiendo operaciones de cuenta por Kafka.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DebitCardServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(DebitCardServiceApplication.class, args);
    }
}
