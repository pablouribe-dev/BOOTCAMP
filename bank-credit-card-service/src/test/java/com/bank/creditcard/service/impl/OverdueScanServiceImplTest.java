package com.bank.creditcard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.creditcard.event.CardDebtEventPublisher;
import com.bank.creditcard.model.CreditCard;
import com.bank.creditcard.repository.CreditCardRepository;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OverdueScanServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CardDebtEventPublisher cardDebtEventPublisher;

    private OverdueScanServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
        service = new OverdueScanServiceImpl(creditCardRepository, cardDebtEventPublisher, clock);
    }

    private CreditCard card(String id, LocalDate dueDate, String dueAmount) {
        return CreditCard.builder().id(id).customerId("c1").paymentDueDate(dueDate)
                .dueAmount(dueAmount == null ? null : new BigDecimal(dueAmount)).build();
    }

    @Test
    void publishesDetectedOnlyForPositiveOverdueAmountsAndMarksThem() {
        CreditCard overdue = card("k1", TODAY.minusDays(5), "150");
        CreditCard nothingDue = card("k2", TODAY.minusDays(5), "0");
        when(creditCardRepository.findByPaymentDueDateBeforeAndOverdueNotifiedNot(TODAY, true))
                .thenReturn(Flux.just(overdue, nothingDue));
        when(cardDebtEventPublisher.publishDetected(overdue)).thenReturn(Completable.complete());
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.scan().test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getDetected() == 1 && "CREDIT_CARD".equals(response.getProductType()));
        assertThat(overdue.getOverdueNotified()).isTrue();
        verify(cardDebtEventPublisher, never()).publishDetected(nothingDue);
    }

    @Test
    void kafkaFailureLeavesTheCreditUnmarkedForTheNextRun() {
        CreditCard overdue = card("k1", TODAY.minusDays(5), "150");
        when(creditCardRepository.findByPaymentDueDateBeforeAndOverdueNotifiedNot(TODAY, true))
                .thenReturn(Flux.just(overdue));
        when(cardDebtEventPublisher.publishDetected(overdue))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.scan().test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void overdueRequiresAPositiveAmountAndAPastDueDate() {
        assertThat(OverdueScanServiceImpl.isOverdue(card("k", TODAY.minusDays(1), "1"), TODAY)).isTrue();
        assertThat(OverdueScanServiceImpl.isOverdue(card("k", TODAY, "1"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(card("k", TODAY.minusDays(1), "0"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(card("k", null, "1"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(card("k", TODAY.minusDays(1), null), TODAY)).isFalse();
    }
}
