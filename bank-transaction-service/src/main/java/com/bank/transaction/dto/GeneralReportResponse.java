package com.bank.transaction.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reporte general de movimientos en un intervalo, agrupado por tipo de
 * producto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralReportResponse {

    /** Inicio del intervalo, inclusive. */
    private LocalDate from;

    /** Fin del intervalo, inclusive. */
    private LocalDate to;

    /** Cantidad total de movimientos del intervalo. */
    private long totalMovements;

    /** Totales por tipo de producto. */
    private List<ProductTypeSummary> byProductType;
}
