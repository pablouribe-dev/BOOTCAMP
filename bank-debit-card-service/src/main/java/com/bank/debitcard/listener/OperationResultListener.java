package com.bank.debitcard.listener;

import com.bank.debitcard.event.AccountOperationResultPayload;
import com.bank.debitcard.event.EventEnvelope;
import com.bank.debitcard.event.IdempotentEventProcessor;
import com.bank.debitcard.service.DebitPaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.account.operation-results} y avanza los pagos con
 * tarjeta de debito. Los resultados de Yanki se ignoran (no tienen pago aqui).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationResultListener {

    private final DebitPaymentService debitPaymentService;
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
                        () -> debitPaymentService.handleOperationResult(envelope.getEventType(), result))
                .blockingAwait();
    }
}
