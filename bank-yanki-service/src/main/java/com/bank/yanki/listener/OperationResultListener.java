package com.bank.yanki.listener;

import com.bank.yanki.event.AccountOperationResultPayload;
import com.bank.yanki.event.EventEnvelope;
import com.bank.yanki.event.IdempotentEventProcessor;
import com.bank.yanki.service.YankiPaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.account.operation-results} y avanza los pagos Yanki.
 * Los resultados de la tarjeta de debito se ignoran (no tienen pago aqui).
 */
@Component
@RequiredArgsConstructor
public class OperationResultListener {

    private final YankiPaymentService yankiPaymentService;
    private final IdempotentEventProcessor idempotentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un resultado, una sola vez por {@code eventId}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.operation-results}")
    public void onOperationResult(EventEnvelope envelope) throws JsonProcessingException {
        AccountOperationResultPayload result =
                objectMapper.treeToValue(envelope.getPayload(), AccountOperationResultPayload.class);
        idempotentEventProcessor.processOnce(envelope,
                        () -> yankiPaymentService.handleOperationResult(envelope.getEventType(), result))
                .blockingAwait();
    }
}
