package com.bank.transaction.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema como bean, para que las reglas que dependen de la fecha
 * (mes en curso, dia del plazo fijo) se puedan probar con una fecha fija.
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
