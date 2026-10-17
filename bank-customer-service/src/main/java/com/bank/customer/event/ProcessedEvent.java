package com.bank.customer.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de un evento ya procesado (coleccion {@code processed_events}).
 *
 * <p>Kafka entrega al menos una vez: antes de procesar un evento se busca su
 * {@code eventId} aqui y, si ya esta, se descarta.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processed_events")
public class ProcessedEvent {

    /** Identificador del evento; al ser el {@code _id} es unico. */
    @Id
    private String eventId;

    /** Tipo de evento procesado. */
    private String eventType;

    /** Momento en que se proceso. */
    private Instant processedAt;
}
