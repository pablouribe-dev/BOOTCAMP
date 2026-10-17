package com.bank.transaction.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.transaction.event.AccountOperationResultPayload;
import com.bank.transaction.event.EventEnvelope;
import com.bank.transaction.event.IdempotentEventProcessor;
import com.bank.transaction.service.EventMovementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationResultListenerTest {

    @Mock
    private EventMovementService eventMovementService;

    @Mock
    private IdempotentEventProcessor idempotentEventProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private OperationResultListener listener;

    private final AccountOperationResultPayload result = AccountOperationResultPayload.builder()
            .operationId("op-1").accountId("a1").operation("WITHDRAWAL").amount(BigDecimal.TEN)
            .origin("DEBIT_CARD").reference("d1").build();

    @BeforeEach
    void setUp() {
        listener = new OperationResultListener(eventMovementService, idempotentEventProcessor, objectMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completedOperationsAreRecordedOncePerEvent() throws Exception {
        when(idempotentEventProcessor.processOnce(any(), any())).thenAnswer(invocation ->
                ((Supplier<Completable>) invocation.getArgument(1)).get());
        when(eventMovementService.recordCompletedOperation(eq("e-1"), any())).thenReturn(Completable.complete());

        listener.onOperationResult(EventEnvelope.builder().eventId("e-1").eventType("ACCOUNT_OPERATION_COMPLETED")
                .payload(objectMapper.valueToTree(result)).build());

        verify(eventMovementService).recordCompletedOperation("e-1", result);
    }

    @Test
    void rejectionsDoNotCreateMovements() throws Exception {
        listener.onOperationResult(EventEnvelope.builder().eventId("e-2").eventType("ACCOUNT_OPERATION_REJECTED")
                .payload(objectMapper.valueToTree(result)).build());

        verify(idempotentEventProcessor, never()).processOnce(any(), any());
        verify(eventMovementService, never()).recordCompletedOperation(any(), any());
    }
}
