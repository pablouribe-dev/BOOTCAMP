package com.bank.debitcard.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pago con tarjeta de debito (coleccion {@code debit_payments}).
 *
 * <p>Coreografia: se pide un retiro a la cuenta principal; si se rechaza por
 * fondos, se intenta con la siguiente cuenta asociada, en orden.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "debit_payments")
public class DebitPayment {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Tarjeta con la que se paga. */
    @Indexed
    private String cardId;

    /** Importe. */
    private BigDecimal amount;

    /** Descripcion del pago. */
    private String description;

    /** Estado. */
    private PaymentStatus status;

    /** Operacion de cuenta en curso; enlaza los resultados con el pago. */
    @Indexed
    private String currentOperationId;

    /** Cuentas ya intentadas, en orden. */
    @Builder.Default
    private List<String> attemptedAccountIds = new ArrayList<>();

    /** Cuenta en la que finalmente se cobro. */
    private String chargedAccountId;

    /** Motivo del ultimo rechazo (si el pago fallo). */
    private String failureReason;

    /** Fecha y hora de creacion. */
    private LocalDateTime createdAt;

    /** Fecha y hora del ultimo cambio de estado. */
    private LocalDateTime updatedAt;
}
