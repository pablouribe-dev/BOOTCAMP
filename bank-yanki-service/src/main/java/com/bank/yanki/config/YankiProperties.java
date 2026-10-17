package com.bank.yanki.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de los pagos Yanki, servidos por el Config Server bajo
 * {@code bank.yanki}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.yanki")
public class YankiProperties {

    /** Tiempo maximo que un pago puede quedar en {@code PENDING}. */
    private Duration paymentTimeout = Duration.ofMinutes(2);
}
