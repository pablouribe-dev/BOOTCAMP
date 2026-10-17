package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /credits/{id}} que necesita este
 * servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditDto {

    /** Identificador del credito. */
    private String id;

    /** Identificador del cliente dueno del credito. */
    private String customerId;

    /** Importe otorgado. */
    private BigDecimal grantedAmount;

    /** Saldo pendiente de pago. */
    private BigDecimal outstandingBalance;
}
