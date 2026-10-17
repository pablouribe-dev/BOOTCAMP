package com.bank.yanki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio del monedero Yanki (Parte III).
 *
 * <p>No llama a otros microservicios por REST: mantiene una copia local de las
 * tarjetas de debito y mueve saldos de cuentas pidiendo operaciones por
 * Kafka.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class YankiServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(YankiServiceApplication.class, args);
    }
}
