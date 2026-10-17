package com.bank.account.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Promedio diario del saldo de una cuenta en un mes.
 *
 * <p>El sistema solo informa si se cumple el minimo; no aplica penalidades.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AverageDailyBalanceResponse {

    /** Identificador de la cuenta. */
    private String accountId;

    /** Mes consultado, en formato {@code YYYY-MM}. */
    private String month;

    /** Promedio del saldo de cierre de cada dia considerado. */
    private BigDecimal averageDailyBalance;

    /** Minimo exigido; {@code null} si la cuenta no tiene minimo (no es VIP). */
    private BigDecimal minimumDailyAverage;

    /** Si cumple el minimo; {@code null} cuando la cuenta no tiene minimo. */
    private Boolean meetsMinimum;
}
