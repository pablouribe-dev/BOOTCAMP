package com.bank.account.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void publishWrapsThePayloadInTheCommonEnvelopeUsingTheAggregateIdAsKey() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate, objectMapper);
        when(kafkaTemplate.send(eq("bank.test.events"), eq("id-1"), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        publisher.publish("bank.test.events", "id-1", "TEST_UPSERTED",
                        Map.of("id", "id-1", "day", LocalDate.of(2026, 10, 16), "amount", new java.math.BigDecimal("10.00")))
                .test().awaitDone(2, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaTemplate).send(eq("bank.test.events"), eq("id-1"), captor.capture());
        EventEnvelope envelope = captor.getValue();
        assertThat(envelope.getEventId()).isNotBlank();
        assertThat(envelope.getEventType()).isEqualTo("TEST_UPSERTED");
        assertThat(envelope.getOccurredAt()).isNotNull();
        assertThat(envelope.getPayload().get("day").asText()).isEqualTo("2026-10-16");
        assertThat(envelope.getPayload().get("amount").decimalValue()).hasToString("10.00");
    }

    @Test
    void publishFailsWhenTheBrokerRejectsTheMessage() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate, objectMapper);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        publisher.publish("bank.test.events", "id-1", "TEST_UPSERTED", Map.of("id", "id-1"))
                .test().awaitDone(2, TimeUnit.SECONDS).assertError(IllegalStateException.class);
    }
}
