package com.bank.transaction.dto;

import com.bank.transaction.model.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada del pago de un credito o de una tarjeta de credito.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    /** Tipo de producto a pagar: {@code CREDIT} o {@code CREDIT_CARD}. */
    @NotNull(message = "productType is required")
    private ProductType productType;

    /** Identificador del producto a pagar. Obligatorio. */
    @NotBlank(message = "productId is required")
    private String productId;

    /** Importe del pago. Obligatorio y mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion libre del movimiento. */
    private String description;

    /**
     * Parte III, opcional: cliente que paga. Si no viene, paga el titular; si
     * es otro cliente, el pago queda marcado como de un tercero.
     */
    private String payerCustomerId;
}
