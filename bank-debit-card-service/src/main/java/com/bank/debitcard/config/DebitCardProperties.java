package com.bank.debitcard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de las tarjetas de debito, servidos por el Config Server bajo
 * {@code bank.debit-cards}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.debit-cards")
public class DebitCardProperties {

    /** Prefijo del numero de tarjeta generado (16 digitos en total). */
    private String cardNumberPrefix = "5555";
}
