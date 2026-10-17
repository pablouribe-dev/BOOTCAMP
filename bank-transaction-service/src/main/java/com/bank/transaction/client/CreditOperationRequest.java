package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo que se envia a {@code POST /credits/{id}/balance-operations}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditOperationRequest {

    /** Operacion a aplicar. */
    private CreditOperationType operation;

    /** Importe del pago. */
    private BigDecimal amount;
}
