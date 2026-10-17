package com.bank.creditcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de una busqueda de deudas vencidas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverdueScanResponse {

    /** Tipo de producto revisado. */
    private String productType;

    /** Deudas vencidas nuevas notificadas en esta corrida. */
    private long detected;
}
