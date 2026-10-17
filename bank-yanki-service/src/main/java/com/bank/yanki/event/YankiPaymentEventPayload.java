package com.bank.yanki.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code YANKI_PAYMENT_COMPLETED} y {@code YANKI_PAYMENT_FAILED}
 * (informativos).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YankiPaymentEventPayload {

    /** Pago completado. */
    public static final String YANKI_PAYMENT_COMPLETED = "YANKI_PAYMENT_COMPLETED";

    /** Pago fallido. */
    public static final String YANKI_PAYMENT_FAILED = "YANKI_PAYMENT_FAILED";

    /** Identificador del pago. */
    private String paymentId;

    /** Celular del emisor. */
    private String fromPhoneNumber;

    /** Celular del receptor. */
    private String toPhoneNumber;

    /** Importe. */
    private BigDecimal amount;

    /** Estado final. */
    private String status;

    /** Motivo del fallo. */
    private String failureReason;
}
