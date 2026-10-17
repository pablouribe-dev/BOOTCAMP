package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reporte completo de un producto en un intervalo de fechas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReportResponse {

    /** Tipo de producto. */
    private ProductType productType;

    /** Identificador del producto. */
    private String productId;

    /** Cliente dueno del producto. */
    private String customerId;

    /** Datos del producto tal como los devuelve su servicio dueno. */
    private Object product;

    /**
     * Saldo actual: disponible de la cuenta, pendiente del credito o
     * disponible de la tarjeta.
     */
    private BigDecimal currentBalance;

    /** Inicio del intervalo, inclusive. */
    private LocalDate from;

    /** Fin del intervalo, inclusive. */
    private LocalDate to;

    /** Cantidad de movimientos del intervalo. */
    private long movementCount;

    /** Importe total por tipo de movimiento. */
    private Map<TransactionType, BigDecimal> totalsByType;

    /** Total de comisiones cobradas en el intervalo. */
    private BigDecimal totalFees;

    /** Movimientos del intervalo, del mas antiguo al mas reciente. */
    private List<TransactionResponse> movements;
}
