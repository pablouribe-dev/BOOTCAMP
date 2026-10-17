package com.bank.transaction.dto;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada de la correccion administrativa de un movimiento.
 *
 * <p>Esta operacion <strong>no recalcula saldos</strong>: solo corrige los
 * datos del registro del movimiento.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionUpdateRequest {

    /** Importe corregido. Si no se envia, se mantiene el actual. */
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion corregida. Si no se envia, se mantiene la actual. */
    private String description;
}
