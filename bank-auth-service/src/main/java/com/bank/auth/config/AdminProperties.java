package com.bank.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales del administrador inicial, desde las variables de entorno
 * {@code ADMIN_USERNAME} y {@code ADMIN_PASSWORD} (nunca en el repositorio).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.security.admin")
public class AdminProperties {

    /** Usuario del administrador inicial. */
    private String username;

    /** Contrasena del administrador inicial. */
    private String password;
}
