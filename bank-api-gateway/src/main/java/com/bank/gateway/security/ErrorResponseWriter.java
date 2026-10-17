package com.bank.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Escribe respuestas de error con el mismo cuerpo que el resto del sistema:
 * {@code timestamp, status, error, message, path}.
 */
public final class ErrorResponseWriter {

    /**
     * Clase de utilidad: no se instancia.
     */
    private ErrorResponseWriter() {
    }

    /**
     * Escribe el error en la respuesta.
     *
     * @param exchange intercambio HTTP en curso
     * @param status   estado HTTP
     * @param message  descripcion del problema
     * @return completable de escritura
     */
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message.replace("\"", "'")
                + "\",\"path\":\"" + exchange.getRequest().getPath().value() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
