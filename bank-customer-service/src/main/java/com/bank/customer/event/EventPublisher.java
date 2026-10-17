package com.bank.customer.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos en Kafka con el sobre comun {@link EventEnvelope}.
 *
 * <p>El envio corre en el scheduler {@code io} de RxJava, porque el productor
 * de Kafka puede bloquear mientras obtiene metadatos del broker; asi nunca se
 * bloquea un hilo de Netty.</p>
 */
@Slf4j
@Component
public class EventPublisher {

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final ObjectMapper payloadMapper;

    /**
     * Inyeccion por constructor.
     *
     * <p>El payload se convierte con una copia del mapper que conserva los
     * {@code BigDecimal} exactos: por defecto Jackson los normaliza al pasarlos
     * a {@code JsonNode} (10.00 quedaria como 1E+1).</p>
     *
     * @param kafkaTemplate plantilla de Kafka
     * @param objectMapper  mapper JSON de la aplicacion (fechas ISO-8601)
     */
    public EventPublisher(KafkaTemplate<String, EventEnvelope> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.payloadMapper = objectMapper.copy().setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    /**
     * Envuelve el payload y lo publica.
     *
     * @param topic     topic destino
     * @param key       clave del mensaje: el id del agregado, para conservar el orden por entidad
     * @param eventType tipo de evento
     * @param payload   datos del evento
     * @return completable que termina cuando el broker confirmo el mensaje
     */
    public Completable publish(String topic, String key, String eventType, Object payload) {
        return Completable.defer(() -> {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .occurredAt(Instant.now())
                    .payload(payloadMapper.valueToTree(payload))
                    .build();
            return Completable.fromCompletionStage(kafkaTemplate.send(topic, key, envelope))
                    .doOnComplete(() -> log.info("Published {} eventId={} key={} on {}",
                            eventType, envelope.getEventId(), key, topic));
        })
                .subscribeOn(Schedulers.io())
                .doOnError(ex -> log.error("Could not publish {} key={} on {}: {}",
                        eventType, key, topic, ex.getMessage()));
    }
}
