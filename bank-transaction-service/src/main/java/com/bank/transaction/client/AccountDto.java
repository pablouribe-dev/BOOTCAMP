package com.bank.transaction.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /accounts/{id}} que necesita este
 * servicio para validar las reglas del movimiento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    /** Identificador de la cuenta. */
    private String id;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Tipo de cuenta, del que dependen las restricciones de movimiento. */
    private AccountTypeDto accountType;

    /** Identificador del cliente dueno de la cuenta. */
    private String customerId;

    /** Saldo disponible. */
    private BigDecimal balance;

    /** Limite de movimientos mensuales, si aplica. */
    private Integer monthlyMovementLimit;

    /** Dia del mes en que la cuenta a plazo fijo admite su movimiento. */
    private Integer movementDay;

    /** Depositos y retiros al mes sin comision. */
    private Integer freeTransactionLimit;

    /** Comision por cada deposito o retiro que exceda el maximo gratuito. */
    private BigDecimal transactionFee;
}
