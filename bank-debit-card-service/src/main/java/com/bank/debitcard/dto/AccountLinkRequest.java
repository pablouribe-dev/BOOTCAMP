package com.bank.debitcard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code POST /debit-cards/{id}/accounts} y
 * {@code PUT /debit-cards/{id}/main-account}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLinkRequest {

    /** Cuenta a asociar o a marcar como principal. */
    @NotBlank(message = "accountId is required")
    private String accountId;
}
