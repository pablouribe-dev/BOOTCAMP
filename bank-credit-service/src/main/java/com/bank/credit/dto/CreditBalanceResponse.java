package com.bank.credit.dto;

import com.bank.credit.model.CreditType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saldo pendiente de un credito.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBalanceResponse {

    /** Identificador del credito. */
    private String creditId;

    /** Tipo de credito. */
    private CreditType creditType;

    /** Importe otorgado. */
    private BigDecimal grantedAmount;

    /** Saldo pendiente de pago. */
    private BigDecimal outstandingBalance;
}
