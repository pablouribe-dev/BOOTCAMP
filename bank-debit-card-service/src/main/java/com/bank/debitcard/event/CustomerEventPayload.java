package com.bank.debitcard.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copia local del payload de {@code CUSTOMER_UPSERTED} / {@code CUSTOMER_DELETED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEventPayload {

    /** Evento con el snapshot del cliente. */
    public static final String CUSTOMER_UPSERTED = "CUSTOMER_UPSERTED";

    /** Evento de baja del cliente. */
    public static final String CUSTOMER_DELETED = "CUSTOMER_DELETED";

    /** Identificador del cliente. */
    private String customerId;

    /** Tipo de cliente. */
    private String type;

    /** Perfil. */
    private String profile;

    /** Tipo de documento. */
    private String documentType;

    /** Numero de documento. */
    private String documentNumber;
}
