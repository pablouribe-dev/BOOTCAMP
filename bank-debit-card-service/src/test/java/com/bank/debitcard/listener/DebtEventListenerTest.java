package com.bank.debitcard.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.event.DebtEventPayload;
import com.bank.debitcard.event.EventEnvelope;
import com.bank.debitcard.event.IdempotentEventProcessor;
import com.bank.debitcard.service.OverdueDebtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebtEventListenerTest {

    @Mock
    private OverdueDebtService overdueDebtService;

    @Mock
    private IdempotentEventProcessor idempotentEventProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private DebtEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DebtEventListener(overdueDebtService, idempotentEventProcessor, objectMapper);
    }

    private EventEnvelope envelope(String type) {
        DebtEventPayload payload = DebtEventPayload.builder().customerId("c1").productType("CREDIT_CARD")
                .productId("k1").dueDate(LocalDate.of(2026, 10, 1)).dueAmount(BigDecimal.TEN).build();
        return EventEnvelope.builder().eventId("e-" + type).eventType(type)
                .payload(objectMapper.valueToTree(payload)).build();
    }

    @SuppressWarnings("unchecked")
    private void runHandlerDirectly() {
        when(idempotentEventProcessor.processOnce(any(), any())).thenAnswer(invocation ->
                ((Supplier<Completable>) invocation.getArgument(1)).get());
    }

    @Test
    void detectedRegistersTheDebt() throws Exception {
        runHandlerDirectly();
        when(overdueDebtService.registerOverdue(any())).thenReturn(Completable.complete());

        listener.onDebtEvent(envelope(DebtEventPayload.DEBT_OVERDUE_DETECTED));

        verify(overdueDebtService).registerOverdue(DebtEventPayload.builder().customerId("c1")
                .productType("CREDIT_CARD").productId("k1").dueDate(LocalDate.of(2026, 10, 1))
                .dueAmount(BigDecimal.TEN).build());
    }

    @Test
    void clearedRemovesTheDebt() throws Exception {
        runHandlerDirectly();
        when(overdueDebtService.clearOverdue(any())).thenReturn(Completable.complete());

        listener.onDebtEvent(envelope(DebtEventPayload.DEBT_OVERDUE_CLEARED));

        verify(overdueDebtService).clearOverdue(any());
    }

    @Test
    void unknownEventsAreIgnored() throws Exception {
        runHandlerDirectly();

        listener.onDebtEvent(envelope("SOMETHING_ELSE"));

        verify(overdueDebtService, never()).registerOverdue(any());
        verify(overdueDebtService, never()).clearOverdue(any());
    }

    @Test
    void repeatedEventsAreDiscardedByTheIdempotencyGuard() throws Exception {
        when(idempotentEventProcessor.processOnce(any(), any())).thenReturn(Completable.complete());

        listener.onDebtEvent(envelope(DebtEventPayload.DEBT_OVERDUE_DETECTED));

        verify(overdueDebtService, never()).registerOverdue(any());
    }
}
