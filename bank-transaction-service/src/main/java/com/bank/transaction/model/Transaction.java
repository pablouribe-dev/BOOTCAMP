package com.bank.transaction.model;

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
 * Documento MongoDB que representa un movimiento.
 *
 * <p>Se almacena en la coleccion {@code transactions} de la base
 * {@code bank_transactions}. Un movimiento solo se guarda si el cambio de saldo
 * en el servicio dueno del producto fue exitoso.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Tipo de producto afectado. */
    private ProductType productType;

    /** Identificador del producto afectado. */
    private String productId;

    /** Identificador del cliente dueno del producto. */
    private String customerId;

    /** Tipo de movimiento. */
    private TransactionType transactionType;

    /** Importe del movimiento. */
    private BigDecimal amount;

    /** Descripcion libre del movimiento. */
    private String description;

    /** Comision por exceso de transacciones cobrada en este movimiento; cero si no hubo. */
    @Builder.Default
    private BigDecimal feeCharged = BigDecimal.ZERO;

    /** Identificador comun a los dos movimientos de una transferencia. */
    private String transferId;

    /** Tipo de transferencia (propia o a terceros); solo en movimientos de transferencia. */
    private TransferType transferType;

    /** Fecha y hora del movimiento. */
    /** Parte III: cliente que pago (si no viene, el titular). */
    private String payerCustomerId;

    /** Parte III: {@code true} si el pagador no es el titular del producto. */
    private Boolean thirdParty;

    /** Parte III: origen del movimiento ({@code API} por defecto). */
    @Builder.Default
    private TransactionOrigin origin = TransactionOrigin.API;

    /** Parte III: cuenta afectada (pagos con tarjeta de debito y Yanki). */
    private String accountId;

    /** Parte III: referencia del origen (id de la tarjeta o del pago Yanki). */
    private String reference;

    /** Parte III: evento que origino el movimiento (idempotencia). */
    private String eventId;

    /** Parte III: operacion de cuenta que origino el movimiento (idempotencia). */
    @Indexed(unique = true, sparse = true)
    private String operationId;

    private LocalDateTime createdAt;
}
