package com.bank.yanki.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PUT /yanki/wallets/{id}/debit-card}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardLinkRequest {

    /** Numero de la tarjeta de debito a asociar. */
    @NotBlank(message = "cardNumber is required")
    private String cardNumber;
}
