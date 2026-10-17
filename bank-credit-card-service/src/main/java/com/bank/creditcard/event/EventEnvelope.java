package com.bank.creditcard.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sobre comun de todos los mensajes Kafka del banco.
 *
 * <pre>
 * { "eventId": "uuid", "eventType": "ACCOUNT_UPSERTED",
 *   "occurredAt": "2026-10-16T20:00:00Z", "payload": { } }
 * </pre>
 *
 * <p>Cada servicio define su propia copia de esta clase (no hay libreria
 * compartida). El {@code payload} viaja como JSON generico y se convierte al
 * tipo concreto segun {@link #eventType}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    /** Identificador unico del evento; base de la idempotencia de los consumidores. */
    private String eventId;

    /** Tipo de evento, por ejemplo {@code CUSTOMER_UPSERTED}. */
    private String eventType;

    /** Momento en que ocurrio el hecho, en UTC e ISO-8601. */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;

    /** Datos del evento. */
    private JsonNode payload;
}
