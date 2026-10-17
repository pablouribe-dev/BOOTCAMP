package com.bank.yanki.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pago Yanki entre dos celulares (coleccion {@code yanki_payments}).
 *
 * <p>Saga con compensacion: debito del emisor, credito del receptor y, si el
 * credito falla, devolucion del debito.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "yanki_payments")
public class YankiPayment {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Celular del emisor. */
    @Indexed
    private String fromPhoneNumber;

    /** Celular del receptor. */
    @Indexed
    private String toPhoneNumber;

    /** Importe. */
    private BigDecimal amount;

    /** Descripcion. */
    private String description;

    /** Estado. */
    private PaymentStatus status;

    /** Paso en curso. */
    private PaymentStep step;

    /** Cuenta principal del emisor (si paga con tarjeta). */
    private String senderAccountId;

    /** Cuenta principal del receptor (si cobra con tarjeta). */
    private String receiverAccountId;

    /** Operacion de retiro al emisor. */
    @Indexed(sparse = true)
    private String debitOperationId;

    /** Operacion de deposito al receptor. */
    @Indexed(sparse = true)
    private String creditOperationId;

    /** Operacion de devolucion al emisor. */
    @Indexed(sparse = true)
    private String compensationOperationId;

    /** Si el debito al emisor ya se hizo (define si hay que compensar). */
    private boolean debitCompleted;

    /** Motivo del fallo. */
    private String failureReason;

    /** Fecha y hora de creacion. */
    private LocalDateTime createdAt;

    /** Fecha y hora del ultimo cambio. */
    private LocalDateTime updatedAt;
}
