package com.bank.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada de un consumo con tarjeta de credito.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeRequest {

    /** Identificador de la tarjeta de credito. Obligatorio. */
    @NotBlank(message = "creditCardId is required")
    private String creditCardId;

    /** Importe del consumo. Obligatorio y mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion libre del movimiento. */
    private String description;
}
