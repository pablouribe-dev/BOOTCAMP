package com.bank.customer.event;

import com.bank.customer.adapter.ReactorRxAdapter;
import com.bank.customer.repository.ProcessedEventRepository;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Garantiza que cada evento se procese una sola vez, aunque Kafka lo entregue
 * repetido.
 *
 * <p>Si el {@code eventId} ya esta en {@code processed_events} se descarta; si
 * no, se ejecuta el manejador y recien despues se registra. Si el manejador
 * falla, el evento no queda registrado y el reintento lo vuelve a procesar.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentEventProcessor {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Ejecuta el manejador solo si el evento no fue procesado antes.
     *
     * @param envelope evento recibido
     * @param handler  logica a ejecutar (se invoca de forma diferida)
     * @return completable que termina cuando el evento fue procesado o descartado
     */
    public Completable processOnce(EventEnvelope envelope, Supplier<Completable> handler) {
        return ReactorRxAdapter.monoToSingle(processedEventRepository.existsById(envelope.getEventId()))
                .flatMapCompletable(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        log.info("Skipping duplicated event {} eventId={}",
                                envelope.getEventType(), envelope.getEventId());
                        return Completable.complete();
                    }
                    return Completable.defer(handler::get)
                            .andThen(Completable.defer(() -> ReactorRxAdapter.monoToCompletable(
                                    processedEventRepository.save(ProcessedEvent.builder()
                                            .eventId(envelope.getEventId())
                                            .eventType(envelope.getEventType())
                                            .processedAt(Instant.now())
                                            .build()))));
                });
    }
}
