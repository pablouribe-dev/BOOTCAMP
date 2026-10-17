package com.bank.debitcard.dto;

import com.bank.debitcard.model.CardStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tarjeta de debito expuesta por la API. El saldo se consulta con
 * {@code GET /accounts/{mainAccountId}/balance}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCardResponse {

    /** Identificador. */
    private String id;

    /** Numero de tarjeta. */
    private String cardNumber;

    /** Cliente titular. */
    private String customerId;

    /** Cuentas asociadas, en orden. */
    private List<String> accountIds;

    /** Cuenta principal. */
    private String mainAccountId;

    /** Estado. */
    private CardStatus status;

    /** Fecha y hora de emision. */
    private LocalDateTime createdAt;
}
