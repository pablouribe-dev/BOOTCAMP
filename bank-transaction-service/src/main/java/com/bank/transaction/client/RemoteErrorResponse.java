package com.bank.transaction.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de error que devuelven los servicios remotos del banco.
 *
 * <p>Solo interesa el mensaje, para poder propagarlo tal cual.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteErrorResponse {

    /** Descripcion del problema reportada por el servicio remoto. */
    private String message;
}
