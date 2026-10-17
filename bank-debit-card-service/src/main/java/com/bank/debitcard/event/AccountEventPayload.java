package com.bank.debitcard.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copia local del payload de {@code ACCOUNT_UPSERTED} / {@code ACCOUNT_DELETED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEventPayload {

    /** Evento con el snapshot de la cuenta. */
    public static final String ACCOUNT_UPSERTED = "ACCOUNT_UPSERTED";

    /** Evento de baja de la cuenta. */
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";

    /** Identificador de la cuenta. */
    private String accountId;

    /** Cliente dueno. */
    private String customerId;

    /** Tipo de cuenta. */
    private String accountType;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Perfil. */
    private String profile;
}
