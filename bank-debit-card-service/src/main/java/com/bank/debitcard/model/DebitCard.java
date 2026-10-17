package com.bank.debitcard.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tarjeta de debito (coleccion {@code debit_cards}, base {@code bank_debit_cards}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "debit_cards")
public class DebitCard {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Numero de tarjeta generado, unico. */
    @Indexed(unique = true)
    private String cardNumber;

    /** Cliente titular. */
    @Indexed
    private String customerId;

    /** Cuentas asociadas, en orden de asociacion. */
    @Builder.Default
    private List<String> accountIds = new ArrayList<>();

    /** Cuenta principal: la primera que se intenta cobrar. */
    private String mainAccountId;

    /** Estado. */
    @Builder.Default
    private CardStatus status = CardStatus.ACTIVE;

    /** Fecha y hora de emision. */
    private LocalDateTime createdAt;
}
