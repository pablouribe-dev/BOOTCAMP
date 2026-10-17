package com.bank.credit.dto;

import com.bank.credit.model.CreditType;
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
 * Datos de entrada para otorgar o actualizar un credito.
 *
 * <p>En una actualizacion ({@code PUT}) el tipo de credito y el cliente son
 * inmutables, y el importe otorgado solo puede cambiar mientras el credito no
 * haya recibido pagos.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequest {

    /** Tipo de credito. Obligatorio y debe coincidir con el tipo de cliente. */
    @NotNull(message = "creditType is required")
    private CreditType creditType;

    /** Identificador del cliente. Obligatorio. */
    @NotBlank(message = "customerId is required")
    private String customerId;

    /** Importe otorgado. Obligatorio y mayor que cero. */
    @NotNull(message = "grantedAmount is required")
    @Positive(message = "grantedAmount must be positive")
    private BigDecimal grantedAmount;

    /** Tasa de interes en porcentaje. Obligatoria. */
    @NotNull(message = "interestRate is required")
    @PositiveOrZero(message = "interestRate must be zero or positive")
    private BigDecimal interestRate;

    /** Parte III, opcional: fecha de vencimiento de la cuota (se fija con el PUT). */
    private LocalDate dueDate;

    /** Parte III, opcional: importe exigible a la fecha de vencimiento. */
    @PositiveOrZero(message = "dueAmount must be zero or positive")
    private BigDecimal dueAmount;
}
