package com.bank.yanki.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema como bean, para probar el vencimiento de pagos colgados.
 */
@Configuration
public class ClockConfig {

    /**
     * Reloj de la zona horaria del sistema.
     *
     * @return reloj del sistema
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
