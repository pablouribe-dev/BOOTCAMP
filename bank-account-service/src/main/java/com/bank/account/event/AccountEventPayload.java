package com.bank.account.event;

import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code ACCOUNT_UPSERTED} (snapshot) y de {@code ACCOUNT_DELETED}
 * (solo {@link #accountId} y {@link #customerId}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEventPayload {

    /** Identificador de la cuenta; tambien es la clave del mensaje. */
    private String accountId;

    /** Cliente dueno de la cuenta. */
    private String customerId;

    /** Tipo de cuenta. */
    private AccountType accountType;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Perfil de la cuenta. */
    private AccountProfile profile;
}
