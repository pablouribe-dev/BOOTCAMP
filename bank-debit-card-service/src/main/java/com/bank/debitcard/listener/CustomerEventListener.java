package com.bank.debitcard.listener;

import com.bank.debitcard.event.CustomerEventPayload;
import com.bank.debitcard.event.EventEnvelope;
import com.bank.debitcard.event.IdempotentEventProcessor;
import com.bank.debitcard.service.ReplicaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.customer.events} y mantiene {@code customers_replica}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerEventListener {

    private final ReplicaService replicaService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un evento de cliente, una sola vez por {@code eventId}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.customer-events}")
    public void onCustomerEvent(EventEnvelope envelope) throws JsonProcessingException {
        CustomerEventPayload payload = objectMapper.treeToValue(envelope.getPayload(), CustomerEventPayload.class);
        idempotentEventProcessor.processOnce(envelope, () -> switch (envelope.getEventType()) {
            case CustomerEventPayload.CUSTOMER_UPSERTED -> replicaService.upsertCustomer(payload);
            case CustomerEventPayload.CUSTOMER_DELETED -> replicaService.deleteCustomer(payload.getCustomerId());
            default -> Completable.fromAction(() -> log.warn("Ignoring customer event {}", envelope.getEventType()));
        }).blockingAwait();
    }
}
