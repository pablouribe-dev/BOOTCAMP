package com.bank.debitcard.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.event.AccountEventPayload;
import com.bank.debitcard.event.AccountOperationResultPayload;
import com.bank.debitcard.event.CustomerEventPayload;
import com.bank.debitcard.event.EventEnvelope;
import com.bank.debitcard.event.IdempotentEventProcessor;
import com.bank.debitcard.service.DebitCardService;
import com.bank.debitcard.service.DebitPaymentService;
import com.bank.debitcard.service.ReplicaService;
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
class DebitCardListenersTest {

    @Mock
    private ReplicaService replicaService;

    @Mock
    private DebitCardService debitCardService;

    @Mock
    private DebitPaymentService debitPaymentService;

    @Mock
    private IdempotentEventProcessor idempotentEventProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runHandlersDirectly() {
        lenient().when(idempotentEventProcessor.processOnce(any(), any())).thenAnswer(invocation ->
                ((Supplier<Completable>) invocation.getArgument(1)).get());
    }

    private EventEnvelope envelope(String type, Object payload) {
        return EventEnvelope.builder().eventId("e-" + type).eventType(type)
                .payload(objectMapper.valueToTree(payload)).build();
    }

    @Test
    void customerEventsFeedTheReplica() throws Exception {
        CustomerEventListener listener = new CustomerEventListener(replicaService, idempotentEventProcessor,
                objectMapper);
        CustomerEventPayload payload = CustomerEventPayload.builder().customerId("c1").type("PERSONAL").build();
        when(replicaService.upsertCustomer(payload)).thenReturn(Completable.complete());
        when(replicaService.deleteCustomer("c1")).thenReturn(Completable.complete());

        listener.onCustomerEvent(envelope("CUSTOMER_UPSERTED", payload));
        listener.onCustomerEvent(envelope("CUSTOMER_DELETED", payload));
        listener.onCustomerEvent(envelope("OTHER", payload));

        verify(replicaService).upsertCustomer(payload);
        verify(replicaService).deleteCustomer("c1");
    }

    @Test
    void accountDeletionAlsoUnlinksTheAccountFromCards() throws Exception {
        AccountEventListener listener = new AccountEventListener(replicaService, debitCardService,
                idempotentEventProcessor, objectMapper);
        AccountEventPayload payload = AccountEventPayload.builder().accountId("a1").customerId("c1").build();
        when(replicaService.upsertAccount(payload)).thenReturn(Completable.complete());
        when(replicaService.deleteAccount("a1")).thenReturn(Completable.complete());
        when(debitCardService.removeDeletedAccount("a1")).thenReturn(Completable.complete());

        listener.onAccountEvent(envelope("ACCOUNT_UPSERTED", payload));
        listener.onAccountEvent(envelope("ACCOUNT_DELETED", payload));
        listener.onAccountEvent(envelope("OTHER", payload));

        verify(replicaService).upsertAccount(payload);
        verify(debitCardService).removeDeletedAccount("a1");
    }

    @Test
    void operationResultsAdvanceThePayments() throws Exception {
        OperationResultListener listener = new OperationResultListener(debitPaymentService, idempotentEventProcessor,
                objectMapper);
        AccountOperationResultPayload result = AccountOperationResultPayload.builder().operationId("op")
                .accountId("a1").reason("INSUFFICIENT_FUNDS").build();
        when(debitPaymentService.handleOperationResult(eq("ACCOUNT_OPERATION_REJECTED"), any()))
                .thenReturn(Completable.complete());

        listener.onOperationResult(envelope("ACCOUNT_OPERATION_REJECTED", result));

        verify(debitPaymentService).handleOperationResult("ACCOUNT_OPERATION_REJECTED", result);
    }

    @Test
    void repeatedEventsAreSkipped() throws Exception {
        org.mockito.Mockito.doReturn(Completable.complete()).when(idempotentEventProcessor).processOnce(any(), any());
        CustomerEventListener listener = new CustomerEventListener(replicaService, idempotentEventProcessor,
                objectMapper);

        listener.onCustomerEvent(envelope("CUSTOMER_UPSERTED", CustomerEventPayload.builder().customerId("c1")
                .build()));

        verify(replicaService, never()).upsertCustomer(any());
    }
}
