package com.bank.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans de seguridad del servicio: codificador BCrypt y reloj (para poder
 * probar la expiracion de los tokens).
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Codificador de contrasenas.
     *
     * @return BCrypt con la fuerza por defecto (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Reloj del sistema.
     *
     * @return reloj UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
