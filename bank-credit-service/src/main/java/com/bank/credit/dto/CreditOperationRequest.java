package com.bank.credit.dto;

import com.bank.credit.model.CreditOperationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Peticion del endpoint interno que mueve el saldo pendiente de un credito.
 *
 * <p>La envia {@code bank-transaction-service}; no esta pensada para consumo
 * directo desde el exterior.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditOperationRequest {

    /** Operacion a aplicar. Obligatoria. */
    @NotNull(message = "operation is required")
    private CreditOperationType operation;

    /** Importe del pago. Debe ser mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;
}
