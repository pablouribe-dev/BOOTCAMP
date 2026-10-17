package com.bank.transaction.listener;

import com.bank.transaction.event.AccountOperationResultPayload;
import com.bank.transaction.event.EventEnvelope;
import com.bank.transaction.event.IdempotentEventProcessor;
import com.bank.transaction.service.EventMovementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.account.operation-results} y registra un movimiento por
 * cada {@code ACCOUNT_OPERATION_COMPLETED}. Los rechazos no generan movimiento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationResultListener {

    private final EventMovementService eventMovementService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un resultado de operacion, una sola vez por {@code eventId}.
     * Corre en un hilo de Kafka, por eso espera con {@code blockingAwait()}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.operation-results}")
    public void onOperationResult(EventEnvelope envelope) throws JsonProcessingException {
        if (!AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED.equals(envelope.getEventType())) {
            log.debug("Ignoring {} eventId={}", envelope.getEventType(), envelope.getEventId());
            return;
        }
        AccountOperationResultPayload result =
                objectMapper.treeToValue(envelope.getPayload(), AccountOperationResultPayload.class);
        idempotentEventProcessor.processOnce(envelope,
                        () -> eventMovementService.recordCompletedOperation(envelope.getEventId(), result))
                .blockingAwait();
    }
}
