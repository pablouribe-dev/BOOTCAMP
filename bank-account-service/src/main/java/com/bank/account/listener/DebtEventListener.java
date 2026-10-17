package com.bank.account.listener;

import com.bank.account.event.DebtEventPayload;
import com.bank.account.event.EventEnvelope;
import com.bank.account.event.IdempotentEventProcessor;
import com.bank.account.service.OverdueDebtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.debt.events} y mantiene la copia local
 * {@code overdue_debts}.
 *
 * <p>Corre en un hilo de Kafka, por eso aqui si se espera el resultado con
 * {@code blockingAwait()}. Si falla, el error handler reintenta y luego envia
 * el mensaje al DLT.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtEventListener {

    private final OverdueDebtService overdueDebtService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un evento de deuda, una sola vez por {@code eventId}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.debt-events}")
    public void onDebtEvent(EventEnvelope envelope) throws JsonProcessingException {
        log.debug("Received {} eventId={}", envelope.getEventType(), envelope.getEventId());
        DebtEventPayload payload = objectMapper.treeToValue(envelope.getPayload(), DebtEventPayload.class);
        idempotentEventProcessor.processOnce(envelope, () -> handle(envelope.getEventType(), payload))
                .blockingAwait();
    }

    /**
     * Elige la accion segun el tipo de evento.
     *
     * @param eventType tipo de evento
     * @param payload   datos de la deuda
     * @return accion a ejecutar
     */
    private Completable handle(String eventType, DebtEventPayload payload) {
        return switch (eventType) {
            case DebtEventPayload.DEBT_OVERDUE_DETECTED -> overdueDebtService.registerOverdue(payload);
            case DebtEventPayload.DEBT_OVERDUE_CLEARED -> overdueDebtService.clearOverdue(payload);
            default -> Completable.fromAction(() -> log.warn("Ignoring unknown debt event {}", eventType));
        };
    }
}
