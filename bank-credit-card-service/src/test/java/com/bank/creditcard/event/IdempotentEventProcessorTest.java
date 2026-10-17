package com.bank.creditcard.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.creditcard.repository.ProcessedEventRepository;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class IdempotentEventProcessorTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private final EventEnvelope envelope = EventEnvelope.builder().eventId("e-1").eventType("TEST").build();

    @Test
    void processesANewEventAndRecordsIt() {
        IdempotentEventProcessor processor = new IdempotentEventProcessor(processedEventRepository);
        when(processedEventRepository.existsById("e-1")).thenReturn(Mono.just(false));
        when(processedEventRepository.save(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        AtomicInteger calls = new AtomicInteger();

        processor.processOnce(envelope, () -> Completable.fromAction(calls::incrementAndGet))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        org.assertj.core.api.Assertions.assertThat(calls).hasValue(1);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void skipsARepeatedEvent() {
        IdempotentEventProcessor processor = new IdempotentEventProcessor(processedEventRepository);
        when(processedEventRepository.existsById("e-1")).thenReturn(Mono.just(true));
        AtomicInteger calls = new AtomicInteger();

        processor.processOnce(envelope, () -> Completable.fromAction(calls::incrementAndGet))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        org.assertj.core.api.Assertions.assertThat(calls).hasValue(0);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void doesNotRecordTheEventWhenTheHandlerFails() {
        IdempotentEventProcessor processor = new IdempotentEventProcessor(processedEventRepository);
        when(processedEventRepository.existsById("e-1")).thenReturn(Mono.just(false));

        processor.processOnce(envelope, () -> Completable.error(new IllegalStateException("boom")))
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);

        verify(processedEventRepository, never()).save(any());
    }
}
