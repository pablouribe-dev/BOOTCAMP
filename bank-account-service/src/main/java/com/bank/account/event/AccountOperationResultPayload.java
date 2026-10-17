package com.bank.account.event;

import com.bank.account.model.BalanceOperationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code ACCOUNT_OPERATION_COMPLETED} y
 * {@code ACCOUNT_OPERATION_REJECTED}.
 *
 * <p>Un completado lleva todos los campos menos {@link #reason}; un rechazo
 * lleva {@code operationId}, {@code accountId} y {@code reason} (y, para
 * facilitar el enlace en los consumidores, el origen y la referencia).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountOperationResultPayload {

    /** Operacion aplicada. */
    public static final String ACCOUNT_OPERATION_COMPLETED = "ACCOUNT_OPERATION_COMPLETED";

    /** Operacion rechazada. */
    public static final String ACCOUNT_OPERATION_REJECTED = "ACCOUNT_OPERATION_REJECTED";

    /** Motivo: fondos insuficientes. */
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    /** Motivo: la cuenta no existe. */
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    /** Identificador de la operacion pedida. */
    private String operationId;

    /** Cuenta. */
    private String accountId;

    /** Cliente dueno de la cuenta (solo completados). */
    private String customerId;

    /** Operacion. */
    private BalanceOperationType operation;

    /** Importe. */
    private BigDecimal amount;

    /** Saldo despues de aplicar la operacion (solo completados). */
    private BigDecimal balanceAfter;

    /** Origen de la peticion. */
    private String origin;

    /** Referencia del origen. */
    private String reference;

    /** Motivo del rechazo (solo rechazos). */
    private String reason;
}
