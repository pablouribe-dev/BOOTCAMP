package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo que se envia a
 * {@code POST /accounts/{id}/balance-operations}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationRequest {

    /** Operacion a aplicar. */
    private AccountOperationType operation;

    /** Importe de la operacion. */
    private BigDecimal amount;

    /** Comision por exceso de transacciones; cero si no corresponde. */
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
}
