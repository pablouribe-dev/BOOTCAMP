package com.bank.yanki.listener;

import com.bank.yanki.event.DebitCardEventPayload;
import com.bank.yanki.event.EventEnvelope;
import com.bank.yanki.event.IdempotentEventProcessor;
import com.bank.yanki.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.debit-card.events} y mantiene {@code debit_cards_replica}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitCardEventListener {

    private final WalletService walletService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un evento de tarjeta, una sola vez por {@code eventId}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.debit-card-events}")
    public void onDebitCardEvent(EventEnvelope envelope) throws JsonProcessingException {
        DebitCardEventPayload payload = objectMapper.treeToValue(envelope.getPayload(), DebitCardEventPayload.class);
        idempotentEventProcessor.processOnce(envelope, () -> switch (envelope.getEventType()) {
            case DebitCardEventPayload.DEBIT_CARD_UPSERTED -> walletService.upsertDebitCard(payload);
            case DebitCardEventPayload.DEBIT_CARD_DELETED -> walletService.deleteDebitCard(payload.getCardId());
            default -> Completable.fromAction(() -> log.warn("Ignoring debit card event {}", envelope.getEventType()));
        }).blockingAwait();
    }
}
