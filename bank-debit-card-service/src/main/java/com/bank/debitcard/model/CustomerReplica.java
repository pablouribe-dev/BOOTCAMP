package com.bank.debitcard.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Copia local de un cliente (coleccion {@code customers_replica}), alimentada
 * por {@code bank.customer.events}. Evita consultar por REST a
 * {@code bank-customer-service}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers_replica")
public class CustomerReplica {

    /** Identificador del cliente (el mismo de {@code bank-customer-service}). */
    @Id
    private String customerId;

    /** Tipo de cliente. */
    private String type;

    /** Perfil. */
    private String profile;

    /** Tipo de documento. */
    private String documentType;

    /** Numero de documento. */
    private String documentNumber;

    /** Ultima actualizacion de la copia. */
    private Instant updatedAt;
}
