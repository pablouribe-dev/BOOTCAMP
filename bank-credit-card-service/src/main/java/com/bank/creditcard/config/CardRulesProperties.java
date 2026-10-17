package com.bank.creditcard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de negocio servidos por el Config Server bajo el prefijo
 * {@code bank.rules}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.rules")
public class CardRulesProperties {

    /** Prefijo con el que arrancan los numeros de tarjeta generados. */
    private String cardNumberPrefix = "4532";
}
