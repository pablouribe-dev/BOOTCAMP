package com.bank.debitcard.listener;

import com.bank.debitcard.event.AccountEventPayload;
import com.bank.debitcard.event.EventEnvelope;
import com.bank.debitcard.event.IdempotentEventProcessor;
import com.bank.debitcard.service.DebitCardService;
import com.bank.debitcard.service.ReplicaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.account.events}: mantiene {@code accounts_replica} y, ante
 * una baja, quita la cuenta de las tarjetas que la tengan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final ReplicaService replicaService;
    private final DebitCardService debitCardService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un evento de cuenta, una sola vez por {@code eventId}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.account-events}")
    public void onAccountEvent(EventEnvelope envelope) throws JsonProcessingException {
        AccountEventPayload payload = objectMapper.treeToValue(envelope.getPayload(), AccountEventPayload.class);
        idempotentEventProcessor.processOnce(envelope, () -> switch (envelope.getEventType()) {
            case AccountEventPayload.ACCOUNT_UPSERTED -> replicaService.upsertAccount(payload);
            case AccountEventPayload.ACCOUNT_DELETED -> replicaService.deleteAccount(payload.getAccountId())
                    .andThen(debitCardService.removeDeletedAccount(payload.getAccountId()));
            default -> Completable.fromAction(() -> log.warn("Ignoring account event {}", envelope.getEventType()));
        }).blockingAwait();
    }
}
