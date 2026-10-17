package com.bank.yanki.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code ACCOUNT_OPERATION_REQUESTED} que publica Yanki.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationRequestedPayload {

    /** Evento de peticion de operacion. */
    public static final String ACCOUNT_OPERATION_REQUESTED = "ACCOUNT_OPERATION_REQUESTED";

    /** Origen de las peticiones de este servicio. */
    public static final String ORIGIN = "YANKI";

    /** Identificador unico de la operacion. */
    private String operationId;

    /** Cuenta principal de la tarjeta asociada. */
    private String accountId;

    /** {@code DEPOSIT} o {@code WITHDRAWAL}. */
    private String operation;

    /** Importe. */
    private BigDecimal amount;

    /** {@code YANKI}. */
    private String origin;

    /** Id del pago Yanki. */
    private String reference;
}
