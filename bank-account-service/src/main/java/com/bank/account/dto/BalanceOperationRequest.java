package com.bank.account.dto;

import com.bank.account.model.BalanceOperationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Peticion del endpoint interno que mueve el saldo de una cuenta.
 *
 * <p>La envia {@code bank-transaction-service}; no esta pensada para consumo
 * directo desde el exterior.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceOperationRequest {

    /** Operacion a aplicar. Obligatoria. */
    @NotNull(message = "operation is required")
    private BalanceOperationType operation;

    /** Importe de la operacion. Debe ser mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /**
     * Comision por exceso de transacciones, opcional (cero por defecto).
     * Deposito: {@code balance += amount - fee}. Retiro:
     * {@code balance -= amount + fee}.
     */
    @PositiveOrZero(message = "fee must be zero or positive")
    private BigDecimal fee;
}
