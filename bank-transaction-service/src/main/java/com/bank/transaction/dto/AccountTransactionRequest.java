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
 * Datos de entrada de un deposito o de un retiro en cuenta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTransactionRequest {

    /** Identificador de la cuenta afectada. Obligatorio. */
    @NotBlank(message = "accountId is required")
    private String accountId;

    /** Importe del movimiento. Obligatorio y mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion libre del movimiento. */
    private String description;
}
