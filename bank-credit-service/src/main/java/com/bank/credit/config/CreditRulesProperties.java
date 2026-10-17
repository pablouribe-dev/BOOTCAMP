package com.bank.credit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de negocio parametrizables, servidos por el Config Server bajo el
 * prefijo {@code bank.rules}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.rules")
public class CreditRulesProperties {

    /** Cantidad maxima de creditos que puede tener un cliente personal. */
    private Integer maxPersonalCredits = 1;
}
