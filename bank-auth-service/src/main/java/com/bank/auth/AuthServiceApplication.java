package com.bank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del microservicio de autenticacion (Parte III).
 *
 * <p>Administra usuarios y emite los JWT que valida el API Gateway. No llama a
 * otros microservicios: solo se registra en Eureka.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
