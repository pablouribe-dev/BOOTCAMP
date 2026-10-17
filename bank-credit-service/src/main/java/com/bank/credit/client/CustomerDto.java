package com.bank.credit.client;

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

    /** Tipo de cliente, del que dependen las reglas de otorgamiento. */
    private CustomerType type;

    /** Nombre completo o razon social. */
    private String name;
}
