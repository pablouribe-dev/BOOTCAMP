package com.bank.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Punto de entrada del Spring Cloud Config Server.
 *
 * <p>Centraliza la configuracion de todos los microservicios del banco: puertos,
 * URIs de MongoDB, URLs de servicios remotos y limites de negocio. Los archivos
 * servidos viven en el repositorio {@code bank-config-repo}.</p>
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    /**
     * Arranca el Config Server.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
