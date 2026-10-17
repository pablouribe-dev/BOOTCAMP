package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /credit-cards/{id}} que necesita
 * este servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardDto {

    /** Identificador de la tarjeta. */
    private String id;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Identificador del cliente titular. */
    private String customerId;

    /** Linea de credito otorgada. */
    private BigDecimal creditLimit;

    /** Importe consumido. */
    private BigDecimal usedAmount;

    /** Saldo disponible calculado por el servicio de tarjetas. */
    private BigDecimal availableBalance;
}
