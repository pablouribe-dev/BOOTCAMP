package com.bank.account.listener;

import com.bank.account.event.AccountOperationRequestedPayload;
import com.bank.account.event.EventEnvelope;
import com.bank.account.service.AccountOperationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code bank.account.operation-requests}.
 *
 * <p>La clave de los mensajes es el {@code accountId}: las operaciones de una
 * misma cuenta llegan a la misma particion y se procesan una tras otra. La
 * idempotencia es por {@code operationId} (en el servicio).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOperationListener {

    private final AccountOperationService accountOperationService;
    private final ObjectMapper objectMapper;

    /**
     * Procesa una peticion de operacion. Corre en un hilo de Kafka, por eso
     * espera el resultado con {@code blockingAwait()}.
     *
     * @param envelope evento recibido
     * @throws JsonProcessingException si el payload no corresponde al evento
     */
    @KafkaListener(topics = "${bank.kafka.topics.operation-requests}")
    public void onOperationRequested(EventEnvelope envelope) throws JsonProcessingException {
        if (!AccountOperationRequestedPayload.ACCOUNT_OPERATION_REQUESTED.equals(envelope.getEventType())) {
            log.warn("Ignoring unexpected event {} on operation requests", envelope.getEventType());
            return;
        }
        AccountOperationRequestedPayload request =
                objectMapper.treeToValue(envelope.getPayload(), AccountOperationRequestedPayload.class);
        accountOperationService.process(request).blockingAwait();
    }
}
