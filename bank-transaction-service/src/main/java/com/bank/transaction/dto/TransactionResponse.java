package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.model.TransferType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacion de un movimiento devuelta por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /** Identificador del movimiento. */
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

    /** Comision por exceso de transacciones cobrada; cero si no hubo. */
    private BigDecimal feeCharged;

    /** Identificador comun a los dos movimientos de una transferencia. */
    private String transferId;

    /** Tipo de transferencia; solo en movimientos de transferencia. */
    private TransferType transferType;

    /** Fecha y hora del movimiento. */
    /** Cliente que pago. */
    private String payerCustomerId;

    /** Si el pago lo hizo un tercero. */
    private Boolean thirdParty;

    /** Origen del movimiento. */
    private TransactionOrigin origin;

    /** Cuenta afectada (debito y Yanki). */
    private String accountId;

    /** Referencia del origen. */
    private String reference;

    private LocalDateTime createdAt;
}
