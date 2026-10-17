package com.bank.yanki.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copia local del payload de {@code ACCOUNT_OPERATION_COMPLETED} y
 * {@code ACCOUNT_OPERATION_REJECTED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationResultPayload {

    /** Operacion aplicada. */
    public static final String ACCOUNT_OPERATION_COMPLETED = "ACCOUNT_OPERATION_COMPLETED";

    /** Operacion rechazada. */
    public static final String ACCOUNT_OPERATION_REJECTED = "ACCOUNT_OPERATION_REJECTED";

    /** Identificador de la operacion. */
    private String operationId;

    /** Cuenta. */
    private String accountId;

    /** Cliente dueno. */
    private String customerId;

    /** Operacion. */
    private String operation;

    /** Importe. */
    private BigDecimal amount;

    /** Saldo resultante. */
    private BigDecimal balanceAfter;

    /** Origen. */
    private String origin;

    /** Referencia. */
    private String reference;

    /** Motivo del rechazo. */
    private String reason;
}
