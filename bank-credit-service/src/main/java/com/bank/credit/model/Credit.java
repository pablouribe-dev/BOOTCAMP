package com.bank.credit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa un credito.
 *
 * <p>Se almacena en la coleccion {@code credits} de la base
 * {@code bank_credits}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credits")
public class Credit {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Tipo de credito, igual al tipo del cliente. */
    private CreditType creditType;

    /** Identificador del cliente al que se otorgo el credito. */
    private String customerId;

    /** Importe otorgado. */
    private BigDecimal grantedAmount;

    /** Saldo pendiente de pago. */
    private BigDecimal outstandingBalance;

    /** Tasa de interes aplicada, en porcentaje. */
    private BigDecimal interestRate;

    /** Parte III: fecha de vencimiento de la cuota pendiente. */
    private LocalDate dueDate;

    /** Parte III: importe exigible a la fecha de vencimiento. */
    private BigDecimal dueAmount;

    /** Parte III: si ya se publico {@code DEBT_OVERDUE_DETECTED} para esta deuda. */
    private Boolean overdueNotified;

    /** Fecha y hora de otorgamiento. */
    private LocalDateTime createdAt;
}
