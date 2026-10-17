package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del API Gateway.
 *
 * <p>Recibe todo el trafico externo en el puerto 8080 y lo enruta, por nombre
 * de Eureka, al microservicio correspondiente. Las rutas viven en
 * {@code bank-config-repo/bank-api-gateway.yml}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    /**
     * Arranca el gateway.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
