package com.bank.account.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /credit-cards?customerId=}: solo
 * interesa saber que la tarjeta existe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardSummaryDto {

    /** Identificador de la tarjeta. */
    private String id;

    /** Cliente titular. */
    private String customerId;
}
