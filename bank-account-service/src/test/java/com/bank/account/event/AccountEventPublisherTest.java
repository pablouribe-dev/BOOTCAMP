package com.bank.account.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.config.BankKafkaProperties;
import com.bank.account.model.Account;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import io.reactivex.rxjava3.core.Completable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private AccountEventPublisher publisher;

    private final Account account = Account.builder().id("a1").customerId("c1").accountType(AccountType.SAVINGS)
            .accountNumber("123").profile(AccountProfile.VIP).build();

    @BeforeEach
    void setUp() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("account-events", "bank.account.events",
                "operation-results", "bank.account.operation-results"));
        publisher = new AccountEventPublisher(eventPublisher, properties);
        when(eventPublisher.publish(anyString(), anyString(), anyString(), any())).thenReturn(Completable.complete());
    }

    @Test
    void publishesSnapshotsKeyedByAccountId() {
        publisher.publishUpserted(account).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        publisher.publishDeleted(account).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish("bank.account.events", "a1", "ACCOUNT_UPSERTED", AccountEventPayload.builder()
                .accountId("a1").customerId("c1").accountType(AccountType.SAVINGS).accountNumber("123")
                .profile(AccountProfile.VIP).build());
        verify(eventPublisher).publish("bank.account.events", "a1", "ACCOUNT_DELETED",
                AccountEventPayload.builder().accountId("a1").customerId("c1").build());
    }

    @Test
    void publishesOperationResultsKeyedByAccountId() {
        AccountOperationResultPayload result = AccountOperationResultPayload.builder().operationId("op")
                .accountId("a1").reason("INSUFFICIENT_FUNDS").build();

        publisher.publishOperationResult("ACCOUNT_OPERATION_REJECTED", result).test()
                .awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish("bank.account.operation-results", "a1", "ACCOUNT_OPERATION_REJECTED", result);
    }
}
