package com.bank.credit.exception;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo uniforme de las respuestas de error de la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /** Momento en que se genero el error. */
    private LocalDateTime timestamp;

    /** Codigo de estado HTTP. */
    private int status;

    /** Nombre corto del estado HTTP. */
    private String error;

    /** Descripcion del problema. */
    private String message;

    /** Ruta de la peticion que fallo. */
    private String path;
}
