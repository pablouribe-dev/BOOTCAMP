package com.bank.debitcard.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.config.BankKafkaProperties;
import com.bank.debitcard.model.CardStatus;
import com.bank.debitcard.model.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebitCardEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private DebitCardEventPublisher publisher;

    private final DebitCard card = DebitCard.builder().id("d1").cardNumber("5555").customerId("c1")
            .accountIds(List.of("a1", "a2")).mainAccountId("a1").status(CardStatus.ACTIVE).build();

    @BeforeEach
    void setUp() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("debit-card-events", "bank.debit-card.events",
                "operation-requests", "bank.account.operation-requests"));
        publisher = new DebitCardEventPublisher(eventPublisher, properties);
        when(eventPublisher.publish(anyString(), anyString(), anyString(), any())).thenReturn(Completable.complete());
    }

    @Test
    void publishesCardSnapshotsKeyedByCardId() {
        publisher.publishUpserted(card).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        publisher.publishDeleted(card).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish("bank.debit-card.events", "d1", "DEBIT_CARD_UPSERTED",
                DebitCardEventPayload.builder().cardId("d1").cardNumber("5555").customerId("c1")
                        .accountIds(List.of("a1", "a2")).mainAccountId("a1").status("ACTIVE").build());
        verify(eventPublisher).publish("bank.debit-card.events", "d1", "DEBIT_CARD_DELETED",
                DebitCardEventPayload.builder().cardId("d1").cardNumber("5555").customerId("c1").build());
    }

    @Test
    void withdrawalRequestsAreKeyedByAccount() {
        publisher.requestWithdrawal("op-1", "a2", BigDecimal.TEN, "d1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        verify(eventPublisher).publish("bank.account.operation-requests", "a2", "ACCOUNT_OPERATION_REQUESTED",
                AccountOperationRequestedPayload.builder().operationId("op-1").accountId("a2").operation("WITHDRAWAL")
                        .amount(BigDecimal.TEN).origin("DEBIT_CARD").reference("d1").build());
    }
}
