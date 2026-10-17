package com.bank.account.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.event.AccountOperationRequestedPayload;
import com.bank.account.event.EventEnvelope;
import com.bank.account.model.BalanceOperationType;
import com.bank.account.service.AccountOperationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountOperationListenerTest {

    @Mock
    private AccountOperationService accountOperationService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));

    private final AccountOperationRequestedPayload payload = AccountOperationRequestedPayload.builder()
            .operationId("op-1").accountId("a1").operation(BalanceOperationType.DEPOSIT)
            .amount(BigDecimal.TEN).origin("YANKI").reference("p-1").build();

    @Test
    void delegatesTheRequestToTheService() throws Exception {
        AccountOperationListener listener = new AccountOperationListener(accountOperationService, objectMapper);
        when(accountOperationService.process(any())).thenReturn(Completable.complete());

        listener.onOperationRequested(EventEnvelope.builder().eventId("e1")
                .eventType("ACCOUNT_OPERATION_REQUESTED").payload(objectMapper.valueToTree(payload)).build());

        verify(accountOperationService).process(payload);
    }

    @Test
    void ignoresOtherEventTypes() throws Exception {
        AccountOperationListener listener = new AccountOperationListener(accountOperationService, objectMapper);

        listener.onOperationRequested(EventEnvelope.builder().eventId("e1").eventType("SOMETHING_ELSE")
                .payload(objectMapper.valueToTree(payload)).build());

        verify(accountOperationService, never()).process(any());
    }
}
