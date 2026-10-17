package com.bank.creditcard.dto;

import com.bank.creditcard.model.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada para emitir o actualizar una tarjeta de credito.
 *
 * <p>En una actualizacion ({@code PUT}) el tipo de tarjeta y el cliente son
 * inmutables, y la nueva linea de credito no puede quedar por debajo del
 * importe ya consumido.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardRequest {

    /** Tipo de tarjeta. Obligatorio y debe coincidir con el tipo de cliente. */
    @NotNull(message = "cardType is required")
    private CardType cardType;

    /** Identificador del cliente titular. Obligatorio. */
    @NotBlank(message = "customerId is required")
    private String customerId;

    /** Linea de credito. Obligatoria y mayor que cero. */
    @NotNull(message = "creditLimit is required")
    @Positive(message = "creditLimit must be positive")
    private BigDecimal creditLimit;

    /** Parte III, opcional: fecha limite de pago (se fija con el PUT). */
    private LocalDate paymentDueDate;

    /** Parte III, opcional: importe exigible a la fecha limite de pago. */
    @PositiveOrZero(message = "dueAmount must be zero or positive")
    private BigDecimal dueAmount;
}
