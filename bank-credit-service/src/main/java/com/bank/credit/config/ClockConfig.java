package com.bank.credit.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema como bean, para que la logica que depende de la fecha
 * actual (vencimiento de deudas) se pueda probar con una fecha fija.
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
