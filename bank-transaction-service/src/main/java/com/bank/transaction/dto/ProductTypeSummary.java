package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Totales de un tipo de producto dentro del reporte general.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTypeSummary {

    /** Tipo de producto. */
    private ProductType productType;

    /** Cantidad de productos distintos con al menos un movimiento. */
    private long productsWithMovements;

    /** Cantidad de movimientos. */
    private long movementCount;

    /** Importe total de los movimientos. */
    private BigDecimal totalAmount;

    /** Total de comisiones cobradas. */
    private BigDecimal totalFees;

    /** Importe total por tipo de movimiento. */
    private Map<TransactionType, BigDecimal> totalsByType;
}
