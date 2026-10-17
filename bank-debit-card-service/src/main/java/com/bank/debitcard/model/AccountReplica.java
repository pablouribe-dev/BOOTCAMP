package com.bank.debitcard.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Copia local de una cuenta (coleccion {@code accounts_replica}), alimentada
 * por {@code bank.account.events}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts_replica")
public class AccountReplica {

    /** Identificador de la cuenta (el mismo de {@code bank-account-service}). */
    @Id
    private String accountId;

    /** Cliente dueno. */
    private String customerId;

    /** Tipo de cuenta. */
    private String accountType;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Perfil. */
    private String profile;

    /** Ultima actualizacion de la copia. */
    private Instant updatedAt;
}
