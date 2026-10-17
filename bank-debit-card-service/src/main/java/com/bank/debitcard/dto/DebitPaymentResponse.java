package com.bank.debitcard.dto;

import com.bank.debitcard.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pago con tarjeta de debito. El {@code POST} responde 202 con
 * {@code paymentId} y {@code status = PENDING}; el estado final se consulta
 * con el {@code GET}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitPaymentResponse {

    /** Identificador del pago. */
    private String paymentId;

    /** Tarjeta. */
    private String cardId;

    /** Importe. */
    private BigDecimal amount;

    /** Descripcion. */
    private String description;

    /** Estado. */
    private PaymentStatus status;

    /** Cuentas intentadas, en orden. */
    private List<String> attemptedAccountIds;

    /** Cuenta cobrada (si se completo). */
    private String chargedAccountId;

    /** Motivo del fallo (si fallo). */
    private String failureReason;

    /** Fecha y hora de creacion. */
    private LocalDateTime createdAt;
}
