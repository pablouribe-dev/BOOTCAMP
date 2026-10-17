package com.bank.yanki.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Copia local de una tarjeta de debito (coleccion {@code debit_cards_replica}),
 * alimentada por {@code bank.debit-card.events}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "debit_cards_replica")
public class DebitCardReplica {

    /** Identificador de la tarjeta (el de {@code bank-debit-card-service}). */
    @Id
    private String cardId;

    /** Numero de tarjeta. */
    @Indexed(unique = true)
    private String cardNumber;

    /** Cliente titular. */
    private String customerId;

    /** Cuenta principal: donde se carga y acredita el saldo Yanki. */
    private String mainAccountId;

    /** {@code ACTIVE} o {@code INACTIVE}. */
    private String status;

    /** Ultima actualizacion de la copia. */
    private Instant updatedAt;
}
