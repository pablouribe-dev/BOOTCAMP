package com.bank.transaction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URLs base de los servicios remotos, servidas por el Config Server bajo el
 * prefijo {@code bank.services}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.services")
public class ServiceUrlProperties {

    /** URL base de {@code bank-account-service}. */
    private String accountServiceUrl;

    /** URL base de {@code bank-credit-service}. */
    private String creditServiceUrl;

    /** URL base de {@code bank-credit-card-service}. */
    private String creditCardServiceUrl;

    /** Parte III: URL base de {@code bank-customer-service}. */
    private String customerServiceUrl;
}
