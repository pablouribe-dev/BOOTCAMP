package com.bank.creditcard.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saldo disponible de una tarjeta de credito.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardBalanceResponse {

    /** Identificador de la tarjeta. */
    private String cardId;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Linea de credito otorgada. */
    private BigDecimal creditLimit;

    /** Importe consumido. */
    private BigDecimal usedAmount;

    /** Saldo disponible, calculado como {@code creditLimit - usedAmount}. */
    private BigDecimal availableBalance;
}
