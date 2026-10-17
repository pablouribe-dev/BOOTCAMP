package com.bank.transaction.config;

import com.bank.transaction.model.ProductType;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de los reportes, servidos por el Config Server bajo el prefijo
 * {@code bank.reports}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.reports")
public class ReportProperties {

    /**
     * Tipos de producto que admite el reporte de ultimos movimientos. Hoy solo
     * tarjeta de credito; la tarjeta de debito de la Parte III se agrega aqui.
     */
    private Set<ProductType> lastMovementsProductTypes = EnumSet.of(ProductType.CREDIT_CARD);

    /** Cantidad de movimientos del reporte de ultimos movimientos (informativo). */
    private int lastMovementsSize = 10;
}
