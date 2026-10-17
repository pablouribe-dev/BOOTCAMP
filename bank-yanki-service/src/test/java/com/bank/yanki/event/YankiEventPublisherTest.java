package com.bank.yanki.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.yanki.config.BankKafkaProperties;
import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.YankiPayment;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YankiEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private YankiEventPublisher publisher;

    @BeforeEach
    void setUp() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("operation-requests", "bank.account.operation-requests",
                "yanki-events", "bank.yanki.events"));
        publisher = new YankiEventPublisher(eventPublisher, properties);
        when(eventPublisher.publish(anyString(), anyString(), anyString(), any())).thenReturn(Completable.complete());
    }

    @Test
    void operationRequestsAreKeyedByAccountWithOriginYanki() {
        publisher.requestOperation("op", "a1", "DEPOSIT", BigDecimal.TEN, "p1").test()
                .awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish("bank.account.operation-requests", "a1", "ACCOUNT_OPERATION_REQUESTED",
                AccountOperationRequestedPayload.builder().operationId("op").accountId("a1").operation("DEPOSIT")
                        .amount(BigDecimal.TEN).origin("YANKI").reference("p1").build());
    }

    @Test
    void outcomesUseTheEventTypeOfTheFinalStatus() {
        YankiPayment completed = YankiPayment.builder().id("p1").status(PaymentStatus.COMPLETED).build();
        YankiPayment failed = YankiPayment.builder().id("p2").status(PaymentStatus.FAILED).failureReason("X").build();

        publisher.publishOutcome(completed).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        publisher.publishOutcome(failed).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.eq("bank.yanki.events"),
                org.mockito.ArgumentMatchers.eq("p1"), org.mockito.ArgumentMatchers.eq("YANKI_PAYMENT_COMPLETED"),
                any());
        verify(eventPublisher).publish(org.mockito.ArgumentMatchers.eq("bank.yanki.events"),
                org.mockito.ArgumentMatchers.eq("p2"), org.mockito.ArgumentMatchers.eq("YANKI_PAYMENT_FAILED"),
                any());
    }
}
