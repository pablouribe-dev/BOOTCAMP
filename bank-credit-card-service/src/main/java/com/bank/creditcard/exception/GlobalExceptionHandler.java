package com.bank.creditcard.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Traduce las excepciones del servicio a respuestas HTTP con cuerpo uniforme.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Recurso inexistente.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex,
                                                                ServerWebExchange exchange) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), exchange);
    }

    /**
     * Regla de negocio violada.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 409
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex,
                                                            ServerWebExchange exchange) {
        log.warn("Business rule violated: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), exchange);
    }

    /**
     * Violacion de un indice unico de MongoDB.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 409
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex,
                                                            ServerWebExchange exchange) {
        log.warn("Duplicate key: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "A credit card with the same cardNumber already exists", exchange);
    }

    /**
     * Servicio remoto no disponible.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 503
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRemoteService(ServiceUnavailableException ex,
                                                             ServerWebExchange exchange) {
        log.error("Remote service failure: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), exchange);
    }

    /**
     * Error de validacion de los DTOs anotados con {@code @Valid}.
     *
     * @param ex       excepcion de binding
     * @param exchange intercambio HTTP en curso
     * @return respuesta 400 con el detalle por campo
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException ex,
                                                          ServerWebExchange exchange) {
        String detail = ex.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return build(HttpStatus.BAD_REQUEST, detail, exchange);
    }

    /**
     * Cuerpo o parametro de entrada malformado.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 400
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInput(ServerWebInputException ex,
                                                     ServerWebExchange exchange) {
        log.warn("Malformed request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getReason(), exchange);
    }

    /**
     * Cualquier otra excepcion no prevista.
     *
     * @param ex       excepcion capturada
     * @param exchange intercambio HTTP en curso
     * @return respuesta 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", exchange);
    }

    /**
     * Arma la respuesta de error con el formato uniforme del proyecto.
     *
     * @param status   estado HTTP a devolver
     * @param message  descripcion del problema
     * @param exchange intercambio HTTP en curso, usado para obtener la ruta
     * @return entidad de respuesta lista para enviar
     */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, ServerWebExchange exchange) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
