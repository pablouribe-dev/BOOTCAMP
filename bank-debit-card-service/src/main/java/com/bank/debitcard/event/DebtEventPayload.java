package com.bank.debitcard.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code DEBT_OVERDUE_DETECTED} y {@code DEBT_OVERDUE_CLEARED}
 * (este ultimo sin fecha ni importe).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtEventPayload {

    /** Evento de deuda vencida detectada. */
    public static final String DEBT_OVERDUE_DETECTED = "DEBT_OVERDUE_DETECTED";

    /** Evento de deuda vencida saldada. */
    public static final String DEBT_OVERDUE_CLEARED = "DEBT_OVERDUE_CLEARED";

    /** Cliente deudor. */
    private String customerId;

    /** {@code CREDIT} o {@code CREDIT_CARD}. */
    private String productType;

    /** Identificador del producto. */
    private String productId;

    /** Fecha de vencimiento incumplida. */
    private LocalDate dueDate;

    /** Importe vencido. */
    private BigDecimal dueAmount;
}
