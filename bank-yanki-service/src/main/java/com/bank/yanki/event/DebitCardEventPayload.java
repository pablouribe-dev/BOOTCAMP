package com.bank.yanki.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copia local del payload de {@code DEBIT_CARD_UPSERTED} / {@code DEBIT_CARD_DELETED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCardEventPayload {

    /** Snapshot de la tarjeta. */
    public static final String DEBIT_CARD_UPSERTED = "DEBIT_CARD_UPSERTED";

    /** Baja de la tarjeta. */
    public static final String DEBIT_CARD_DELETED = "DEBIT_CARD_DELETED";

    /** Identificador de la tarjeta. */
    private String cardId;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Cliente titular. */
    private String customerId;

    /** Cuentas asociadas. */
    private List<String> accountIds;

    /** Cuenta principal. */
    private String mainAccountId;

    /** Estado. */
    private String status;
}
