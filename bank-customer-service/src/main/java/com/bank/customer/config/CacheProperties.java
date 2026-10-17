package com.bank.customer.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de la cache de clientes en Redis, servidos por el Config Server
 * bajo el prefijo {@code bank.cache}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.cache")
public class CacheProperties {

    /** Tiempo de vida de cada entrada. */
    private Duration ttl = Duration.ofMinutes(10);

    /** Espera maxima por una respuesta de Redis antes de seguir contra MongoDB. */
    private Duration timeout = Duration.ofMillis(500);
}
