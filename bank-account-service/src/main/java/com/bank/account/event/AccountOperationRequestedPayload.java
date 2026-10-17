package com.bank.account.event;

import com.bank.account.model.BalanceOperationType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code ACCOUNT_OPERATION_REQUESTED}: un deposito o retiro pedido
 * por {@code bank-debit-card-service} o {@code bank-yanki-service}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationRequestedPayload {

    /** Evento de peticion de operacion. */
    public static final String ACCOUNT_OPERATION_REQUESTED = "ACCOUNT_OPERATION_REQUESTED";

    /** Identificador unico de la operacion; base de la idempotencia. */
    private String operationId;

    /** Cuenta sobre la que se opera. */
    private String accountId;

    /** {@code DEPOSIT} o {@code WITHDRAWAL}. */
    private BalanceOperationType operation;

    /** Importe. */
    private BigDecimal amount;

    /** Origen: {@code DEBIT_CARD} o {@code YANKI}. */
    private String origin;

    /** Referencia del origen (id de la tarjeta o del pago Yanki). */
    private String reference;
}
