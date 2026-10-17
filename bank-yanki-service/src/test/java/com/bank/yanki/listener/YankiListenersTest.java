package com.bank.yanki.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.yanki.event.AccountOperationResultPayload;
import com.bank.yanki.event.DebitCardEventPayload;
import com.bank.yanki.event.EventEnvelope;
import com.bank.yanki.event.IdempotentEventProcessor;
import com.bank.yanki.service.WalletService;
import com.bank.yanki.service.YankiPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.reactivex.rxjava3.core.Completable;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YankiListenersTest {

    @Mock
    private WalletService walletService;

    @Mock
    private YankiPaymentService yankiPaymentService;

    @Mock
    private IdempotentEventProcessor idempotentEventProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runHandlersDirectly() {
        when(idempotentEventProcessor.processOnce(any(), any())).thenAnswer(invocation ->
                ((Supplier<Completable>) invocation.getArgument(1)).get());
    }

    private EventEnvelope envelope(String type, Object payload) {
        return EventEnvelope.builder().eventId("e-" + type).eventType(type)
                .payload(objectMapper.valueToTree(payload)).build();
    }

    @Test
    void debitCardEventsFeedTheReplica() throws Exception {
        DebitCardEventListener listener = new DebitCardEventListener(walletService, idempotentEventProcessor,
                objectMapper);
        DebitCardEventPayload payload = DebitCardEventPayload.builder().cardId("d1").cardNumber("5555").build();
        when(walletService.upsertDebitCard(payload)).thenReturn(Completable.complete());
        when(walletService.deleteDebitCard("d1")).thenReturn(Completable.complete());

        listener.onDebitCardEvent(envelope("DEBIT_CARD_UPSERTED", payload));
        listener.onDebitCardEvent(envelope("DEBIT_CARD_DELETED", payload));
        listener.onDebitCardEvent(envelope("OTHER", payload));

        verify(walletService).upsertDebitCard(payload);
        verify(walletService).deleteDebitCard("d1");
    }

    @Test
    void operationResultsAdvanceThePayments() throws Exception {
        OperationResultListener listener = new OperationResultListener(yankiPaymentService, idempotentEventProcessor,
                objectMapper);
        AccountOperationResultPayload result = AccountOperationResultPayload.builder().operationId("op").build();
        when(yankiPaymentService.handleOperationResult(eq("ACCOUNT_OPERATION_COMPLETED"), any()))
                .thenReturn(Completable.complete());

        listener.onOperationResult(envelope("ACCOUNT_OPERATION_COMPLETED", result));

        verify(yankiPaymentService).handleOperationResult("ACCOUNT_OPERATION_COMPLETED", result);
    }
}
