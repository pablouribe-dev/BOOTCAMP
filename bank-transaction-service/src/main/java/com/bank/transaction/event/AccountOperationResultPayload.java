package com.bank.transaction.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copia local del payload de {@code ACCOUNT_OPERATION_COMPLETED} (publicado por
 * {@code bank-account-service}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationResultPayload {

    /** Operacion aplicada. */
    public static final String ACCOUNT_OPERATION_COMPLETED = "ACCOUNT_OPERATION_COMPLETED";

    /** Identificador de la operacion. */
    private String operationId;

    /** Cuenta afectada. */
    private String accountId;

    /** Cliente dueno de la cuenta. */
    private String customerId;

    /** {@code DEPOSIT} o {@code WITHDRAWAL}. */
    private String operation;

    /** Importe. */
    private BigDecimal amount;

    /** Saldo despues de la operacion. */
    private BigDecimal balanceAfter;

    /** {@code DEBIT_CARD} o {@code YANKI}. */
    private String origin;

    /** Id de la tarjeta de debito o del pago Yanki. */
    private String reference;
}
