package com.bank.yanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code POST /yanki/payments}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YankiPaymentRequest {

    /** Celular del emisor. */
    @NotBlank(message = "fromPhoneNumber is required")
    private String fromPhoneNumber;

    /** Celular del receptor. */
    @NotBlank(message = "toPhoneNumber is required")
    private String toPhoneNumber;

    /** Importe, mayor que cero. */
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    /** Descripcion (opcional). */
    private String description;
}
