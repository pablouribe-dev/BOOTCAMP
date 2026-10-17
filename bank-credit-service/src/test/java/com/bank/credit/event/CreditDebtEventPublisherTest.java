package com.bank.credit.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.credit.config.BankKafkaProperties;
import com.bank.credit.model.Credit;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditDebtEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void publishesDetectedAndClearedKeyedByCustomer() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("debt-events", "bank.debt.events"));
        CreditDebtEventPublisher publisher = new CreditDebtEventPublisher(eventPublisher, properties);
        when(eventPublisher.publish(anyString(), anyString(), anyString(), any())).thenReturn(Completable.complete());
        Credit credit = Credit.builder().id("k1").customerId("c1").dueDate(LocalDate.of(2026, 10, 1))
                .dueAmount(new BigDecimal("150")).build();

        publisher.publishDetected(credit).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        publisher.publishCleared(credit).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(eventPublisher).publish("bank.debt.events", "c1", "DEBT_OVERDUE_DETECTED", DebtEventPayload.builder()
                .customerId("c1").productType("CREDIT").productId("k1").dueDate(LocalDate.of(2026, 10, 1))
                .dueAmount(new BigDecimal("150")).build());
        verify(eventPublisher).publish("bank.debt.events", "c1", "DEBT_OVERDUE_CLEARED",
                DebtEventPayload.builder().customerId("c1").productType("CREDIT").productId("k1").build());
    }
}
