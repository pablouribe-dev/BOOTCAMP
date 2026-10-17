package com.bank.transaction.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /customers/{id}} que necesita este
 * servicio (Parte III: validar al pagador de un pago de terceros).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    /** Identificador del cliente. */
    private String id;

    /** Nombre o razon social. */
    private String name;
}
