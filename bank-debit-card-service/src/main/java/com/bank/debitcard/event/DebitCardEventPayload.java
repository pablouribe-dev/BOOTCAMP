package com.bank.debitcard.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code DEBIT_CARD_UPSERTED} (snapshot) y {@code DEBIT_CARD_DELETED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCardEventPayload {

    /** Evento con el snapshot de la tarjeta. */
    public static final String DEBIT_CARD_UPSERTED = "DEBIT_CARD_UPSERTED";

    /** Evento de baja de la tarjeta. */
    public static final String DEBIT_CARD_DELETED = "DEBIT_CARD_DELETED";

    /** Identificador de la tarjeta; clave del mensaje. */
    private String cardId;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Cliente titular. */
    private String customerId;

    /** Cuentas asociadas, en orden. */
    private List<String> accountIds;

    /** Cuenta principal. */
    private String mainAccountId;

    /** {@code ACTIVE} o {@code INACTIVE}. */
    private String status;
}
