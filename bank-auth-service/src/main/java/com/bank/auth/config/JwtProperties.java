package com.bank.auth.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del JWT, servidos por el Config Server bajo
 * {@code bank.security.jwt}. El secreto llega por la variable de entorno
 * {@code JWT_SECRET} y nunca vive en el repositorio.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.security.jwt")
public class JwtProperties {

    /** Secreto HS256 compartido con el gateway; minimo 32 bytes. */
    private String secret;

    /** Duracion del token (60 minutos por defecto). */
    private Duration expiration = Duration.ofMinutes(60);
}
