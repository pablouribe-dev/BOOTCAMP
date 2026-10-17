package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo que se envia a
 * {@code POST /credit-cards/{id}/balance-operations}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardOperationRequest {

    /** Operacion a aplicar: consumo o pago. */
    private CardOperationType operation;

    /** Importe de la operacion. */
    private BigDecimal amount;
}
