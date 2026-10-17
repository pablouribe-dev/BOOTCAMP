package com.bank.creditcard.dto;

import com.bank.creditcard.model.CardType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacion de una tarjeta de credito devuelta por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardResponse {

    /** Identificador de la tarjeta. */
    private String id;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Tipo de tarjeta. */
    private CardType cardType;

    /** Identificador del cliente titular. */
    private String customerId;

    /** Linea de credito otorgada. */
    private BigDecimal creditLimit;

    /** Importe consumido. */
    private BigDecimal usedAmount;

    /** Saldo disponible, calculado como {@code creditLimit - usedAmount}. */
    private BigDecimal availableBalance;

    /** Fecha limite de pago del periodo. */
    private LocalDate paymentDueDate;

    /** Importe exigible a la fecha limite de pago. */
    private BigDecimal dueAmount;

    /** Si la deuda esta notificada como vencida. */
    private Boolean overdueNotified;

    /** Fecha y hora de emision. */
    private LocalDateTime createdAt;
}
