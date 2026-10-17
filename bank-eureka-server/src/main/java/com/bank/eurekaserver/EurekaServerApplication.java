package com.bank.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Punto de entrada del registro de servicios.
 *
 * <p>Todos los microservicios (negocio y gateway) se registran aqui con su
 * {@code spring.application.name}, y se encuentran entre si por ese nombre.
 * El panel de control queda en {@code http://localhost:8761}.</p>
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    /**
     * Arranca el servidor Eureka.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
