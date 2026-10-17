package com.bank.debitcard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code POST /debit-cards/{id}/payments}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitPaymentRequest {

    /** Importe. Obligatorio y mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion (opcional). */
    private String description;
}
