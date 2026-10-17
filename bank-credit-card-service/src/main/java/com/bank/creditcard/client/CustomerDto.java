package com.bank.creditcard.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /customers/{id}} que necesita este
 * servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    /** Identificador del cliente. */
    private String id;

    /** Tipo de cliente, con el que debe coincidir el tipo de tarjeta. */
    private CustomerType type;

    /** Nombre completo o razon social. */
    private String name;
}
