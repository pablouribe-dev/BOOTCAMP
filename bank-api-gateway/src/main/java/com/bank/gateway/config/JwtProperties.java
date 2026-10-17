package com.bank.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secreto con el que el gateway valida los JWT, servido por el Config Server
 * bajo {@code bank.security.jwt}. Llega por la variable de entorno
 * {@code JWT_SECRET} y debe ser el mismo que usa {@code bank-auth-service}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.security.jwt")
public class JwtProperties {

    /** Secreto HS256 compartido; minimo 32 bytes. */
    private String secret;
}
