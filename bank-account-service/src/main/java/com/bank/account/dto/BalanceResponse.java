package com.bank.account.dto;

import com.bank.account.model.AccountType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saldo disponible de una cuenta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    /** Identificador de la cuenta. */
    private String accountId;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Cliente dueno de la cuenta (Parte III: lo necesita el resultado de las operaciones por Kafka). */
    private String customerId;

    /** Tipo de cuenta. */
    private AccountType accountType;

    /** Saldo disponible. */
    private BigDecimal balance;
}
