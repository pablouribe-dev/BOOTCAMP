package com.bank.creditcard.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa una tarjeta de credito.
 *
 * <p>Se almacena en la coleccion {@code credit_cards} de la base
 * {@code bank_credit_cards}. El saldo disponible no se guarda: se calcula como
 * {@code creditLimit - usedAmount} al construir la respuesta.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "credit_cards")
public class CreditCard {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Numero de tarjeta generado por el servicio. Es unico. */
    @Indexed(unique = true)
    private String cardNumber;

    /** Tipo de tarjeta, igual al tipo del cliente. */
    private CardType cardType;

    /** Identificador del cliente titular. */
    private String customerId;

    /** Linea de credito otorgada. */
    private BigDecimal creditLimit;

    /** Importe consumido y aun no pagado. */
    private BigDecimal usedAmount;

    /** Parte III: fecha limite de pago del periodo. */
    private LocalDate paymentDueDate;

    /** Parte III: importe exigible a la fecha limite de pago. */
    private BigDecimal dueAmount;

    /** Parte III: si ya se publico {@code DEBT_OVERDUE_DETECTED} para esta deuda. */
    private Boolean overdueNotified;

    /** Fecha y hora de emision. */
    private LocalDateTime createdAt;
}
