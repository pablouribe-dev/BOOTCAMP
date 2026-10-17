package com.bank.customer.event;

import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.model.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code CUSTOMER_UPSERTED} (snapshot completo) y de
 * {@code CUSTOMER_DELETED} (solo {@link #customerId}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEventPayload {

    /** Identificador del cliente; tambien es la clave del mensaje. */
    private String customerId;

    /** Tipo de cliente. */
    private CustomerType type;

    /** Perfil comercial. */
    private CustomerProfile profile;

    /** Tipo de documento. */
    private DocumentType documentType;

    /** Numero de documento. */
    private String documentNumber;
}
