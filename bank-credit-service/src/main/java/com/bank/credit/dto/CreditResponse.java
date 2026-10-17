package com.bank.credit.dto;

import com.bank.credit.model.CreditType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacion de un credito devuelta por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditResponse {

    /** Identificador del credito. */
    private String id;

    /** Tipo de credito. */
    private CreditType creditType;

    /** Identificador del cliente. */
    private String customerId;

    /** Importe otorgado. */
    private BigDecimal grantedAmount;

    /** Saldo pendiente de pago. */
    private BigDecimal outstandingBalance;

    /** Tasa de interes en porcentaje. */
    private BigDecimal interestRate;

    /** Fecha de vencimiento de la cuota pendiente. */
    private LocalDate dueDate;

    /** Importe exigible a la fecha de vencimiento. */
    private BigDecimal dueAmount;

    /** Si la deuda esta notificada como vencida. */
    private Boolean overdueNotified;

    /** Fecha y hora de otorgamiento. */
    private LocalDateTime createdAt;
}
