package com.bank.transaction.dto;

import com.bank.transaction.model.TransferType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de una transferencia: los dos movimientos registrados con el mismo
 * {@code transferId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    /** Identificador comun a los dos movimientos. */
    private String transferId;

    /** Propia o a terceros. */
    private TransferType transferType;

    /** Importe transferido. */
    private BigDecimal amount;

    /** Movimiento {@code TRANSFER_OUT} en la cuenta origen. */
    private TransactionResponse outgoing;

    /** Movimiento {@code TRANSFER_IN} en la cuenta destino. */
    private TransactionResponse incoming;
}
