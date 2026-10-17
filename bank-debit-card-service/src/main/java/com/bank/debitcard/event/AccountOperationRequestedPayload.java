package com.bank.debitcard.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code ACCOUNT_OPERATION_REQUESTED} que publica este servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationRequestedPayload {

    /** Evento de peticion de operacion. */
    public static final String ACCOUNT_OPERATION_REQUESTED = "ACCOUNT_OPERATION_REQUESTED";

    /** Origen de las peticiones de este servicio. */
    public static final String ORIGIN = "DEBIT_CARD";

    /** Identificador unico de la operacion. */
    private String operationId;

    /** Cuenta a cargar. */
    private String accountId;

    /** Siempre {@code WITHDRAWAL} en un pago con tarjeta. */
    private String operation;

    /** Importe. */
    private BigDecimal amount;

    /** {@code DEBIT_CARD}. */
    private String origin;

    /** Id de la tarjeta. */
    private String reference;
}
