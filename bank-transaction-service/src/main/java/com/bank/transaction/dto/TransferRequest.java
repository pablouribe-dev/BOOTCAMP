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
 * Datos de entrada de una transferencia entre cuentas del banco.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    /** Identificador de la cuenta origen. Obligatorio. */
    @NotBlank(message = "sourceAccountId is required")
    private String sourceAccountId;

    /** Numero de la cuenta destino (propia o de un tercero). Obligatorio. */
    @NotBlank(message = "destinationAccountNumber is required")
    private String destinationAccountNumber;

    /** Importe a transferir. Obligatorio y mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion libre. */
    private String description;
}
