package com.bank.yanki.dto;

import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.PaymentStep;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pago Yanki. El {@code POST} responde 202 con {@code paymentId} y el estado
 * actual ({@code PENDING} si espera a las cuentas).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YankiPaymentResponse {

    /** Identificador del pago. */
    private String paymentId;

    /** Celular del emisor. */
    private String fromPhoneNumber;

    /** Celular del receptor. */
    private String toPhoneNumber;

    /** Importe. */
    private BigDecimal amount;

    /** Descripcion. */
    private String description;

    /** Estado. */
    private PaymentStatus status;

    /** Paso en curso. */
    private PaymentStep step;

    /** Motivo del fallo. */
    private String failureReason;

    /** Fecha y hora de creacion. */
    private LocalDateTime createdAt;
}
