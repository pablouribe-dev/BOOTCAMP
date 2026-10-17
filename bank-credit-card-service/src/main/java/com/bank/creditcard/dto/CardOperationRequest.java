package com.bank.creditcard.dto;

import com.bank.creditcard.model.CardOperationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Peticion del endpoint interno que mueve el importe consumido de una tarjeta.
 *
 * <p>La envia {@code bank-transaction-service}; no esta pensada para consumo
 * directo desde el exterior.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardOperationRequest {

    /** Operacion a aplicar: consumo o pago. Obligatoria. */
    @NotNull(message = "operation is required")
    private CardOperationType operation;

    /** Importe de la operacion. Debe ser mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;
}
