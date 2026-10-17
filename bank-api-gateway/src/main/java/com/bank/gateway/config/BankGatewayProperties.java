package com.bank.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas propias del gateway, servidas por el Config Server bajo el prefijo
 * {@code bank.gateway}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.gateway")
public class BankGatewayProperties {

    /**
     * Patrones de ruta (estilo Ant) que nunca se exponen hacia afuera, por
     * ejemplo los endpoints internos {@code /**}{@code /balance-operations}.
     */
    private List<String> blockedPaths = new ArrayList<>();
}
